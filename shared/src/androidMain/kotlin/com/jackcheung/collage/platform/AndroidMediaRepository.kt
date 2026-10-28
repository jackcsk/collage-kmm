package com.jackcheung.collage.platform

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.FaceDetector
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** MediaStore-backed photo library; booth shots live in the app's cache directory. */
internal class AndroidMediaRepository(private val context: Context) : MediaRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    override fun mediaAccess(): MediaAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.Full
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.Full
            else -> MediaAccess.None
        }
    }

    override fun imageChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        send(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    override suspend fun queryImages(): List<PhotoRef> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        resolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList(cursor.count) {
                while (cursor.moveToNext()) {
                    add(PhotoRef(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)).toString()))
                }
            }
        }.orEmpty()
    }

    override suspend fun loadThumbnail(photo: PhotoRef, maxSide: Int): ImageBitmap? =
        thumbnailBitmap(photo, maxSide)?.asImageBitmap()

    override suspend fun decode(photo: PhotoRef, scaleFor: (Int, Int) -> Float): ImageBitmap? =
        withContext(Dispatchers.IO) {
            readOrNull { decodeBitmap(photo.uri, scaleFor) }?.asImageBitmap()
        }

    override suspend fun hasFace(photo: PhotoRef): Boolean {
        val thumbnail = thumbnailBitmap(photo, FACE_SCAN_SIZE) ?: return false
        return withContext(Dispatchers.Default) {
            // FaceDetector needs RGB_565 and an even width.
            val rgb = thumbnail.copy(Bitmap.Config.RGB_565, false)
            val even = if (rgb.width % 2 == 0) {
                rgb
            } else {
                Bitmap.createScaledBitmap(rgb, rgb.width - 1, rgb.height, true).also { rgb.recycle() }
            }
            try {
                if (even.width < 2 || even.height < 2) return@withContext false
                val faces = arrayOfNulls<FaceDetector.Face>(1)
                FaceDetector(even.width, even.height, 1).findFaces(even, faces) > 0
            } finally {
                even.recycle()
            }
        }
    }

    override suspend fun saveJpeg(bytes: ByteArray): PhotoRef = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Collage_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Collage")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(target, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Can't open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            PhotoRef(uri.toString())
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    override fun isBoothShot(photo: PhotoRef): Boolean {
        val uri = photo.uri
        return uri.scheme == ContentResolver.SCHEME_FILE && uri.path.orEmpty().startsWith(context.boothDirectory().path)
    }

    override fun clearBoothShots() {
        context.boothDirectory().deleteRecursively()
        context.boothDirectory().mkdirs()
    }

    private suspend fun thumbnailBitmap(photo: PhotoRef, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val uri = photo.uri
        readOrNull {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                resolver.loadThumbnail(uri, Size(maxSide, maxSide), null)
            } else {
                decodeBitmap(uri) { w, h -> maxSide.toFloat() / max(w, h) }
            }
        }
    }

    /**
     * Decodes upright into a software bitmap, which any canvas can draw. [scaleFor] receives the
     * encoded width and height and returns the wanted scale; it is never upscaled.
     */
    private fun decodeBitmap(uri: Uri, scaleFor: (Int, Int) -> Float): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = scaleFor(w, h)
            if (scale < 1f) {
                decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
            }
        }

    private inline fun <T> readOrNull(read: () -> T): T? = try {
        read()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private companion object {
        const val FACE_SCAN_SIZE = 320
    }
}

internal val PhotoRef.uri: Uri get() = id.toUri()

/** Where photo booth shots are written; emptied each time the booth opens. */
internal fun Context.boothDirectory(): File = File(cacheDir, "booth")

/** Permissions to request for photo library access on this device. */
internal val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
actual fun rememberMediaRepository(): MediaRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidMediaRepository(context) }
}

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    check(asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
    return out.toByteArray()
}

actual fun logError(message: String, error: Throwable) {
    Log.e("Collage", message, error)
}
