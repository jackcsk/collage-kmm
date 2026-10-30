package com.jackcheung.collage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jackcheung.collage.platform.SharedPhotoInbox
import com.jackcheung.collage.platform.rememberMediaRepository
import com.jackcheung.collage.platform.rememberSharedPhotoInbox
import com.jackcheung.collage.resources.Res
import com.jackcheung.collage.resources.editor_cancel
import com.jackcheung.collage.resources.editor_discard
import com.jackcheung.collage.resources.editor_discard_body
import com.jackcheung.collage.resources.editor_discard_title
import com.jackcheung.collage.ui.booth.PhotoBoothScreen
import com.jackcheung.collage.ui.editor.EditorScreen
import com.jackcheung.collage.ui.home.HomeScreen
import com.jackcheung.collage.ui.theme.CollageTheme
import org.jetbrains.compose.resources.stringResource

/** The whole app; hosted by the Android activity and the iOS view controller. */
@Composable
fun CollageApp() {
    val repository = rememberMediaRepository()
    val viewModel = viewModel { CollageViewModel(repository) }
    val inbox = rememberSharedPhotoInbox()
    CollageTheme {
        SharedPhotos(inbox, viewModel)
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

/** Hands shared photos to [viewModel], first asking before an edited layout is thrown away. */
@Composable
private fun SharedPhotos(inbox: SharedPhotoInbox, viewModel: CollageViewModel) {
    val shared = inbox.pending
    if (shared.isEmpty()) return
    if (viewModel.editor?.edited != true) {
        LaunchedEffect(shared) { viewModel.receiveShared(inbox.take()) }
        return
    }
    AlertDialog(
        onDismissRequest = { inbox.take() },
        title = { Text(stringResource(Res.string.editor_discard_title)) },
        text = { Text(stringResource(Res.string.editor_discard_body)) },
        confirmButton = {
            TextButton(onClick = { viewModel.receiveShared(inbox.take()) }) { Text(stringResource(Res.string.editor_discard)) }
        },
        dismissButton = {
            TextButton(onClick = { inbox.take() }) { Text(stringResource(Res.string.editor_cancel)) }
        },
    )
}
