# JOJ and Markíza playback audit

**JOJ implemented after the recheck below, 3 October 2026:** All eight routes
now host the correct individual official player inside Tellyo. Krimi and JOJ 24
passed automatic live video/audio, centered framing, remote pause/resume and
Back disposal checks. JOJ/Plus require login and the four remaining channels
require Premium; authenticated playback has not been verified. See
[JOJ implementation and validation](joj-player-validation.md).

**JOJ recheck, 3 October 2026:** All eight JOJ entries were selected through the
production HomeViewModel and their external-launch controls exercised on the
API 34 emulator. Every entry still resolves to an external HTTPS destination
handled by Android TV's BrowserStub; none establishes playback inside Tellyo or
through that placeholder. All destination pages returned HTTP 200, which does
not establish a working stream. Šport 2 still redirects to Šport, and JOJko/Svet
still open the generic JOJ Play portal. The audit completed successfully in 38s;
this confirms collection of those failures, not successful channel playback.
Evidence: `artifacts/broadcasters/joj-recheck-report.json` and
`joj-recheck-logcat.txt`. The unchanged application APK was restored afterward.

**Implemented after this audit:** Dajto, Markíza Krimi, Markíza Klasik and TA3
now play inside Tellyo and passed live video/audio, TV framing and Back checks.
TA3's first-use consent, play/pause and reopening were also tested with the TV
remote. See [four-channel verification](four-channel-player-validation.md).
The earlier external-route results below describe the previous implementation.

**Four-channel recheck, 2 October 2026:** Dajto, Markíza Krimi, Markíza Klasik
and TA3 were selected through the actual HomeViewModel and their external-launch
buttons were exercised on the Android TV emulator. All four resolved to external
HTTPS destinations and Android's `BrowserStub`, so none established video
playback. All four pages returned HTTP 200; Klasik still redirected to the main
Markíza live page. These are not embedded playback routes in the current build.
The audit collected all four results successfully; its passing result means
the audit completed, not that the channels played. Evidence:
`artifacts/broadcasters/four-channel-report.json` and
`artifacts/broadcasters/four-channel-logcat.txt`.

**Later verification:** Markíza's live programme was confirmed, and Markíza/Doma
now start automatically using the official embed's autoplay option. The new
instrumented test also passed Back checks during ads and live playback. See
[autoplay and Back verification](markiza-autoplay-validation.md) for the current
result. The earlier observations below are retained as the original audit.

Tested on 2 October 2026 using Tellyo on the Android TV API 34 emulator with Android System WebView 153.0.8010.36. This audit exercises the application HomeViewModel, embedded WebView and external-launch button. It does not add or change production playback routes.

All 13 configured official destinations responded HTTP 200 when requested by the Android HTTP client. Page reachability is separate from live video playback.

## External routes

The Android TV image resolves HTTPS links to com.android.tv.frameworkpackagestubs/.Stubs$BrowserStub. This is a placeholder, not a usable browser. The external button was exercised, but the eleven external routes cannot establish video playback on this emulator. A physical TV with a compatible browser or broadcaster application requires a separate check.

| Channel | Route | HTTP | Destination |
| --- | --- | --- | --- |
| Markíza | official_embed | 200 | https://media.cms.markiza.sk/embed/CQ8HW3n7gyI |
| Doma | official_embed | 200 | https://media.cms.markiza.sk/embed/3nUCb6yPRpm |
| Dajto | external | 200 | https://www.markiza.sk/live/2-dajto |
| Markíza Krimi | external | 200 | https://www.markiza.sk/live/22-krimi |
| Markíza Klasik | external | 200 | https://www.markiza.sk/live/1-markiza |
| JOJ | external | 200 | https://www.joj.sk/live/1-joj |
| JOJ Plus | external | 200 | https://www.joj.sk/live/1-jojplus |
| JOJ Krimi (WAU) | external | 200 | https://www.joj.sk/live/3-wau. |
| JOJ 24 | external | 200 | https://joj24.noviny.sk/ |
| JOJ Šport | external | 200 | https://jojsport.joj.sk/live/15-jojsport |
| JOJ Šport 2 | external | 200 | https://jojsport.joj.sk/live/15-jojsport |
| JOJko | external | 200 | https://play.joj.sk/ |
| JOJ Svet | external | 200 | https://play.joj.sk/ |

## Routing mismatches

- Markíza Klasik points to /live, which redirects to /live/1-markiza. It does not select Klasik.
- JOJ Šport 2 points to /live, which redirects to /live/15-jojsport, the same destination as JOJ Šport. A separate Šport 2 stream was not selected.
- JOJko and JOJ Svet both point to the generic JOJ Play portal, without selecting an individual channel.

## Embedded-player checks

Markíza and Doma loaded the official players, with player titles CRA Markiza SD and CRA Doma SD. A remote centre press and a down/centre sequence did not start video. A normal touch tap on the visible play button started pre-roll advertising and produced decoded frames. Advertisement frames are excluded from the live-programme success condition. The extended live-programme check permits up to 180 seconds after the tap and preserves the ads and broadcaster controls.

Final extended-check results:

| Channel | Live programme | TV remote start | Evidence |
| --- | --- | --- | --- |
| Markíza | Not verified | Did not start | Touch started the first ad, which subsequently paused at 11.727 seconds. The player still showed ad 1 of 6 after the full 180-second post-tap observation. No live-programme video was established. |
| Doma | Verified after touch | Did not start | After pre-roll, the player showed LIVE, a decoded 1024×576 video, advancing time and a visible Doma channel logo. |

The Doma result demonstrates that its embedded source can deliver the actual programme on this emulator. It does not establish a working TV-remote start flow. Markíza's result is inconclusive about broadcaster-wide availability; it failed to reach live content in this app/network session.

The combined per-channel JSON report is saved at `artifacts/broadcasters/playback-report.json`.
Screenshots are saved at `artifacts/broadcasters/screenshots/TellyoAudit/markiza-audit.png` and
`artifacts/broadcasters/screenshots/TellyoAudit/markiza-doma-audit.png`. The Doma screenshot confirms
live channel content rather than a pre-roll ad. No login, subscription or access-control bypass was used.

## Reproduce

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.core.provider.BroadcasterPlaybackAuditTest' '-Pandroid.testInstrumentationRunnerArguments.liveBroadcasters=true'
```

Per-channel evidence is logged with the TellyoBroadcasterTest tag and retained in Gradle per-test Logcat files. Test completion confirms collection of the audit, not that every channel played. The audit is skipped in normal offline test runs. Lint passed after adding the audit.
