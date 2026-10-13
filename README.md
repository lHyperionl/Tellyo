# Tellyo Android TV

Android TV / Google TV app for live television and programme guides, designed for D-pad remotes.

## Features

- Native Media3 playback for STVR, Slovak and selected Czech IPTV feeds, and configured authorized HLS/DASH streams.
- Official Markíza, JOJ and TA3 web players with account access and optional filtering of separate ad requests (enabled by default).
- Cached programme guides for 20 channels, with priority-aware imports and optional XMLTV overrides.
- Persistent favorites, channel ordering, last-channel autoplay and playback quality settings.
- Connection status, foreground-aware recovery that respects manual pause, and playback diagnostics.
- Startup update checks with remote-friendly prompts, verified APK downloads and Android installer confirmation.

External streams depend on provider availability, network and region. JOJ and Plus require an account in their official players.

Embedded players use a small built-in ad-host filter, not the uBlock Origin extension.
Disable **Blokovať reklamy v prehrávači** in Settings if a player fails to start.
Ads inside the broadcast remain; see [embedded ad filtering](docs/embedded-ad-filtering.md).

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

The bundled channel catalogue and cached data provide local fallbacks. An authorized HTTPS metadata endpoint can supply channels, provider routes and XMLTV; see the [configuration contract](docs/authorized-metadata.md). The **Signed Android release** workflow publishes signed public updates on default-branch pushes; see [update setup and signing](docs/app-updates.md).

- [UI design](docs/ui-design.md) and [TV navigation fixes](docs/tv-ux-fixes-2026-10-04.md)
- [IPTV validation](docs/iptv-validation.md) and [programme guide coverage](docs/programme-guide-validation-2026-10-06.md)
- [Czech IPTV selection and validation](docs/czech-iptv-validation.md)
- [Latest code review, fixes and test results](docs/app-code-review-fixes-2026-10-06.md)

## License

Tellyo's source code is available under the [MIT license](LICENSE).
Broadcaster brands, logos and content remain the property of their owners.
