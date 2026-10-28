package com.jackcheung.collage.platform

import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX camera bound to the composition's lifecycle while [Preview] is shown. */
private class AndroidBoothCamera(private val context: Context) : BoothCamera {
    override var canSwitch by mutableStateOf(false)
        private set
    override var front by mutableStateOf(true)
        private set
    private var surfaceRequest by mutableStateOf<SurfaceRequest?>(null)
    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private val shutterSound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }

    override fun switchCamera() {
        front = !front
    }

    override suspend fun takePhoto(): PhotoRef {
        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        val directory = context.boothDirectory().apply { mkdirs() }
        val file = File(directory, "booth_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(file, mirrored = front)
        return PhotoRef(Uri.fromFile(file).toString())
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(front, lifecycleOwner) {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            canSwitch = hasFront && hasBack
            val useFront = when {
                front && hasFront -> true
                hasBack -> false
                hasFront -> true
                else -> return@LaunchedEffect
            }
            front = useFront
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            val lens = if (useFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lens).build(), preview, imageCapture)
            try {
                awaitCancellation()
            } finally {
                provider.unbindAll()
            }
        }
        surfaceRequest?.let { request ->
            CameraXViewfinder(surfaceRequest = request, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }

    fun release() {
        shutterSound.release()
    }

    /** Saves one photo to [file]; front-camera shots are mirrored to match the preview. */
    private suspend fun ImageCapture.takePicture(file: File, mirrored: Boolean) {
        suspendCancellableCoroutine { continuation ->
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = mirrored }
            val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
            takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }
}

@Composable
actual fun rememberBoothCamera(): BoothCamera {
    val context = LocalContext.current.applicationContext
    val camera = remember(context) { AndroidBoothCamera(context) }
    DisposableEffect(camera) { onDispose { camera.release() } }
    return camera
}
