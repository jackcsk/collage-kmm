package com.jackcheung.collage.platform

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.jackcheung.collage.resources.Res
import com.jackcheung.collage.resources.editor_saved_body_android
import com.jackcheung.collage.resources.editor_share_chooser
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberMediaAccessRequester(onResult: () -> Unit): MediaAccessRequester {
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { callback() }
    return remember(launcher) {
        object : MediaAccessRequester {
            override fun request() = launcher.launch(mediaPermissions)

            // Android 14 shows its photo selection again when the permissions are re-requested.
            override fun manageSelection() = launcher.launch(mediaPermissions)
        }
    }
}

@Composable
actual fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    return if (maxItems > 1) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
            callback(uris.map { PhotoRef(it.toString()) })
        }
        remember(launcher) { { launcher.launch(request) } }
    } else {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            callback(listOfNotNull(uri).map { PhotoRef(it.toString()) })
        }
        remember(launcher) { { launcher.launch(request) } }
    }
}

@Composable
actual fun rememberAppSettingsOpener(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
            )
        }
    }
}

@Composable
actual fun rememberImageSharer(): (PhotoRef) -> Unit {
    val context = LocalContext.current
    val title = stringResource(Res.string.editor_share_chooser)
    return remember(context, title) {
        { photo ->
            val uri = photo.uri
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, title))
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

@Composable
actual fun LightSystemBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

actual val savedCollageMessage: StringResource = Res.string.editor_saved_body_android

@Composable
actual fun rememberCameraPermission(): PermissionState {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    return remember(launcher) {
        object : PermissionState {
            override val granted get() = granted
            override val denied get() = denied

            override fun request() = launcher.launch(Manifest.permission.CAMERA)
        }
    }
}
