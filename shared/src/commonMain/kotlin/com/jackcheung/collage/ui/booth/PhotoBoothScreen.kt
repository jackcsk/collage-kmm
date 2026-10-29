package com.jackcheung.collage.ui.booth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackcheung.collage.platform.BoothCamera
import com.jackcheung.collage.platform.LightSystemBarIcons
import com.jackcheung.collage.platform.PhotoRef
import com.jackcheung.collage.platform.PlatformBackHandler
import com.jackcheung.collage.platform.logError
import com.jackcheung.collage.platform.rememberAppSettingsOpener
import com.jackcheung.collage.platform.rememberBoothCamera
import com.jackcheung.collage.platform.rememberCameraPermission
import com.jackcheung.collage.resources.*
import com.jackcheung.collage.ui.PhotoThumbnail
import com.jackcheung.collage.ui.ThumbnailCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val BOOTH_SHOTS = 4
private const val COUNTDOWN_SECONDS = 3

/** Collects [BOOTH_SHOTS] photos, one per shutter press, then hands them to [onFinished]. */
@Composable
fun PhotoBoothScreen(thumbnails: ThumbnailCache, onClose: () -> Unit, onFinished: (List<PhotoRef>) -> Unit) {
    val permission = rememberCameraPermission()
    val openSettings = rememberAppSettingsOpener()
    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }
    PlatformBackHandler(onBack = onClose)
    LightSystemBarIcons()

    Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.booth_close))
                }
                Text(stringResource(Res.string.booth_title), style = MaterialTheme.typography.titleLarge)
            }
            if (permission.granted) {
                BoothCameraContent(camera = rememberBoothCamera(), thumbnails = thumbnails, onFinished = onFinished)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                ) {
                    Text(stringResource(Res.string.booth_permission_body), textAlign = TextAlign.Center)
                    if (permission.denied) {
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = openSettings) { Text(stringResource(Res.string.permission_settings)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoothCameraContent(camera: BoothCamera, thumbnails: ThumbnailCache, onFinished: (List<PhotoRef>) -> Unit) {
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownEnabled by rememberSaveable { mutableStateOf(true) }
    var shots by remember { mutableStateOf<List<PhotoRef>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(Res.string.booth_capture_failed)

    val captureShot: () -> Unit = captureShot@{
        if (running || shots.size >= BOOTH_SHOTS) return@captureShot
        running = true
        val useCountdown = countdownEnabled
        scope.launch {
            try {
                if (useCountdown) {
                    for (n in COUNTDOWN_SECONDS downTo 1) {
                        countdown = n
                        delay(1_000)
                    }
                }
                countdown = null
                launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(durationMillis = 450))
                }
                shots = shots + camera.takePhoto()
                if (shots.size == BOOTH_SHOTS) {
                    onFinished(shots)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Photo booth capture failed", e)
                snackbar.showSnackbar(failedMessage)
            } finally {
                running = false
                countdown = null
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val sidePadding = if (landscape) 16.dp else 0.dp
        val shotSpacing = 8.dp
        val shotGaps = shotSpacing * (BOOTH_SHOTS - 1)
        val railWidth = 80.dp
        val panelGap = 16.dp
        val controlsMinWidth = 96.dp * 2 + shotSpacing + panelGap + railWidth
        val previewSize = if (landscape) {
            minOf(maxHeight, (maxWidth - sidePadding * 2 - 24.dp - controlsMinWidth).coerceAtLeast(0.dp))
        } else {
            maxWidth
        }
        val controlsWidth = if (landscape) maxWidth - sidePadding * 2 - previewSize - 24.dp else maxWidth
        val thumbnailSize = minOf(96.dp, (controlsWidth - shotGaps).coerceAtLeast(0.dp) / BOOTH_SHOTS)

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clipToBounds().background(Color.DarkGray),
                ) {
                    camera.Preview(Modifier.fillMaxSize())
                    countdown?.let { n ->
                        Text(
                            text = "$n",
                            style = TextStyle(
                                fontSize = 120.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 16f),
                            ),
                        )
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
                }

                if (landscape) {
                    Column(verticalArrangement = Arrangement.spacedBy(shotSpacing)) {
                        repeat(2) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(shotSpacing),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                repeat(2) { column ->
                                    BoothShotSlot(
                                        photo = shots.getOrNull(row * 2 + column),
                                        thumbnails = thumbnails,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(shotSpacing, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    ) {
                        repeat(BOOTH_SHOTS) { i ->
                            BoothShotSlot(
                                photo = shots.getOrNull(i),
                                thumbnails = thumbnails,
                                modifier = Modifier.size(thumbnailSize),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.booth_hint, (shots.size + 1).coerceAtMost(BOOTH_SHOTS), BOOTH_SHOTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                val countdownControl: @Composable () -> Unit = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.toggleable(
                            value = countdownEnabled,
                            enabled = !running,
                            role = Role.Switch,
                            onValueChange = { countdownEnabled = it },
                        ),
                    ) {
                        Switch(checked = countdownEnabled, onCheckedChange = null, enabled = !running)
                        Text(
                            text = stringResource(Res.string.booth_countdown),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                val shutter: @Composable () -> Unit = {
                    val takePhotoDescription = stringResource(Res.string.booth_take_photo)
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !running && shots.size < BOOTH_SHOTS, role = Role.Button, onClick = captureShot)
                            .semantics { contentDescription = takePhotoDescription }
                            .border(4.dp, Color.White, CircleShape)
                            .padding(8.dp)
                            .background(if (running) Color.White.copy(alpha = 0.4f) else Color.White, CircleShape),
                    )
                }
                val switchCamera: @Composable () -> Unit = {
                    IconButton(onClick = camera::switchCamera, enabled = !running) {
                        Icon(
                            painterResource(Res.drawable.ic_cameraswitch),
                            contentDescription = stringResource(Res.string.booth_switch_camera),
                        )
                    }
                }
                if (landscape) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(panelGap),
                        modifier = Modifier.width(railWidth).verticalScroll(rememberScrollState()),
                    ) {
                        countdownControl()
                        shutter()
                        if (camera.canSwitch) switchCamera()
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        shutter()
                        Box(Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) { countdownControl() }
                        if (camera.canSwitch) {
                            Box(Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) { switchCamera() }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val controlsWidthPx = if (landscape) railWidth.roundToPx() else width
            val controls = measurables[3].measure(loose.copy(minWidth = controlsWidthPx, maxWidth = controlsWidthPx))
            val panelWidth = controlsWidth.roundToPx()
            val gridWidth = if (landscape) (panelWidth - controls.width - panelGap.roundToPx()).coerceAtLeast(0) else width
            val hint = measurables[2].measure(loose.copy(minWidth = gridWidth, maxWidth = gridWidth))
            val gridHintGap = minOf(panelGap.roundToPx(), (height - hint.height).coerceAtLeast(0))
            val shotSlots = if (landscape) {
                val side = minOf(gridWidth, (height - hint.height - gridHintGap).coerceAtLeast(0))
                measurables[1].measure(Constraints.fixed(side, side))
            } else {
                measurables[1].measure(loose.copy(minWidth = width, maxWidth = width))
            }
            val previewSide = if (landscape) {
                previewSize.roundToPx()
            } else {
                minOf(width, (height - shotSlots.height - hint.height - controls.height).coerceAtLeast(0))
            }
            val preview = measurables[0].measure(Constraints.fixed(previewSide, previewSide))
            layout(width, height) {
                preview.placeRelative(
                    x = if (landscape) sidePadding.roundToPx() else (width - previewSide) / 2,
                    y = if (landscape) (height - previewSide) / 2 else 0,
                )
                if (landscape) {
                    val gridX = width - sidePadding.roundToPx() - panelWidth
                    val gridY = (height - shotSlots.height - gridHintGap - hint.height) / 2
                    shotSlots.placeRelative(gridX + (gridWidth - shotSlots.width) / 2, gridY)
                    hint.placeRelative(gridX, gridY + shotSlots.height + gridHintGap)
                    controls.placeRelative(
                        x = width - sidePadding.roundToPx() - controls.width,
                        y = (height - controls.height) / 2,
                    )
                } else {
                    val controlsY = height - controls.height
                    val hintY = controlsY - hint.height
                    shotSlots.placeRelative(0, hintY - shotSlots.height)
                    hint.placeRelative(0, hintY)
                    controls.placeRelative(0, controlsY)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BoothShotSlot(photo: PhotoRef?, thumbnails: ThumbnailCache, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.5f), shape)) {
        photo?.let { PhotoThumbnail(photo = it, cache = thumbnails, modifier = Modifier.fillMaxSize()) }
    }
}
