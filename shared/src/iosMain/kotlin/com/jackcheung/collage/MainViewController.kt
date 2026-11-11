package com.jackcheung.collage

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.jackcheung.collage.platform.IosVerticalBar
import com.jackcheung.collage.ui.adaptive.BarEdge
import com.jackcheung.collage.ui.adaptive.SystemVerticalBar
import platform.UIKit.UIViewController

/** Entry point for the iOS app (`MainViewControllerKt.MainViewController()` in Swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { CollageApp() }

/**
 * Reports where UIKit places the vertical bar, from Swift (`MainViewControllerKt.setVerticalBar`).
 * [edge] is 0 for no vertical bar, 1 leading, 2 trailing, as `ComposeHostController` maps
 * `UITraitCollection.verticalBarEdge`. The rest is the bar's layout region in points: its distance
 * from the top, the bottom and its own edge of the view, and its width.
 */
fun setVerticalBar(edge: Int, top: Double, bottom: Double, edgeInset: Double, width: Double) {
    val barEdge = when (edge) {
        1 -> BarEdge.Start
        2 -> BarEdge.End
        else -> null
    }
    IosVerticalBar.bar = barEdge?.let {
        SystemVerticalBar(it, top = top.dp, bottom = bottom.dp, edgeInset = edgeInset.dp, width = width.dp)
    }
}
