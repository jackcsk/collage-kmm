package com.jackcheung.collage.ui.booth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private const val FIRST_COUNTDOWN = 3
private const val NEXT_COUNTDOWN = 2

/** Takes [BOOTH_SHOTS] photos in a row after a countdown, then hands them to [onFinished]. */
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
    var shots by remember { mutableStateOf<List<PhotoRef>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(Res.string.booth_capture_failed)

    val start: () -> Unit = start@{
        if (running) return@start
        running = true
        shots = emptyList()
        scope.launch {
            try {
                repeat(BOOTH_SHOTS) { shot ->
                    for (n in (if (shot == 0) FIRST_COUNTDOWN else NEXT_COUNTDOWN) downTo 1) {
                        countdown = n
                        delay(1_000)
                    }
                    countdown = null
                    launch {
                        flash.snapTo(1f)
                        flash.animateTo(0f, tween(durationMillis = 450))
                    }
                    shots = shots + camera.takePhoto()
                }
                onFinished(shots)
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().background(Color.DarkGray),
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                repeat(BOOTH_SHOTS) { i ->
                    val shape = RoundedCornerShape(4.dp)
                    Box(Modifier.size(56.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.5f), shape)) {
                        shots.getOrNull(i)?.let { photo ->
                            PhotoThumbnail(photo = photo, cache = thumbnails, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.booth_hint, BOOTH_SHOTS),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                val startDescription = stringResource(Res.string.booth_start)
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !running, role = Role.Button, onClick = start)
                        .semantics { contentDescription = startDescription }
                        .border(4.dp, Color.White, CircleShape)
                        .padding(8.dp)
                        .background(if (running) Color.White.copy(alpha = 0.4f) else Color.White, CircleShape),
                )
                if (camera.canSwitch) {
                    IconButton(
                        onClick = camera::switchCamera,
                        enabled = !running,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_cameraswitch),
                            contentDescription = stringResource(Res.string.booth_switch_camera),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
