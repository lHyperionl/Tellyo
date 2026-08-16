# Tellyo Android TV

Initial Android TV / Google TV foundation for the Tellyo shell.

The current build contains the approved dark navy / electric blue Phase 4 dashboard, D-pad focus behavior, a Media3 playback proof of concept, and provider/channel boundaries for future integrations. The dashboard follows the supplied 1672×941 reference composition and scales that design canvas to a 16:9 TV window. The player uses one public development HLS test asset configured in `PreviewTvProvider`; it is not a broadcaster integration. The project contains no authentication, DRM, scraping, timeshift, backend, or ad-detection code.

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot"
$env:ANDROID_HOME = "<Android SDK path>"
.\gradlew.bat :app:assembleDebug
```

Useful checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Install the debug APK on a running Android TV emulator or connected TV device:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.tellyo.app 1
```

The primary application package is `com.tellyo.app`.
