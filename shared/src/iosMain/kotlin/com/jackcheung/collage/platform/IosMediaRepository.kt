package com.jackcheung.collage.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHChange
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageContentModeAspectFit
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeExact
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserverProtocol
import platform.UIKit.UIImage
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

private const val ASSET = "asset:"
private const val FILE = "file:"
private const val FACE_SCAN_SIZE = 320

/** Photos-framework library; booth shots, picked photos and saved copies live in Caches. */
internal object IosMediaRepository : MediaRepository {
    init {
        // Picked photos are copies that only matter while the app runs.
        clearDirectory(pickedDirectory())
    }

    override fun mediaAccess(): MediaAccess = when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
        PHAuthorizationStatusAuthorized -> MediaAccess.Full
        PHAuthorizationStatusLimited -> MediaAccess.Partial
        else -> MediaAccess.None
    }

    override fun imageChanges(): Flow<Unit> = callbackFlow {
        val observer = LibraryObserver { trySend(Unit) }
        val library = PHPhotoLibrary.sharedPhotoLibrary()
        library.registerChangeObserver(observer)
        send(Unit)
        awaitClose { library.unregisterChangeObserver(observer) }
    }.conflate()

    override suspend fun queryImages(): List<PhotoRef> = withContext(Dispatchers.Default) {
        val options = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false))
        }
        val result = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options)
        List(result.count.toInt()) { i -> PhotoRef(ASSET + (result.objectAtIndex(i.toULong()) as PHAsset).localIdentifier) }
    }

    override suspend fun loadThumbnail(photo: PhotoRef, maxSide: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        load(photo, exact = false) { w, h -> maxSide.toFloat() / max(w, h) }?.run { image.toImageBitmap(width, height) }
    }

    override suspend fun decode(photo: PhotoRef, scaleFor: (Int, Int) -> Float): ImageBitmap? = withContext(Dispatchers.Default) {
        load(photo, exact = true, scaleFor)?.run { image.toImageBitmap(width, height) }
    }

    override suspend fun hasFace(photo: PhotoRef): Boolean = withContext(Dispatchers.Default) {
        val loaded = load(photo, exact = false) { w, h -> FACE_SCAN_SIZE.toFloat() / max(w, h) } ?: return@withContext false
        val cgImage = loaded.image.toUprightCGImage(loaded.width, loaded.height) ?: return@withContext false
        try {
            val request = VNDetectFaceRectanglesRequest()
            val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            handler.performRequests(listOf(request), null) && !request.results.isNullOrEmpty()
        } finally {
            CGImageRelease(cgImage)
        }
    }

    override suspend fun saveJpeg(bytes: ByteArray): PhotoRef {
        val data = bytes.toNSData()
        // Keep a file copy to share; only the latest one is needed.
        val directory = cacheDirectory("collages")
        clearDirectory(directory)
        val stamp = NSDateFormatter().apply { dateFormat = "yyyyMMdd_HHmmss" }.stringFromDate(NSDate())
        val path = "$directory/Collage_$stamp.jpg"
        check(data.writeToFile(path, atomically = true)) { "Can't write $path" }
        suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    PHAssetCreationRequest.creationRequestForAsset()
                        .addResourceWithType(PHAssetResourceTypePhoto, data = data, options = null)
                },
                completionHandler = { success, error ->
                    if (success) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Saving to Photos failed"))
                    }
                },
            )
        }
        return PhotoRef(FILE + path)
    }

    override fun isBoothShot(photo: PhotoRef): Boolean = photo.filePath?.startsWith(boothDirectory()) == true

    override fun clearBoothShots() = clearDirectory(boothDirectory())

    /**
     * Moves image files into the picked-photo directory and returns them as photos. A member, so
     * the directory is cleared (by `init`) before anything is moved into it, not after.
     */
    fun adoptFiles(paths: List<String>): List<PhotoRef> = paths.mapNotNull { path ->
        val extension = path.substringAfterLast('.', "").ifEmpty { "jpg" }
        val destination = "${pickedDirectory()}/${NSUUID().UUIDString}.$extension"
        if (NSFileManager.defaultManager.moveItemAtPath(path, destination, error = null)) filePhoto(destination) else null
    }

    /** The image at [photo] and the pixel size to draw it at. */
    private class Loaded(val image: UIImage, val width: Int, val height: Int)

    private fun load(photo: PhotoRef, exact: Boolean, scaleFor: (Int, Int) -> Float): Loaded? {
        photo.filePath?.let { path ->
            val image = UIImage.imageWithContentsOfFile(path) ?: return null
            val (width, height) = scaledSize(image.pixelWidth, image.pixelHeight, scaleFor(image.pixelWidth, image.pixelHeight))
            return Loaded(image, width, height)
        }
        val id = photo.id.removePrefix(ASSET)
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(id), null).firstObject as? PHAsset ?: return null
        val assetWidth = asset.pixelWidth.toInt()
        val assetHeight = asset.pixelHeight.toInt()
        if (assetWidth <= 0 || assetHeight <= 0) return null
        val (targetWidth, targetHeight) = scaledSize(assetWidth, assetHeight, scaleFor(assetWidth, assetHeight))
        val image = requestImage(asset, targetWidth, targetHeight, exact) ?: return null
        // The manager may hand back a different size; keep its aspect, capped at the target.
        val fit = min(1f, max(targetWidth, targetHeight).toFloat() / max(image.pixelWidth, image.pixelHeight))
        val (width, height) = scaledSize(image.pixelWidth, image.pixelHeight, fit)
        return Loaded(image, width, height)
    }

    /** Blocking; called off the main thread. Downloads from iCloud when needed. */
    private fun requestImage(asset: PHAsset, width: Int, height: Int, exact: Boolean): UIImage? {
        val options = PHImageRequestOptions().apply {
            synchronous = true
            networkAccessAllowed = true
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            resizeMode = if (exact) PHImageRequestOptionsResizeModeExact else PHImageRequestOptionsResizeModeFast
        }
        var result: UIImage? = null
        PHImageManager.defaultManager().requestImageForAsset(
            asset,
            targetSize = CGSizeMake(width.toDouble(), height.toDouble()),
            contentMode = PHImageContentModeAspectFit,
            options = options,
        ) { image, _ -> result = image }
        return result
    }
}

private class LibraryObserver(private val onChange: () -> Unit) : NSObject(), PHPhotoLibraryChangeObserverProtocol {
    override fun photoLibraryDidChange(changeInstance: PHChange) = onChange()
}

/** Path of a photo the app stores as a file, or null for library assets. */
internal val PhotoRef.filePath: String? get() = if (id.startsWith(FILE)) id.removePrefix(FILE) else null

internal fun filePhoto(path: String): PhotoRef = PhotoRef(FILE + path)

internal fun cacheDirectory(name: String): String {
    val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    val path = "$base/$name"
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    return path
}

internal fun boothDirectory(): String = cacheDirectory("booth")

internal fun pickedDirectory(): String = cacheDirectory("picked")

private fun clearDirectory(path: String) {
    val files = NSFileManager.defaultManager
    files.contentsOfDirectoryAtPath(path, error = null)?.forEach { name ->
        files.removeItemAtPath("$path/$name", error = null)
    }
}

@Composable
actual fun rememberMediaRepository(): MediaRepository = IosMediaRepository

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes ?: error("JPEG encode failed")

actual fun logError(message: String, error: Throwable) {
    println("Collage: $message: $error")
}
