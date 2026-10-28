package com.jackcheung.collage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jackcheung.collage.platform.rememberMediaRepository
import com.jackcheung.collage.ui.booth.PhotoBoothScreen
import com.jackcheung.collage.ui.editor.EditorScreen
import com.jackcheung.collage.ui.home.HomeScreen
import com.jackcheung.collage.ui.theme.CollageTheme

/** The whole app; hosted by the Android activity and the iOS view controller. */
@Composable
fun CollageApp() {
    val repository = rememberMediaRepository()
    val viewModel = viewModel { CollageViewModel(repository) }
    CollageTheme {
        AnimatedContent(
            targetState = viewModel.screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(viewModel)
                Screen.Editor -> EditorScreen(viewModel)
                Screen.PhotoBooth -> PhotoBoothScreen(
                    thumbnails = viewModel.gridThumbnails,
                    onClose = viewModel::closePhotoBooth,
                    onFinished = viewModel::onBoothFinished,
                )
            }
        }
    }
}
