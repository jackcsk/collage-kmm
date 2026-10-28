# Collage

A photo collage maker in the spirit of Instagram's discontinued Layout app, written in Kotlin with
Compose Multiplatform for Android and iOS.

| Platform | Status |
|---|---|
| Android | Tested manually on an emulator (API 35) and a Pixel 7 (Android 17) |
| iOS | Compiles; not yet run on a simulator or device |

## Features

- Pick up to 9 photos from the gallery, a **Faces** tab (on-device face detection) or the system
  photo picker; works with Android 14+ / iOS limited photo access.
- Layout previews for the selected count (1–9 photos, 4–7 layouts each), drawn with your photos.
- Editor: tap to select, drag/pinch to pan and zoom, drag the lines between photos to resize,
  hold and drag to swap; Replace, Mirror, Flip and Border.
- Saves a 2048×2048 JPEG (Android: `Pictures/Collage`; iOS: the photo library) and opens the share
  sheet. The editor and the export share the same drawing code, so the file matches the screen.
- Photo booth: 3-2-1 countdown, four shots in a row, straight into a 2×2 layout.

## Project layout

| Module | Contents |
|---|---|
| `shared/src/commonMain` | Layout model and maths, view model, all screens, drawing and export, strings and icons |
| `shared/src/androidMain` | MediaStore, `FaceDetector`, photo picker, CameraX |
| `shared/src/iosMain` | Photos, Vision, PHPicker, AVFoundation, share sheet |
| `androidApp` | Activity hosting the shared app; application id `com.jackcheung.collage.android` |
| `iosApp` | Xcode project hosting the shared app; bundle id `com.jackcheung.collage.ios` |

## Build

Requirements: JDK 17 or newer. Android needs the Android SDK (platform 37); iOS needs a Mac with
Xcode and Apple Silicon for the simulator.

```shell
./gradlew :androidApp:assembleDebug         # androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :shared:testAndroidHostTest       # unit tests for the layout model
./gradlew :shared:compileKotlinIosArm64     # type-checks the iOS code (also works on Linux)
```

### iOS

1. Set your Apple team ID in `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`).
2. Open `iosApp/iosApp.xcodeproj` and run. Xcode builds the shared framework through Gradle, so
   the JDK must be installed system-wide (or set `JAVA_HOME` in the "Compile Kotlin Framework"
   build phase).
3. The photo booth needs a real device; the simulator has no camera.

## License

[MIT](LICENSE)
