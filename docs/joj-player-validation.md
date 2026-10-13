# JOJ player integration

Current catalogue: JOJ, Plus, Krimi and JOJ 24. At the user's request,
the four Premium channels (Šport, Šport 2, JOJko and Svet) were removed on
3 October 2026, including their playback and EPG mappings. The eight-channel
results below record validation before that removal; the account-gate test
now covers only JOJ and Plus.

Validated on 3 October 2026 on the Android TV API 34 emulator, using the
software video decoder configuration in `scripts/start-tv-emulator.ps1`.

The eight existing catalogue entries now resolve to `OfficialEmbed`, rather
than launching Android TV's placeholder browser. Each uses its own canonical
channel destination; Šport 2, JOJko and Svet no longer open a different channel
or the generic portal. JOJ 24 uses the embed published by its official homepage.
Authorized configured HLS/DASH sources still take priority.

| Channel | Official live route | Playback result without an account |
| --- | --- | --- |
| JOJ | `/live/1-joj` | Login required |
| Plus | `/live/2-plus` | Login required |
| Krimi (formerly WAU) | `/live/3-jojkrimi` | Live video and audio passed |
| JOJ 24 | Official `media.joj.sk` embed | Live video and audio passed |
| Šport | `/live/15-jojsport` | Login and Premium required |
| Šport 2 | `/live/180-jojsport2` | Login and Premium required |
| JOJko | `/live/5-jojko` | Login and Premium required |
| Svet | `/live/114-jojsvet` | Login and Premium required |

The page adapter fits the broadcaster's existing container to the viewport
and applies centered `object-fit:contain` to video, including advertisements.
It sends the broadcaster's normal Play action after asynchronous setup, with
bounded startup retries that stop after media loads or viewer interaction.
Tivio's click handler is on the icon inside its titled control. Clicking only
the surrounding container does not start playback. Radiant's volume API
clears unattended autoplay mute once; subsequent viewer mute choices survive.

Source/session lookup, advertisements, cookie consent, login and subscription
checks remain in the official players. No temporary manifest URLs or account
credentials are added to application code. Cookie choices and account actions
can be selected using the TV remote. Back destroys the WebView so playback
stops and returns to Tellyo Home.

The live instrumentation checks require advancing video frames, advancing
decoded audio bytes, unmuted volume, the live content rather than a pre-roll,
centered geometry, no scroll/zoom, remote pause/resume and WebView disposal.
Krimi and JOJ 24 passed. Their reports are retained in the ignored local files
`artifacts/broadcasters/joj-krimi-validation.json` and
`artifacts/broadcasters/joj-24-validation.json`.
The final run against the installed build passed both free channels in
35 seconds; combined evidence is `joj-final-free-validation.json` with
`joj-krimi-final-live.png` and `joj-24-final-live.png` in the same directory.

All six account-required channels also passed the account-gate instrumentation
run (58 seconds): each rendered the correct channel's gate, opened the actual
JOJ login form with the remote, reached the email field and displayed the TV
keyboard. The first Back dismissed the keyboard, the second returned Home,
and the WebView was disposed while Tellyo remained running. Evidence:
`artifacts/broadcasters/joj-account-gates-validation.json`.

The broadcaster's ad server connection failed on this network during Krimi's
diagnostic run; its own SDK subsequently started live content. Advertisement
delivery therefore needs a separate network check. Tellyo does not intercept
ad requests or skip the broadcaster's ad flow.

Repeat after building/installing the app and Android test APKs:

```powershell
adb shell am instrument -w -e class com.tellyo.app.feature.player.OfficialEmbedPlaybackTest -e liveBroadcasters true -e embedIds joj-krimi,joj-24 com.tellyo.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.tellyo.app.feature.player.OfficialEmbedPlaybackTest -e jojAccountGates true com.tellyo.app.test/androidx.test.runner.AndroidJUnitRunner
```

Successful route or account-screen checks do not prove authenticated playback.
JOJ/Plus and the four Premium channels need a viewer account before their
live video/audio can be verified. Build, unit tests and Android lint are also
part of validation.

## Startup retry improvement (7 October 2026)

The JOJ/Plus/Krimi page adapter now retries the official Play control every
second, with a 250 ms readiness check, instead of waiting five seconds between
attempts. It also reacts to changes in the control's title and disabled state.
Retries end after 30 seconds from the first attempt, when media accepts a play
request (including buffering or pre-roll), at an account gate, or after viewer
interaction. Hidden/disabled controls, consent dialogs and background pages
prevent automatic Play requests. JOJ 24 retains its official embed autoplay.

Run the deterministic adapter regressions with:

```powershell
node --test scripts/test-joj-startup.mjs
```

All 11 checks passed. A fixture whose player setup finishes at 2.1 seconds
starts at 3 seconds; the former retry schedule would wait until 5 seconds.
This measures adapter scheduling, not live network or broadcaster startup.
No Android device was connected for live timing validation. Android build,
unit-test and lint attempts encountered a local Gradle/JDK loopback connection
failure before any tasks ran. The earlier live-player results above predate
this change; repeat them on a connected device before claiming a measured
end-to-end improvement. Broadcaster page loading, source setup, account access,
advertising and media buffering still contribute to total startup time.
