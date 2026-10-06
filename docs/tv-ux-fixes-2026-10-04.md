# TV feedback fixes — 4 October 2026

Back from either player now opens Home with the watched channel focused in the
channel row. Home scrolls an off-screen card into view before requesting focus,
then consumes the restoration request so clock ticks do not move focus again.
Opening the programme guide from the player still enters the guide.

The “Viac informácií” button is removed. The programme synopsis uses up to four
lines, or two with enlarged text. OK on the preview or channel card opens playback;
Down from the preview goes directly to the channel row.

Native IPTV streams request a 30-second live offset, clamped by Media3 to the
available live window. The load control prioritizes the 30-second minimum buffer
duration over its byte threshold. AUTO and STABLE prefer AVC when the stream
offers codec alternatives; HIGH retains its bitrate preference. Decoder selection
prefers hardware and permits fallback if initialization fails.

Brief rebuffering retains the video view and last frame. A small loading notice
appears after 1.5 seconds of continued buffering. Startup, errors and retries
remain visible. Debug playback logs include the selected video decoder and media
segment loading times without stream URLs or headers.

## JOJ account requirement

The official pages were checked again with a normal browser user agent:

| Channel | `tivioDisableTvChannelsForAnonymousUsers` |
| --- | --- |
| JOJ (`/live/1-joj`) | `true` |
| Plus (`/live/2-plus`) | `true` |
| Krimi (`/live/3-jojkrimi`) | `false` |

JOJ and Plus were already documented as account-gated in `joj-player-validation.md`.
No public media embed was present in either live page, and the imported language
playlist does not include those channels. Their account-free playback remains
unresolved without an available authorized public source. WebView session cookies
are now flushed to disk on exit to retain completed sign-ins where the broadcaster
permits it. The existing public Krimi and JOJ 24 routes are retained.

## Playback investigation and limits

Arena Sport 1 reproduced repeated stalls on the API 34 emulator: the initial
90-second run had three rebuffers and about 51 seconds of measured progression.
Segment load events sometimes took 9–14 seconds for 4–6 seconds of content.
Changing the HTTP transport to OkHttp did not resolve it, so that experiment was
removed from the final code.

The selected decoder was `c2.android.hevc.decoder`. Disabling video for a diagnostic
45-second run allowed the same feed to retain approximately 30 seconds of buffer,
with fast segment loads and no repeated buffering. AVC feeds Turbo TV and EbS
passed separate 60-second video runs with zero rebuffers. This points to HEVC
software decoding on this emulator as a contributor, rather than establishing
that every upstream stream has a network delivery fault.

A single-rendition HEVC feed cannot be converted to AVC by quality caps or codec
preferences. Sustained HEVC video playback remains unverified on a physical TV
with hardware decoding; no claim is made that all imported sources are fixed.

The final APK was tested for 90 seconds per channel with video enabled:

| Channel | Selected decoder | Playback progression | Rebuffers |
| --- | --- | --- | --- |
| Turbo TV | `c2.android.avc.decoder` | 83.0 seconds | 0 |
| EbS | `c2.android.avc.decoder` | 81.6 seconds | 0 |
| Prima SK | `c2.android.hevc.decoder` | 44.9 seconds | 4 |

The Prima SK stability assertion failed. Its actual selected codec was HEVC
despite the URL containing `avc`; URL names are not reliable codec evidence.
The final report and decoder/segment logs are saved in
`artifacts/iptv/tv-ux-final-stability.json` and
`artifacts/iptv/tv-ux-final-logcat.txt`.

The user tested on **MI BOX4**. Xiaomi's
[Mi Box 4K specifications](https://www.mi.com/in/mi-box-4k/specs/) list HEVC
decoding up to 4K at 60 fps, but the reported device name alone does not establish
the exact model, firmware, or decoder selected by this app. The emulator has
hardware decoding disabled by its launch configuration, so its remaining HEVC
failure cannot establish the cause on the user's box. Physical-device playback
and the `video_decoder_initialized` diagnostic need to be checked on that box.

## Verification

- 70 unit tests passed; debug build and Android lint passed.
- 20 Home focus/layout and playback recovery tests passed, including large-text
  synopsis bounds and restoration of the last card in a long channel row.
- An additional real-Activity remote Back test passed for a broadcaster player
  and a native IPTV channel.
- The stream-header isolation test passed, for 22 distinct focus, recovery,
  real-Activity Back and session tests in total.
- The final combined instrumentation run passed six tests and failed the live
  stability test on Prima SK. The two AVC channels passed its sustained checks.
- The opt-in sustained test uses the production Activity, video surface and
  MediaSession. It records buffer samples, progression and rebuffers in
  `files/iptv-stability-report.json` in the app's private storage. Diagnostic
  audio-only runs are explicitly marked with `videoEnabled=false`.

Example sustained run after installing both debug APKs:

```powershell
adb shell am instrument -w -e class com.tellyo.app.core.provider.SlovakIptvStabilityTest -e liveIptvStability true -e stabilitySeconds 90 -e stabilityIds iptv-TurboTV.sk,iptv-EbS.be,iptv-PrimaSK.sk com.tellyo.app.test/androidx.test.runner.AndroidJUnitRunner
```

The local JDK initially failed to create Unix-domain loopback pipes on Windows.
For validation, `JAVA_TOOL_OPTIONS` pointed `jdk.net.unixdomain.tmpdir` at a
nonexistent directory under `.gradle`, causing the JDK to use its TCP fallback.
This was scoped to the build process and did not change project or system settings.
A clean rebuild resolved stale generated Hilt classes in the incremental build.
