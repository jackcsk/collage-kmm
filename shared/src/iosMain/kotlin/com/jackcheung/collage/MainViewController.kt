package com.jackcheung.collage

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point for the iOS app (`MainViewControllerKt.MainViewController()` in Swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { CollageApp() }
