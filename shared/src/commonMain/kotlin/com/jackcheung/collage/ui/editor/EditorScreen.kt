package com.jackcheung.collage.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val SaveEndPadding = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: CollageViewModel) {
    val state = viewModel.editor ?: return
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.edited) confirmDiscard = true else viewModel.closeEditor() }
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.editor_back))
                    }
                },
                title = { Text(stringResource(Res.string.editor_title)) },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = saveState != SaveState.Saving,
                        modifier = Modifier.padding(end = SaveEndPadding),
                    ) {
                        Text(stringResource(Res.string.editor_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        CollageEditorCanvas(
                            state = state,
                            image = viewModel::image,
                            onSelect = viewModel::selectCell,
                            onTransform = viewModel::transformCell,
                            onMoveDivider = viewModel::moveDivider,
                            onSwap = viewModel::swapCells,
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                    Text(
                        text = stringResource(Res.string.editor_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (landscape) 0.dp else 8.dp,
                        ),
                    )
                    val tools: @Composable () -> Unit = {
                        val hasSelection = state.selected != null
                        val selectedTransform = state.selected?.let { state.cells[it].transform }
                        val toolModifier = if (landscape) Modifier.fillMaxWidth() else Modifier
                        ToolButton(
                            icon = painterResource(Res.drawable.ic_add_photo_alternate),
                            label = stringResource(Res.string.editor_replace),
                            enabled = hasSelection,
                            onClick = pickReplacement,
                            modifier = toolModifier,
                        )
                        ToggleToolButton(
                            icon = painterResource(Res.drawable.ic_flip),
                            label = stringResource(Res.string.editor_mirror),
                            enabled = hasSelection,
                            checked = selectedTransform?.mirrored == true,
                            onToggle = viewModel::mirrorSelected,
                            modifier = toolModifier,
                        )
                        ToggleToolButton(
                            icon = painterResource(Res.drawable.ic_flip),
                            iconRotation = 90f,
                            label = stringResource(Res.string.editor_flip),
                            enabled = hasSelection,
                            checked = selectedTransform?.flipped == true,
                            onToggle = viewModel::flipSelected,
                            modifier = toolModifier,
                        )
                        ToggleToolButton(
                            icon = painterResource(if (state.border) Res.drawable.ic_border_outer else Res.drawable.ic_border_clear),
                            label = stringResource(Res.string.editor_border),
                            enabled = true,
                            checked = state.border,
                            onToggle = viewModel::toggleBorder,
                            modifier = toolModifier,
                        )
                    }
                    if (landscape) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // Match the app bar's 4dp content inset plus the Save button's padding.
                            modifier = Modifier.padding(end = 4.dp + SaveEndPadding).width(80.dp)
                                .verticalScroll(rememberScrollState()),
                        ) { tools() }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) { tools() }
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val tools = measurables[2].measure(loose)
                val contentWidth = if (landscape) (width - tools.width).coerceAtLeast(0) else width
                val contentHeight = if (landscape) height else (height - tools.height).coerceAtLeast(0)
                val hint = measurables[1].measure(loose.copy(maxWidth = contentWidth, maxHeight = contentHeight))
                val previewHeight = (contentHeight - hint.height).coerceAtLeast(0)
                val preview = measurables[0].measure(Constraints.fixed(contentWidth, previewHeight))
                layout(width, height) {
                    preview.placeRelative(0, 0)
                    hint.placeRelative(0, previewHeight)
                    tools.placeRelative(
                        x = if (landscape) width - tools.width else 0,
                        y = if (landscape) (height - tools.height) / 2 else height - tools.height,
                    )
                }
            }
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

@Composable
private fun ToolButton(icon: Painter, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ToolLayout(
        icon = icon,
        label = label,
        enabled = enabled,
        active = false,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun ToggleToolButton(
    icon: Painter,
    label: String,
    enabled: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    iconRotation: Float = 0f,
    modifier: Modifier = Modifier,
) {
    ToolLayout(
        icon = icon,
        label = label,
        enabled = enabled,
        active = checked,
        iconRotation = iconRotation,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) { onToggle() },
    )
}

@Composable
private fun ToolLayout(
    icon: Painter,
    label: String,
    enabled: Boolean,
    active: Boolean,
    modifier: Modifier,
    iconRotation: Float = 0f,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.rotate(iconRotation))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
