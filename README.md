# Tellyo Android TV

Android TV / Google TV app for live television and programme guides, designed for D-pad remotes.

## Features

- Native Media3 playback for STVR, Slovak IPTV and configured authorized HLS/DASH streams.
- Official Markíza, JOJ and TA3 web players with broadcaster-managed advertising and account access.
- Cached programme guides for 20 channels, with priority-aware imports and optional XMLTV overrides.
- Persistent favorites, channel ordering, last-channel autoplay and playback quality settings.
- Connection status, foreground-aware recovery that respects manual pause, and playback diagnostics.

External streams depend on provider availability, network and region. JOJ and Plus require an account in their official players.

## Build and run

Requires JDK 17 or newer, Android SDK 35, and an Android TV device or emulator running Android 6.0 or newer. Set `JAVA_HOME` and `ANDROID_HOME` to your local installations; add Android SDK platform-tools to `PATH`.

On Windows, build, test and lint, then install on a connected TV:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.tellyo.app 1
```

On Linux/macOS, use `bash ./gradlew` for Gradle commands. TV instrumentation tests use `:app:connectedDebugAndroidTest` and require a running emulator or device. Live-provider tests are opt-in.

For the local Windows TV emulator, run `scripts/start-tv-emulator.ps1` and keep its terminal open. See the [emulator setup notes](docs/emulator-startup-validation.md).

## Configuration and documentation

The bundled channel catalogue and cached data provide local fallbacks. An authorized HTTPS metadata endpoint can supply channels, provider routes and XMLTV; see the [configuration contract](docs/authorized-metadata.md). Configure keystore secrets to use the **Signed Android release** GitHub Actions workflow.

- [UI design](docs/ui-design.md) and [TV navigation fixes](docs/tv-ux-fixes-2026-10-04.md)
- [IPTV validation](docs/iptv-validation.md) and [programme guide coverage](docs/programme-guide-validation-2026-10-06.md)
- [Latest code review, fixes and test results](docs/app-code-review-fixes-2026-10-06.md)
