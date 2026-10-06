# Markíza autoplay and Back verification

Tested on 2 October 2026 with the Android TV API 34 emulator.

Markíza and Doma now use the official embed's `?autoplay=1` option. The broadcaster's
response sets its player configuration to `autoplay: true`. Tellyo already permits
media playback without a WebView user gesture. The official player handles its
normal ads and live video.

## Audio correction

The first autoplay verification checked video but missed that the official player
starts unattended autoplay muted. The emulator also had `hw.audioOutput=no`.
Speaker output is now enabled in the local AVD configuration and Android's media
volume is set to 10/15.

Once the first video begins, Tellyo sends the broadcaster's normal Play command
through its message API to fulfil the channel-opening request with audio. This
clears the player's autoplay mute using its own controls, preserves ads and runs
once per page so later manual mute choices are respected. It is limited to the
official `https://media.cms.markiza.sk/embed/` destination.

The regression test now requires an unmuted video, positive player volume and
increasing decoded audio bytes in addition to advancing video frames. Android's
audio service also showed a started, unmuted stereo AAudio output after this change.
The audio-enhanced test passed for both channels' startup and Markíza's full
pre-roll-to-live transition. Windows' speaker and emulator mixer controls were
also unmuted. Audio evidence is in `artifacts/broadcasters/autoplay-audio-report.json`
and `artifacts/broadcasters/autoplay-audio-logcat.txt`.

The opt-in `OfficialEmbedPlaybackTest` passed using the actual MainActivity,
HomeViewModel and embedded WebView:

- Markíza and Doma decoded video with advancing time without touch, Play or a remote confirmation.
- Android Back, Escape and Backspace returned to Tellyo during ads. The WebView was
  removed, native playback was idle, and MainActivity stayed open.
- Markíza was reopened and played all six pre-roll ads without input. It then reached
  LIVE with advancing video time and decoded 1024-pixel-wide frames.
- Android Back during the live programme returned home and removed the WebView.

Back handling already worked; no production Back change was necessary. Disposal
stops loading, navigates to a blank page and destroys the WebView, ending its playback.

This live result supersedes the earlier inconclusive Markíza result in
`broadcaster-playback-validation.md`. External JOJ/Dajto/Krimi/Klasik routes were
not part of this change.

Evidence: `artifacts/broadcasters/autoplay-back-report.json` and
`artifacts/broadcasters/autoplay-logcat.txt`. Build, unit tests and lint passed.

Reproduce on a connected Android TV emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.player.OfficialEmbedPlaybackTest' '-Pandroid.testInstrumentationRunnerArguments.liveBroadcasters=true'
```

The test deliberately waits for the normal pre-roll to complete and is skipped
unless `liveBroadcasters=true` is supplied.
