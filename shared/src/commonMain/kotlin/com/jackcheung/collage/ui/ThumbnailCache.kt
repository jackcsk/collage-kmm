package com.jackcheung.collage.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.jackcheung.collage.platform.MediaRepository
import com.jackcheung.collage.platform.PhotoRef

/**
 * Least-recently-used cache of grid thumbnails. Main-thread confined: only touched from
 * composition and the coroutines it launches.
 */
class ThumbnailCache(
    private val repository: MediaRepository,
    private val maxSide: Int = 256,
    private val capacity: Int = 150,
) {
    // Insertion-ordered; an entry is re-inserted on every hit, so the first key is the eldest.
    private val entries = LinkedHashMap<PhotoRef, ImageBitmap>()

    fun cached(photo: PhotoRef): ImageBitmap? = entries.remove(photo)?.also { entries[photo] = it }

    suspend fun load(photo: PhotoRef): ImageBitmap? {
        cached(photo)?.let { return it }
        val image = repository.loadThumbnail(photo, maxSide) ?: return null
        entries[photo] = image
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return image
    }
}

/** Square-cropped thumbnail that loads through [cache] while it is on screen. */
@Composable
fun PhotoThumbnail(photo: PhotoRef, cache: ThumbnailCache, modifier: Modifier = Modifier) {
    val image by produceState(cache.cached(photo), photo) {
        if (value == null) value = cache.load(photo)
    }
    image?.let {
        Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}
