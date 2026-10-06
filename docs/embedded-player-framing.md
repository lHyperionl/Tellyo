# Embedded player framing

Investigated on the Android TV API 34 emulator on 2 October 2026.

The sampled Markíza live frame matched WebView's decoded frame when compared with an
Android screenshot and the visible emulator. Its CSS viewport was 960×540, page
scale 1, scroll offsets zero, and video bounds filled the viewport. The earlier
reported offset was not reproduced in that sample; this check does not establish
the framing of the earlier programme or frame supplied by the user.

The embed now explicitly honours its viewport, loads at overview scale and
disables WebView zoom. Video fitting is fixed to `contain` with centered object
position, scoped to videos inside the official player's `#ef-player` element.
Other aspect ratios can show black bars to preserve the full picture. The change
does not resize the broadcaster's ad wrappers or alter its controls.

The live-player regression checks video centering, fit mode, page scale and scroll
offsets alongside autoplay, decoded audio and Back. The Markíza and Doma startup
checks passed with video center (480,270), viewport 960×540, scale 1 and no scroll.
The full Markíza pre-roll-to-live check passed with the same layout. Audible
autoplay and Back during ads/live passed as well. Build, unit tests and lint passed.
The updated APK was restored to the visible emulator after instrumentation.

Final evidence: `artifacts/broadcasters/framing-report.json` and
`artifacts/broadcasters/framing-logcat.txt`.

Original comparison evidence: `artifacts/broadcasters/layout-original-decoded.png`,
`artifacts/broadcasters/layout-original-android.png` and
`artifacts/broadcasters/layout-before.json`.

After the repeated report, a paused frame from a broadcast commercial was
captured simultaneously through the video's canvas (1026×576), Android
screencap (1920×1080), and the visible emulator window. All three preserved the
same picture edges. A pixel comparison excluding the pause control measured
mean absolute RGB error 0.401/255 for centered contain fitting, versus 24.260
for 2% zoom anchored at the top left and 39.121 for 5% zoom at the top left.
This verifies the sampled displayed picture against the actual decoded frame;
it does not recreate the earlier Lidl frame supplied by the user. No additional
production change was made based on this comparison. Playback was resumed.

Evidence: `artifacts/broadcasters/framing-side-by-side.png`,
`artifacts/broadcasters/framing-pixel-comparison.json`, and the reproducible
`artifacts/broadcasters/compare-framing.py` comparison script.

## Emulator decoder follow-up, 3 October 2026

Krimi reproduced a separate fault: its 1024×576 decoded frame contained a
640×360 picture at the top left, with black pixels to its right and below.
Android faithfully displayed that already incorrect decoded frame. Consequently,
matching a WebView canvas to a screenshot alone cannot rule out a decoder fault.
DOM bounds, centered contain fitting, scale and scroll were still correct.

An independent FFmpeg/PyAV decoder read the official stream's same 1024×576
variant and produced a full picture, including the channel logo and right-side
age badge. Cold-booting the same AVD with the emulator feature `HardwareDecoder`
disabled removed the padding after the complete pre-roll. The application APK
and official player were unchanged between those two checks.

Use `scripts/start-tv-emulator.ps1` to start this local emulator with
`-feature -HardwareDecoder` and `-no-snapshot-load`. This changes the emulator's
video-decoding path while retaining host GPU rendering. Physical TV decoder
settings are unchanged; no artificial crop or zoom is applied to the broadcast.

Evidence: `artifacts/broadcasters/krimi-hardware-decoded-frame.png`,
`krimi-hardware-android-frame.png`, `krimi-independent-frame.png` and
`markiza-krimi-software-live.png` in the same folder. The follow-up four-channel
playback report is `software-decoder-channel-report.json`.

A final check of the reinstalled app waited through all six Krimi pre-rolls and
another 30 seconds of live playback. At 1024×576 the decoded frame filled the
picture correctly, and Android displayed it across 1920×1080 with centered
contain fitting, no zoom/scroll and unmuted volume 1. This verifies the resolution
that reproduced the earlier padding, as well as startup at lower adaptive quality.
Evidence: `krimi-software-decoded-frame.png`, `krimi-software-android-frame.png`,
`krimi-software-layout.json` and `krimi-software-audio.json` in the artifacts folder.
Playback was resumed and Krimi was left open in the visible emulator.
