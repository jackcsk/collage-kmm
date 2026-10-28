package com.jackcheung.collage.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource

/** Asks for photo library access; [onResult] runs once the user has answered. */
interface MediaAccessRequester {
    fun request()

    /** Lets a user with [MediaAccess.Partial] change which photos the app can see. */
    fun manageSelection()
}

@Composable
expect fun rememberMediaAccessRequester(onResult: () -> Unit): MediaAccessRequester

/** Returns a launcher for the system photo picker, which needs no library permission. */
@Composable
expect fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): () -> Unit

@Composable
expect fun rememberAppSettingsOpener(): () -> Unit

/** Returns a function that opens the share sheet for a saved collage. */
@Composable
expect fun rememberImageSharer(): (PhotoRef) -> Unit

/** System back (Android back button/gesture); iOS has none, so it is a no-op there. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** The platform's own colours (Android dynamic colour), or null to use the app's palette. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?

/** Light status/navigation bar icons while composed, for always-dark screens. */
@Composable
expect fun LightSystemBarIcons()

/** Where a saved collage ends up, shown after saving. */
expect val savedCollageMessage: StringResource

interface PermissionState {
    val granted: Boolean

    /** The user refused; asking again may not show a prompt, so offer settings instead. */
    val denied: Boolean

    fun request()
}

@Composable
expect fun rememberCameraPermission(): PermissionState

/** A camera for the photo booth. Shots go to the booth directory managed by [MediaRepository]. */
interface BoothCamera {
    /** Whether both front and back cameras exist. */
    val canSwitch: Boolean
    val front: Boolean

    fun switchCamera()

    /** Takes a photo, playing the shutter sound. Front shots are mirrored to match the preview. */
    suspend fun takePhoto(): PhotoRef

    @Composable
    fun Preview(modifier: Modifier)
}

@Composable
expect fun rememberBoothCamera(): BoothCamera
