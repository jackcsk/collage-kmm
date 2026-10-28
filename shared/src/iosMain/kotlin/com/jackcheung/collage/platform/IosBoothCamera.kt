package com.jackcheung.collage.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeJPEG
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.position
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AVFoundation camera; the session runs while [Preview] is composed. Session work happens on a
 * private serial queue because starting and reconfiguring block.
 */
private class IosBoothCamera : BoothCamera {
    override val canSwitch: Boolean = device(front = true) != null && device(front = false) != null
    override var front by mutableStateOf(device(front = true) != null)
        private set

    private val session = AVCaptureSession()
    private val output = AVCapturePhotoOutput()
    private val queue = dispatch_queue_create("com.jackcheung.collage.camera", null)
    private var input: AVCaptureDeviceInput? = null

    /** Retained until the capture finishes; the output holds its delegate weakly. */
    private var capture: PhotoCaptureDelegate? = null

    override fun switchCamera() {
        front = !front
    }

    override suspend fun takePhoto(): PhotoRef = suspendCancellableCoroutine { continuation ->
        val path = "${boothDirectory()}/booth_${NSUUID().UUIDString}.jpg"
        val delegate = PhotoCaptureDelegate { data, error ->
            capture = null
            if (data != null && data.writeToFile(path, atomically = true)) {
                continuation.resume(filePhoto(path))
            } else {
                continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Capture failed"))
            }
        }
        capture = delegate
        val settings = AVCapturePhotoSettings.photoSettingsWithFormat(mapOf<Any?, Any?>(AVVideoCodecKey to AVVideoCodecTypeJPEG))
        // The system plays the shutter sound.
        output.capturePhotoWithSettings(settings, delegate)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        LaunchedEffect(front) { configure(front) }
        DisposableEffect(Unit) { onDispose { dispatch_async(queue) { session.stopRunning() } } }
        UIKitView(factory = { CameraPreviewView(session) }, modifier = modifier)
    }

    private fun configure(useFront: Boolean) = dispatch_async(queue) {
        val device = device(useFront) ?: device(!useFront) ?: return@dispatch_async
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto
        input?.let(session::removeInput)
        val newInput = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
        if (newInput != null && session.canAddInput(newInput)) {
            session.addInput(newInput)
            input = newInput
        }
        if (output !in session.outputs && session.canAddOutput(output)) session.addOutput(output)
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            @Suppress("DEPRECATION") // videoRotationAngle needs iOS 17.
            if (connection.supportsVideoOrientation) connection.videoOrientation = AVCaptureVideoOrientationPortrait
            if (connection.supportsVideoMirroring) {
                // Mirror front shots so they match the preview, like the Android booth.
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = device.position == AVCaptureDevicePositionFront
            }
        }
        session.commitConfiguration()
        if (!session.running) session.startRunning()
    }
}

private fun device(front: Boolean): AVCaptureDevice? = AVCaptureDevice.defaultDeviceWithDeviceType(
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    AVMediaTypeVideo,
    if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack,
)

private class PhotoCaptureDelegate(
    private val onFinished: (NSData?, NSError?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(output: AVCapturePhotoOutput, didFinishProcessingPhoto: AVCapturePhoto, error: NSError?) {
        onFinished(didFinishProcessingPhoto.fileDataRepresentation(), error)
    }
}

/** Hosts the preview layer and keeps it sized to the view. */
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

@Composable
actual fun rememberBoothCamera(): BoothCamera = remember { IosBoothCamera() }
