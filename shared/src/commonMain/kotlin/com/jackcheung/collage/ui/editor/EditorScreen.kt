package com.jackcheung.collage.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jackcheung.collage.CollageViewModel
import com.jackcheung.collage.editor.SaveState
import com.jackcheung.collage.platform.PlatformBackHandler
import com.jackcheung.collage.platform.rememberImageSharer
import com.jackcheung.collage.platform.rememberPhotoPicker
import com.jackcheung.collage.platform.savedCollageMessage
import com.jackcheung.collage.resources.*
import com.jackcheung.collage.ui.adaptive.AdaptiveScaffold
import com.jackcheung.collage.ui.adaptive.BarItem
import com.jackcheung.collage.ui.adaptive.animateWindowBounds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditorScreen(viewModel: CollageViewModel) {
    val state = viewModel.editor ?: return
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Reads the editor when called, not when composed, so this lambda stays the same across gesture frames.
    val leave = { if (viewModel.editor?.edited == true) confirmDiscard = true else viewModel.closeEditor() }
    PlatformBackHandler(onBack = leave)

    val pickReplacement = rememberPhotoPicker(maxItems = 1) { photos ->
        photos.firstOrNull()?.let(viewModel::replaceSelected)
    }
    val share = rememberImageSharer()

    val saveState = viewModel.saveState
    val failedMessage = stringResource(Res.string.editor_save_failed)
    LaunchedEffect(saveState) {
        if (saveState == SaveState.Failed) {
            viewModel.dismissSaveResult()
            snackbar.showSnackbar(failedMessage)
        }
    }

    val hasSelection = state.selected != null
    val selectedTransform = state.selected?.let { state.cells[it].transform }
    AdaptiveScaffold(
        title = stringResource(Res.string.editor_title),
        navigation = BarItem(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.editor_back), leave),
        primaryAction = BarItem(
            icon = painterResource(Res.drawable.ic_save),
            label = stringResource(Res.string.editor_save),
            onClick = viewModel::save,
            enabled = saveState != SaveState.Saving,
        ),
        tools = listOf(
            BarItem(
                icon = painterResource(Res.drawable.ic_add_photo_alternate),
                label = stringResource(Res.string.editor_replace),
                onClick = pickReplacement,
                enabled = hasSelection,
            ),
            BarItem(
                icon = painterResource(Res.drawable.ic_flip),
                label = stringResource(Res.string.editor_mirror),
                onClick = viewModel::mirrorSelected,
                enabled = hasSelection,
                selected = selectedTransform?.mirrored == true,
            ),
            BarItem(
                icon = painterResource(Res.drawable.ic_flip),
                label = stringResource(Res.string.editor_flip),
                onClick = viewModel::flipSelected,
                enabled = hasSelection,
                selected = selectedTransform?.flipped == true,
                iconRotation = 90f,
            ),
            BarItem(
                icon = painterResource(if (state.border) Res.drawable.ic_border_outer else Res.drawable.ic_border_clear),
                label = stringResource(Res.string.editor_border),
                onClick = viewModel::toggleBorder,
                selected = state.border,
            ),
        ),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            ) {
                CollageEditorCanvas(
                    state = state,
                    image = viewModel::image,
                    onSelect = viewModel::selectCell,
                    onTransform = viewModel::transformCell,
                    onMoveDivider = viewModel::moveDivider,
                    onSwap = viewModel::swapCells,
                    modifier = Modifier.aspectRatio(1f).animateWindowBounds(),
                )
            }
            Text(
                text = stringResource(Res.string.editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }

    when (saveState) {
        SaveState.Saving -> SavingDialog()
        is SaveState.Saved -> SavedCollageDialog(
            onDismiss = viewModel::dismissSaveResult,
            onShare = {
                viewModel.dismissSaveResult()
                share(saveState.photo)
            },
            onNewLayout = viewModel::startNewLayout,
        )
        SaveState.Idle, SaveState.Failed -> Unit
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.editor_discard_title)) },
            text = { Text(stringResource(Res.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.closeEditor()
                }) { Text(stringResource(Res.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.editor_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedCollageDialog(onDismiss: () -> Unit, onShare: () -> Unit, onNewLayout: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
                Text(
                    text = stringResource(Res.string.editor_saved_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(savedCollageMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                SavedAction(
                    icon = painterResource(Res.drawable.ic_share),
                    label = stringResource(Res.string.editor_share),
                    onClick = onShare,
                )
                SavedAction(
                    icon = painterResource(Res.drawable.ic_add_photo_alternate),
                    label = stringResource(Res.string.editor_new_layout),
                    onClick = onNewLayout,
                )
                SavedAction(
                    icon = painterResource(Res.drawable.ic_edit),
                    label = stringResource(Res.string.editor_keep_editing),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SavedAction(icon: Painter, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun SavingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
                CircularProgressIndicator()
                Spacer(Modifier.width(16.dp))
                Text(stringResource(Res.string.editor_saving))
            }
        }
    }
}
