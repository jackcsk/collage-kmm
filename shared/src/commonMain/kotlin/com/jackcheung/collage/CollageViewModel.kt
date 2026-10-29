package com.jackcheung.collage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackcheung.collage.editor.CollageCell
import com.jackcheung.collage.editor.EditorState
import com.jackcheung.collage.editor.SaveState
import com.jackcheung.collage.editor.saveCollage
import com.jackcheung.collage.layout.LayoutCatalog
import com.jackcheung.collage.layout.LayoutTemplate
import com.jackcheung.collage.layout.PhotoTransform
import com.jackcheung.collage.layout.moveDivider
import com.jackcheung.collage.platform.MediaAccess
import com.jackcheung.collage.platform.MediaRepository
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.platform.logError
import com.jackcheung.collage.ui.ThumbnailCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen

    data object Editor : Screen

    data object PhotoBooth : Screen
}

/** Progress of the on-device face search over the most recent photos. */
data class FaceScan(val faces: List<PhotoRef> = emptyList(), val scanned: Int = 0, val total: Int = 0) {
    val running: Boolean get() = scanned < total
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollageViewModel(private val repository: MediaRepository) : ViewModel() {
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    /** Grid thumbnails, shared by the gallery, faces and photo booth strips. */
    val gridThumbnails = ThumbnailCache(repository)

    // region Gallery

    private val access = MutableStateFlow(repository.mediaAccess())
    val mediaAccess: StateFlow<MediaAccess> = access.asStateFlow()

    /** Bumped when partial access may have changed without the library noticing. */
    private val refreshes = MutableStateFlow(0)

    val gallery: StateFlow<List<PhotoRef>> = access
        .flatMapLatest { access ->
            if (access == MediaAccess.None) {
                flowOf(emptyList())
            } else {
                combine(repository.imageChanges(), refreshes) { _, _ -> }.mapLatest { repository.queryImages() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshMediaAccess() {
        access.value = repository.mediaAccess()
        refreshes.update { it + 1 }
    }

    // endregion

    // region Faces

    private val faceResults = HashMap<PhotoRef, Boolean>()
    private val _faceScan = MutableStateFlow(FaceScan())
    val faceScan: StateFlow<FaceScan> = _faceScan.asStateFlow()
    private var faceJob: Job? = null

    /** Starts scanning recent photos for faces; keeps up with gallery changes afterwards. */
    fun startFaceScan() {
        if (faceJob != null) return
        faceJob = viewModelScope.launch {
            gallery.collectLatest { images ->
                val candidates = images.take(FACE_SCAN_LIMIT)
                var scanned = 0
                fun publish() {
                    _faceScan.value = FaceScan(candidates.filter { faceResults[it] == true }, scanned, candidates.size)
                }
                publish()
                candidates.chunked(FACE_SCAN_PARALLELISM).forEach { chunk ->
                    val results = coroutineScope {
                        chunk.map { photo -> async { photo to (faceResults[photo] ?: repository.hasFace(photo)) } }.awaitAll()
                    }
                    faceResults.putAll(results)
                    scanned += chunk.size
                    publish()
                }
            }
        }
    }

    // endregion

    // region Selection

    var selection by mutableStateOf<List<PhotoRef>>(emptyList())
        private set

    /** Small previews of selected photos for the layout strip and as stand-ins while editing. */
    private val thumbnails = mutableStateMapOf<PhotoRef, ImageBitmap>()
    private val thumbnailLoads = HashMap<PhotoRef, Job>()

    /** Adds or removes [photo]; returns false when the selection is already full. */
    fun toggleSelection(photo: PhotoRef): Boolean {
        if (photo in selection) {
            updateSelection(selection - photo)
            return true
        }
        if (selection.size >= LayoutCatalog.MAX_PHOTOS) return false
        updateSelection(selection + photo)
        return true
    }

    /** Adds photos from the system picker; returns false if some didn't fit. */
    fun addToSelection(photos: List<PhotoRef>): Boolean {
        val fresh = photos.filterNot { it in selection }
        val room = LayoutCatalog.MAX_PHOTOS - selection.size
        updateSelection(selection + fresh.take(room))
        return fresh.size <= room
    }

    fun clearSelection() = updateSelection(emptyList())

    private fun updateSelection(photos: List<PhotoRef>) {
        selection = photos
        val keep = photos.toSet() + editor?.cells?.map { it.photo }.orEmpty()
        thumbnails.keys.retainAll(keep)
        thumbnailLoads.keys.filterNot { it in keep }.forEach { thumbnailLoads.remove(it)?.cancel() }
        photos.forEach(::loadThumbnail)
    }

    private fun loadThumbnail(photo: PhotoRef) {
        if (photo in thumbnails || photo in thumbnailLoads) return
        thumbnailLoads[photo] = viewModelScope.launch {
            repository.loadThumbnail(photo, THUMBNAIL_SIZE)?.let { thumbnails[photo] = it }
            thumbnailLoads.remove(photo)
        }
    }

    // endregion

    // region Editor

    var editor by mutableStateOf<EditorState?>(null)
        private set

    /** Editing-resolution photos; [thumbnails] stand in until these load. */
    private val photos = mutableStateMapOf<PhotoRef, ImageBitmap>()
    private val photoLoads = HashMap<PhotoRef, Job>()

    var saveState by mutableStateOf<SaveState>(SaveState.Idle)
        private set

    fun image(photo: PhotoRef): ImageBitmap? = photos[photo] ?: thumbnails[photo]

    fun openEditor(template: LayoutTemplate) {
        val photosForLayout = selection.take(template.cellCount)
        if (photosForLayout.size < template.cellCount) return
        editor = EditorState(layout = template.root, cells = photosForLayout.map { CollageCell(it) })
        saveState = SaveState.Idle
        loadEditorPhotos()
        screen = Screen.Editor
    }

    fun closeEditor() {
        editor = null
        photoLoads.values.forEach { it.cancel() }
        photoLoads.clear()
        photos.clear()
        saveState = SaveState.Idle
        updateSelection(selection) // drops thumbnails of photos only the editor used
        screen = Screen.Home
    }

    /** After saving: back to an empty selection for the next collage. */
    fun startNewLayout() {
        closeEditor()
        clearSelection()
    }

    fun selectCell(index: Int?) = edit { it.copy(selected = index) }

    fun transformCell(index: Int, pan: Offset, zoomChange: Float, centroid: Offset, cellSize: Size): Unit = edit { state ->
        val cell = state.cells[index]
        val bitmap = image(cell.photo) ?: return
        val transform = cell.transform.transformed(
            pan = pan,
            zoomChange = zoomChange,
            centroid = centroid,
            imageWidth = bitmap.width.toFloat(),
            imageHeight = bitmap.height.toFloat(),
            cellWidth = cellSize.width,
            cellHeight = cellSize.height,
        )
        state.withCell(index, cell.copy(transform = transform))
    }

    fun moveDivider(path: List<Int>, index: Int, delta: Float) = edit {
        it.copy(layout = it.layout.moveDivider(path, index, delta), edited = true)
    }

    /** Exchanges the photos of two cells; both start from a fresh crop. */
    fun swapCells(from: Int, to: Int): Unit = edit { state ->
        if (from == to) return
        val cells = state.cells.toMutableList()
        cells[from] = CollageCell(state.cells[to].photo)
        cells[to] = CollageCell(state.cells[from].photo)
        state.copy(cells = cells, selected = to, edited = true)
    }

    fun mirrorSelected() = updateSelectedTransform { it.copy(mirrored = !it.mirrored) }

    fun flipSelected() = updateSelectedTransform { it.copy(flipped = !it.flipped) }

    fun toggleBorder() = edit { it.copy(border = !it.border, edited = true) }

    fun replaceSelected(photo: PhotoRef) {
        edit { state ->
            val index = state.selected ?: return
            state.withCell(index, CollageCell(photo))
        }
        loadThumbnail(photo)
        loadEditorPhotos()
    }

    fun save() {
        val state = editor ?: return
        if (saveState == SaveState.Saving) return
        saveState = SaveState.Saving
        viewModelScope.launch {
            saveState = try {
                val photo = repository.saveCollage(state.layout, state.cells, state.border)
                // The saving dialog blocks edits, so the saved state is still the current one.
                if (editor == state) editor = state.copy(edited = false)
                SaveState.Saved(photo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Saving collage failed", e)
                SaveState.Failed
            }
        }
    }

    fun dismissSaveResult() {
        saveState = SaveState.Idle
    }

    private inline fun edit(transform: (EditorState) -> EditorState) {
        editor = editor?.let(transform)
    }

    private fun updateSelectedTransform(transform: (PhotoTransform) -> PhotoTransform): Unit = edit { state ->
        val index = state.selected ?: return
        val cell = state.cells[index]
        state.withCell(index, cell.copy(transform = transform(cell.transform)))
    }

    private fun EditorState.withCell(index: Int, cell: CollageCell) =
        copy(cells = cells.toMutableList().also { it[index] = cell }, edited = true)

    private fun loadEditorPhotos() {
        val state = editor ?: return
        val maxSide = editingResolution(state.cells.size)
        val needed = state.cells.map { it.photo }.toSet()
        photos.keys.retainAll(needed)
        needed.filterNot { it in photos || it in photoLoads }.forEach { photo ->
            photoLoads[photo] = viewModelScope.launch {
                repository.decode(photo) { w, h -> maxSide.toFloat() / maxOf(w, h) }?.let { photos[photo] = it }
                photoLoads.remove(photo)
            }
        }
    }

    // endregion

    // region Photo booth

    fun openPhotoBooth() {
        // Old shots are about to be deleted, so they can't stay selected.
        updateSelection(selection.filterNot(repository::isBoothShot))
        repository.clearBoothShots()
        screen = Screen.PhotoBooth
    }

    fun closePhotoBooth() {
        screen = Screen.Home
    }

    fun onBoothFinished(shots: List<PhotoRef>) {
        updateSelection(shots.take(LayoutCatalog.MAX_PHOTOS))
        val template = LayoutCatalog.templatesFor(selection.size).firstOrNull()
        if (template == null) {
            screen = Screen.Home
            return
        }
        openEditor(template)
    }

    // endregion

    private companion object {
        const val THUMBNAIL_SIZE = 384
        const val FACE_SCAN_LIMIT = 500
        const val FACE_SCAN_PARALLELISM = 4

        /** Longest side of photos decoded for editing: fewer, larger cells need more pixels. */
        fun editingResolution(count: Int) = when {
            count <= 1 -> 2048
            count <= 2 -> 1600
            count <= 4 -> 1280
            else -> 1024
        }
    }
}
