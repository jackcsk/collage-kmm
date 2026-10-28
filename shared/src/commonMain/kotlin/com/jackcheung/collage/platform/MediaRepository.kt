package com.jackcheung.collage.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow

/**
 * Identifies a photo wherever it lives: a library item, a photo booth shot or a picked file.
 * Only the platform that created it interprets [id].
 */
@Immutable
data class PhotoRef(val id: String)

/** How much of the photo library the app may read. */
enum class MediaAccess {
    None,

    /** Only photos the user chose to share with the app. */
    Partial,
    Full,
}

/** The device's photo library and the app's own photo files. */
interface MediaRepository {
    fun mediaAccess(): MediaAccess

    /** Emits once immediately and again whenever the library changes. Only collect with access. */
    fun imageChanges(): Flow<Unit>

    /** Photos visible to the app, newest first. */
    suspend fun queryImages(): List<PhotoRef>

    /** Small upright preview, at most [maxSide] px on its long side. */
    suspend fun loadThumbnail(photo: PhotoRef, maxSide: Int): ImageBitmap?

    /**
     * Decodes [photo] upright. [scaleFor] receives the photo's width and height in pixels and
     * returns the wanted scale; scales of 1 or more decode at full size.
     */
    suspend fun decode(photo: PhotoRef, scaleFor: (width: Int, height: Int) -> Float): ImageBitmap?

    /** Whether the photo contains at least one face, judged on a small preview. */
    suspend fun hasFace(photo: PhotoRef): Boolean

    /** Adds a JPEG to the photo library and returns a reference that can be shared. */
    suspend fun saveJpeg(bytes: ByteArray): PhotoRef

    /** Whether [photo] is a photo booth shot, which [clearBoothShots] deletes. */
    fun isBoothShot(photo: PhotoRef): Boolean

    fun clearBoothShots()
}

@Composable
expect fun rememberMediaRepository(): MediaRepository

/** Encodes as JPEG at [quality] (0–100). */
expect fun ImageBitmap.encodeJpeg(quality: Int): ByteArray

expect fun logError(message: String, error: Throwable)
