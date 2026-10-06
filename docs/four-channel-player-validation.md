# Dajto, Krimi, Klasik and TA3 in-app playback

The four catalogue entries previously opened external websites. Dajto, Markíza
Krimi and Markíza Klasik now use the embed codes published in their official live
pages and player `copyEmbed` metadata. TA3 loads its official live page inside
Tellyo and fits the existing Livebox player container to the TV viewport. Its
scripts, consent flow, current source lookup, advertising and controls remain
owned by TA3. No signed manifest is stored in app code.

Published sources checked on 2 October 2026:

| Channel | Official page | Player |
| --- | --- | --- |
| Dajto | https://www.markiza.sk/live/2-dajto | `https://media.cms.markiza.sk/embed/DXOmzXWnYIU?autoplay=1` |
| Markíza Krimi | https://www.markiza.sk/live/22-krimi | `https://media.cms.markiza.sk/embed/I9DRrSWXiao?autoplay=1` |
| Markíza Klasik | https://www.markiza.sk/live/44-klasik | `https://media.cms.markiza.sk/embed/AWoXanGioN3?autoplay=1` |
| TA3 | https://www.ta3.com/live | The page's `LiveboxPlayer.live` player, fitted to the screen |

The existing Markíza autoplay, audible-start and fit settings also apply to the
three added embeds. Klasik's official destination mapping now selects Klasik
rather than redirecting to the main Markíza channel.

TA3 shows its own consent notice on first use. TV arrows move between its
necessary-cookies, settings and accept buttons, with visible focus; OK confirms
the viewer's selected button. Tellyo does not select a consent policy itself.
The notice remains above the player. Its player receives fitting/remote setup
when the page first becomes visible, without waiting for all site resources.
After consent, OK controls play/pause through the broadcaster's player controls
or its Kaltura play/pause API. Back disposes the WebView and stops playback.

The opt-in `OfficialEmbedPlaybackTest` accepts `embedIds` to test selected
channels through the production HomeViewModel and Compose WebView. It requires
advancing decoded frames and audio bytes, an unmuted volume, live programme
state outside a pre-roll break, centered contain fitting, no page zoom/scroll,
the player visible above other page elements, and Back returning home with the
WebView disposed. TA3 is additionally checked for consent choices using only
TV key events, pause/resume, and reopening without another consent/play press.
TA3's live/ad state is read
through its Kaltura player's `isLive()` and `ads.isAdBreak()` APIs, rather than
treating a playing advertisement as success.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.player.OfficialEmbedPlaybackTest' '-Pandroid.testInstrumentationRunnerArguments.liveBroadcasters=true' '-Pandroid.testInstrumentationRunnerArguments.embedIds=ta3,markiza-dajto,markiza-krimi,markiza-klasik'
```

Final API 34 emulator run passed for all four channels. The three Markíza
channels reached live playback after their complete pre-roll without a play
press, each decoding 1024-pixel-wide programme video with advancing frames and
audio bytes, unmuted volume 1, and a visible centered video element. TA3 decoded
856×480 live video, passed all consent-button navigation/confirmation checks,
paused and resumed with OK, and reopened into live playback with its choice
remembered. Back passed after each route and TA3 reopening. Build, unit tests
and lint passed for the final source.

The first TA3 visual assertion exposed its consent notice covering the video;
the final run verifies that the player is visible after the normal remote
consent choice. Earlier backend-only playback observations are superseded.

Evidence: `artifacts/broadcasters/final-added-channel-report.json` and
`artifacts/broadcasters/final-added-channel-logcat.txt`. Live screenshots for
TA3, Dajto and Krimi were pulled while the test ran; Gradle removes the target
app after instrumentation, so the final Klasik observation is retained in the
report/Logcat rather than a pulled screenshot. The verified APK was installed
again for interactive use.

On 3 October the follow-up run passed all four routes again with the emulator's
hardware video decoder disabled (7m 2s). Live screenshots showed correctly fitted
video; TA3's consent navigation, pause/resume and reopening checks passed again,
as did Back for every channel. This resolves the Krimi top-left padding exposed
during visual review; see `embedded-player-framing.md` for the independent decoder
comparison and `scripts/start-tv-emulator.ps1` for the local launch settings.
Follow-up evidence is in `artifacts/broadcasters/software-decoder-channel-report.json`,
`software-decoder-logcat.txt` and the `*-software-live.png` screenshots. The APK
was installed again after the successful run.
