# STVR screensaver fix — 4 October 2026

STVR resolves to the native Media3 player. Its Compose playback screen did not
request that Android keep the display on. Video decoding and the MediaSession
alone do not prevent Android TV ambient mode, matching the reported screensaver
after 15 minutes on MI BOX4.

`PlayerScreen` now installs `NativePlaybackKeepScreenOn`. It sets the host view's
`keepScreenOn` while the foreground player requests playback and is ready or
buffering. This also covers other native channels and brief rebuffering. It
releases the request on pause, playback suppression, idle/error, end, background,
player replacement, or leaving the screen, and removes its listeners on disposal.
An existing keep-screen-on request on the host view is preserved.

The view property uses Android's supported screen-on mechanism; no wake-lock
permission, device screensaver-setting change, or simulated remote input is needed.
See [Android's screen-on and TV ambient-mode guidance](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

## Verification

- All 70 unit tests passed.
- Debug APK, instrumentation APK, and Android lint passed.
- `NativePlaybackKeepScreenOnTest` covers startup, playing/rebuffering, pause,
  stop/error/end, suppression, background/resume, disposal, and player replacement.
- The new instrumentation suite compiled but could not execute: the local TV
  emulator stayed offline before Android booted. Runtime assertions are unverified.
- No physical MI BOX4 was connected. A sustained on-device run beyond 15 minutes
  remains necessary to confirm behavior with its firmware.

Run the focused regression suite on a connected Android TV device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.player.NativePlaybackKeepScreenOnTest'
```
