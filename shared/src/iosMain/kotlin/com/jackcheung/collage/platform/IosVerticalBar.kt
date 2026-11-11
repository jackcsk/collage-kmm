package com.jackcheung.collage.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jackcheung.collage.ui.adaptive.SystemVerticalBar

/**
 * UIKit's vertical bar placement (iOS 27.1, iPhone Duo). Kotlin/Native's UIKit bindings predate
 * that SDK, so the Swift host reads it and reports it through `setVerticalBar`.
 */
internal object IosVerticalBar {
    var bar by mutableStateOf<SystemVerticalBar?>(null)
}

@Composable
actual fun systemVerticalBar(): SystemVerticalBar? = IosVerticalBar.bar
