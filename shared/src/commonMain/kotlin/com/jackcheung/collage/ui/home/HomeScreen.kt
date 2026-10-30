package com.jackcheung.collage.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jackcheung.collage.CollageViewModel
import com.jackcheung.collage.FaceScan
import com.jackcheung.collage.editor.CollageCell
import com.jackcheung.collage.layout.LayoutCatalog
import com.jackcheung.collage.layout.LayoutTemplate
import com.jackcheung.collage.layout.geometry
import com.jackcheung.collage.platform.MediaAccess
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.platform.rememberAppSettingsOpener
import com.jackcheung.collage.platform.rememberMediaAccessRequester
import com.jackcheung.collage.platform.rememberPhotoPicker
import com.jackcheung.collage.resources.*
import com.jackcheung.collage.ui.PhotoThumbnail
import com.jackcheung.collage.ui.ThumbnailCache
import com.jackcheung.collage.ui.drawCollage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TAB_GALLERY = 0
private const val TAB_FACES = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: CollageViewModel) {
    val access by viewModel.mediaAccess.collectAsStateWithLifecycle()
    val gallery by viewModel.gallery.collectAsStateWithLifecycle()
    val faceScan by viewModel.faceScan.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(TAB_GALLERY) }

    val limitMessage = stringResource(Res.string.home_limit_reached, LayoutCatalog.MAX_PHOTOS)
    val showLimit: () -> Unit = { scope.launch { snackbar.showSnackbar(limitMessage) } }
    LaunchedEffect(viewModel.sharedOverflow) {
        if (viewModel.sharedOverflow) {
            viewModel.sharedOverflowShown()
            showLimit()
        }
    }

    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val accessRequester = rememberMediaAccessRequester {
        viewModel.refreshMediaAccess()
        permissionDenied = viewModel.mediaAccess.value == MediaAccess.None
    }
    LaunchedEffect(Unit) {
        if (access == MediaAccess.None && !requestedPermission) {
            requestedPermission = true
            accessRequester.request()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshMediaAccess() }
    val openSettings = rememberAppSettingsOpener()

    val openPicker = rememberPhotoPicker(LayoutCatalog.MAX_PHOTOS) { photos ->
        if (!viewModel.addToSelection(photos)) showLimit()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = openPicker) {
                        Icon(painterResource(Res.drawable.ic_photo_library), contentDescription = stringResource(Res.string.home_browse))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openPhotoBooth,
                icon = { Icon(painterResource(Res.drawable.ic_photo_camera), contentDescription = null) },
                text = { Text(stringResource(Res.string.home_photo_booth)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LayoutStrip(
                selection = selection,
                image = viewModel::image,
                onPick = viewModel::openEditor,
                onClear = viewModel::clearSelection,
            )
            HorizontalDivider()
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == TAB_GALLERY,
                    onClick = { tab = TAB_GALLERY },
                    text = { Text(stringResource(Res.string.home_tab_gallery)) },
                )
                Tab(
                    selected = tab == TAB_FACES,
                    onClick = { tab = TAB_FACES },
                    text = { Text(stringResource(Res.string.home_tab_faces)) },
                )
            }
            if (tab == TAB_FACES && access != MediaAccess.None) {
                LaunchedEffect(Unit) { viewModel.startFaceScan() }
            }
            when (access) {
                MediaAccess.None -> PermissionPrompt(
                    denied = permissionDenied,
                    onAllow = accessRequester::request,
                    onOpenSettings = openSettings,
                    onBrowse = openPicker,
                )
                MediaAccess.Partial, MediaAccess.Full -> Column {
                    if (access == MediaAccess.Partial) {
                        PartialAccessBanner(onManage = accessRequester::manageSelection)
                    }
                    val toggle: (PhotoRef) -> Unit = { if (!viewModel.toggleSelection(it)) showLimit() }
                    when (tab) {
                        TAB_GALLERY -> PhotoGrid(
                            images = gallery,
                            thumbnails = viewModel.gridThumbnails,
                            selection = selection,
                            emptyText = stringResource(Res.string.gallery_empty),
                            onToggle = toggle,
                        )
                        else -> FacesTab(faceScan = faceScan, thumbnails = viewModel.gridThumbnails, selection = selection, onToggle = toggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutStrip(
    selection: List<PhotoRef>,
    image: (PhotoRef) -> ImageBitmap?,
    onPick: (LayoutTemplate) -> Unit,
    onClear: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(164.dp), contentAlignment = Alignment.Center) {
        if (selection.isEmpty()) {
            Text(
                text = stringResource(Res.string.home_hint, LayoutCatalog.MAX_PHOTOS),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            return@Box
        }
        val cells = remember(selection) { selection.map { CollageCell(it) } }
        val templates = LayoutCatalog.templatesFor(selection.size)
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.home_selected_count, selection.size, LayoutCatalog.MAX_PHOTOS),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(Res.string.home_clear)) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(templates, key = { _, template -> template.id }) { index, template ->
                    val description = stringResource(Res.string.home_layout_option, index + 1)
                    LayoutPreview(
                        template = template,
                        cells = cells,
                        image = image,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPick(template) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutPreview(
    template: LayoutTemplate,
    cells: List<CollageCell>,
    image: (PhotoRef) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        drawCollage(
            geometry = template.root.geometry(size, 2.dp.toPx()),
            cells = cells,
            image = image,
            background = Color.White,
            placeholder = placeholder,
        )
    }
}

@Composable
private fun PhotoGrid(
    images: List<PhotoRef>,
    thumbnails: ThumbnailCache,
    selection: List<PhotoRef>,
    emptyText: String,
    onToggle: (PhotoRef) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        header?.invoke()
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(images, key = { it.id }) { photo ->
                GalleryTile(
                    photo = photo,
                    thumbnails = thumbnails,
                    order = selection.indexOf(photo),
                    onClick = { onToggle(photo) },
                )
            }
        }
    }
}

@Composable
private fun GalleryTile(photo: PhotoRef, thumbnails: ThumbnailCache, order: Int, onClick: () -> Unit) {
    val selected = order >= 0
    val badgeDescription = if (selected) stringResource(Res.string.home_selection_badge, order + 1) else null
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                badgeDescription?.let { contentDescription = it }
            },
    ) {
        PhotoThumbnail(photo = photo, cache = thumbnails, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .border(3.dp, MaterialTheme.colorScheme.primary),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    },
                ),
        ) {
            if (selected) {
                Text(
                    text = "${order + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun FacesTab(
    faceScan: FaceScan,
    thumbnails: ThumbnailCache,
    selection: List<PhotoRef>,
    onToggle: (PhotoRef) -> Unit,
) {
    PhotoGrid(
        images = faceScan.faces,
        thumbnails = thumbnails,
        selection = selection,
        emptyText = if (faceScan.running) "" else stringResource(Res.string.faces_empty),
        onToggle = onToggle,
        header = {
            if (faceScan.running) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(Res.string.faces_scanning, faceScan.scanned, faceScan.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { faceScan.scanned.toFloat() / faceScan.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                text = stringResource(Res.string.permission_partial),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManage) { Text(stringResource(Res.string.permission_manage)) }
        }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit, onBrowse: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(stringResource(Res.string.permission_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (denied) {
            Button(onClick = onOpenSettings) { Text(stringResource(Res.string.permission_settings)) }
        } else {
            Button(onClick = onAllow) { Text(stringResource(Res.string.permission_allow)) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBrowse) { Text(stringResource(Res.string.home_browse)) }
    }
}
