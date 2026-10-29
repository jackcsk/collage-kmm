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
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
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
import platform.UIKit.UIColor
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
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
    private var videoOrientation = AVCaptureVideoOrientationPortrait // Camera queue only.
    private var previewView: CameraPreviewView? = null // Main thread only.

    /** Retained until the capture finishes; the output holds its delegate weakly. */
    private var capture: PhotoCaptureDelegate? = null

    override fun switchCamera() {
        front = !front
    }

    override suspend fun takePhoto(): PhotoRef = suspendCancellableCoroutine { continuation ->
        onMain {
            // Read the scene on main, then enqueue its orientation before the capture.
            previewView?.updateConnection()
            dispatch_async(queue) {
                // Capturing without a live connection raises an Objective-C exception.
                val connection = output.connectionWithMediaType(AVMediaTypeVideo)
                if (connection == null || !connection.active || !connection.enabled) {
                    continuation.resumeWithException(IllegalStateException("No active camera"))
                    return@dispatch_async
                }
                val path = "${boothDirectory()}/booth_${NSUUID().UUIDString}.jpg"
                val delegate = PhotoCaptureDelegate { data, error ->
                    dispatch_async(queue) {
                        capture = null
                        if (data != null && data.writeToFile(path, atomically = true)) {
                            continuation.resume(filePhoto(path))
                        } else {
                            continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Capture failed"))
                        }
                    }
                }
                capture = delegate
                val settings = AVCapturePhotoSettings.photoSettingsWithFormat(mapOf<Any?, Any?>(AVVideoCodecKey to AVVideoCodecTypeJPEG))
                // The system plays the shutter sound.
                output.capturePhotoWithSettings(settings, delegate)
            }
        }
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        LaunchedEffect(front) { configure(front) }
        DisposableEffect(Unit) {
            onDispose {
                previewView = null
                dispatch_async(queue) { session.stopRunning() }
            }
        }
        UIKitView(
            factory = {
                CameraPreviewView(session, ::updateOrientation).also { previewView = it }
            },
            modifier = modifier,
        )
    }

    private fun updateOrientation(orientation: AVCaptureVideoOrientation) = dispatch_async(queue) {
        videoOrientation = orientation
        updatePhotoOrientation()
    }

    private fun updatePhotoOrientation() {
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            @Suppress("DEPRECATION") // videoRotationAngle needs iOS 17.
            if (connection.supportsVideoOrientation) connection.videoOrientation = videoOrientation
        }
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
        session.commitConfiguration()
        updatePhotoOrientation()
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            if (connection.supportsVideoMirroring) {
                // Mirror front shots so they match the preview, like the Android booth.
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = device.position == AVCaptureDevicePositionFront
            }
        }
        if (!session.running) session.startRunning()
        val mirrored = device.position == AVCaptureDevicePositionFront
        // Connections may not exist until configuration/start has finished, and switches replace them.
        onMain { previewView?.updateConnection(mirrored) }
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

/** Hosts the preview layer; UIKit and scene orientation are accessed only on main. */
private class CameraPreviewView(
    session: AVCaptureSession,
    private val onOrientationChanged: (AVCaptureVideoOrientation) -> Unit,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    private var mirrored = false
    private var videoOrientation: AVCaptureVideoOrientation? = null

    init {
        // Compose draws interop views through a hole in its own layer, so without a colour the
        // window behind shows through (white) until the camera delivers frames.
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        updateConnection()
    }

    fun updateConnection(mirrored: Boolean = this.mirrored) {
        this.mirrored = mirrored
        val orientation = when (window?.windowScene?.interfaceOrientation) {
            UIInterfaceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
            UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
            else -> return // Not attached to an oriented scene yet.
        }
        previewLayer.connection?.let { connection ->
            @Suppress("DEPRECATION") // videoRotationAngle needs iOS 17.
            if (connection.supportsVideoOrientation) connection.videoOrientation = orientation
            if (connection.supportsVideoMirroring) {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.videoMirrored = mirrored
            }
        }
        if (videoOrientation != orientation) {
            videoOrientation = orientation
            onOrientationChanged(orientation)
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        updateConnection()
        CATransaction.commit()
    }
}

@Composable
actual fun rememberBoothCamera(): BoothCamera = remember { IosBoothCamera() }
