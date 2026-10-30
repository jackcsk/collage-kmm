package com.jackcheung.collage

import androidx.compose.ui.window.ComposeUIViewController
import com.jackcheung.collage.platform.SharedPhotoInbox
import com.jackcheung.collage.platform.takeSharedPhotos
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController

/** Entry point for the iOS app (`MainViewControllerKt.MainViewController()` in Swift). */
fun MainViewController(): UIViewController {
    val inbox = SharedPhotoInbox()
    // The share extension opens the app after leaving images behind; this also covers launches.
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ -> inbox.deliver(takeSharedPhotos()) }
    return ComposeUIViewController { CollageApp(inbox) }
}
