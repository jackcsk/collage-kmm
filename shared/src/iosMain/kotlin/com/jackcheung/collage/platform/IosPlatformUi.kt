package com.jackcheung.collage.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import com.jackcheung.collage.resources.Res
import com.jackcheung.collage.resources.editor_saved_body_ios
import kotlinx.cinterop.useContents
import org.jetbrains.compose.resources.StringResource
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.PhotosUI.presentLimitedLibraryPickerFromViewController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.popoverPresentationController
import platform.UIKit.setStatusBarStyle
import platform.UIKit.statusBarStyle
import platform.darwin.NSObject
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberMediaAccessRequester(onResult: () -> Unit): MediaAccessRequester {
    val callback by rememberUpdatedState(onResult)
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        object : MediaAccessRequester {
            override fun request() {
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { _ -> onMain { callback() } }
            }

            override fun manageSelection() {
                PHPhotoLibrary.sharedPhotoLibrary().presentLimitedLibraryPickerFromViewController(
                    viewController.topPresented(),
                ) { _ -> onMain { callback() } }
            }
        }
    }
}

@Composable
actual fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val viewController = LocalUIViewController.current
    // The picker holds its delegate weakly, so it lives here.
    val delegate = remember { PickerDelegate { photos -> callback(photos) } }
    return remember(viewController, maxItems) {
        {
            val configuration = PHPickerConfiguration().apply {
                selectionLimit = maxItems.toLong()
                filter = PHPickerFilter.imagesFilter
            }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            viewController.topPresented().presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** Copies each picked image into the app's cache, since the provider's file is temporary. */
private class PickerDelegate(private val onPicked: (List<PhotoRef>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return
        val copies = arrayOfNulls<PhotoRef>(results.size)
        val group = dispatch_group_create()
        results.forEachIndexed { index, result ->
            dispatch_group_enter(group)
            result.itemProvider.loadFileRepresentationForTypeIdentifier("public.image") { url: NSURL?, _: NSError? ->
                val extension = url?.pathExtension?.takeIf { it.isNotEmpty() } ?: "jpg"
                val destination = "${pickedDirectory()}/${NSUUID().UUIDString}.$extension"
                if (url != null && NSFileManager.defaultManager.copyItemAtURL(url, NSURL.fileURLWithPath(destination), error = null)) {
                    copies[index] = filePhoto(destination)
                }
                dispatch_group_leave(group)
            }
        }
        dispatch_group_notify(group, dispatch_get_main_queue()) { onPicked(copies.filterNotNull()) }
    }
}

@Composable
actual fun rememberAppSettingsOpener(): () -> Unit = remember {
    {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}

@Composable
actual fun rememberImageSharer(): (PhotoRef) -> Unit {
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        { photo ->
            val path = photo.filePath
            if (path != null) {
                val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
                val presenter = viewController.topPresented()
                // iPad shows the sheet as a popover, which needs an anchor.
                sheet.popoverPresentationController?.let { popover ->
                    popover.sourceView = presenter.view
                    presenter.view.bounds.useContents {
                        popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
                    }
                }
                presenter.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }
}

/** iOS has no system back button; every screen has its own back or close control. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null

/** Needs `UIViewControllerBasedStatusBarAppearance` = NO in Info.plist. */
@Suppress("DEPRECATION")
@Composable
actual fun LightSystemBarIcons() {
    DisposableEffect(Unit) {
        val application = UIApplication.sharedApplication
        val previous = application.statusBarStyle
        application.setStatusBarStyle(UIStatusBarStyleLightContent, animated = true)
        onDispose { application.setStatusBarStyle(previous, animated = true) }
    }
}

actual val savedCollageMessage: StringResource = Res.string.editor_saved_body_ios

@Composable
actual fun rememberCameraPermission(): PermissionState {
    var status by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) }
    return remember {
        object : PermissionState {
            override val granted get() = status == AVAuthorizationStatusAuthorized
            override val denied get() = status == AVAuthorizationStatusDenied || status == AVAuthorizationStatusRestricted

            override fun request() {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    onMain { status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
                }
            }
        }
    }
}
