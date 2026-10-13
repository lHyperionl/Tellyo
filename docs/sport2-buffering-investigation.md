# Sport2 buffering investigation

Investigated on 7 October 2026 from the local development network.

## Findings

Sport2 resolves to a single-rendition HLS media playlist. At inspection it held
ten alternating 4–6 second segments, approximately 50 seconds of video. There
was no adaptive master playlist offering a smaller rendition, so the app's
Stable quality setting cannot reduce this feed's bitrate.

Five consecutive complete segment downloads contained 24.04 seconds of media
but took 36.524 seconds to transfer. Individual throughput varied from 1.56 to
3.64 Mbps; the media itself required approximately 3.49 Mbps. Transport-stream
PTS spans agreed with the manifest durations. This confirms uneven delivery
independently of the Android player; it does not distinguish server load from
congestion along the network path.

The active API 34 emulator session also logged repeated buffering, segment
loads lasting 7–11 seconds, and many dropped frames from its software HEVC
decoder. An earlier 150-second baseline observation had no rebuffers, but its
available buffer declined to approximately 9–12 seconds. Neither short run
establishes reliable delivery over a full programme.

## Changes

- Sport2 requests a 40-second live offset instead of 30 seconds, retaining more
  of the available playlist ahead of playback. Other Slovak IPTV channels keep
  their existing offset. Media3 constrains the requested offset to the actual
  playlist window.
- Native startup reserves 6 seconds instead of 3 seconds, and recovery reserves
  12 seconds instead of 6 seconds. These are minimum readiness thresholds,
  subject to Media3's live-offset constraints. The existing 30–90 second loading
  buffer and preference for duration over byte limits remain in place.
- The existing provider instrumentation check verifies the Sport2 policy and
  the unchanged offsets and request headers for the remaining channels.

Playback can take longer to start or recover, and Sport2 plays farther behind
live. A buffer absorbs temporary slowdowns; it cannot make sustained delivery
below the stream's bitrate reliable. Software HEVC decoding in an emulator is
also distinct from hardware decoding on a physical TV box.

The buffering watchdog now samples buffered media duration every second and
retries after 20 seconds without growth, rather than 20 seconds of total
buffering. This allows slow segments to fill the 12-second recovery reserve.
An absolute 90-second limit still bounds buffering when tiny increments never
produce playable media. The clock is monotonic, and pause, stop, channel changes
and backgrounding cancel the pending watchdog as before.

## Validation

- Debug app and instrumentation APKs built successfully in an isolated build
  directory, avoiding other active builds in this checkout.
- 103 unit tests passed. Lint completed with no errors and nine warnings.
- 12 emulator checks passed: provider resolution and buffering policy, HTTP
  header isolation across channels, and playback recovery/pause/lifecycle tests.
- The first API 36 sustained test stopped before requesting media because its
  network lacked Android's VALIDATED capability. The disposable emulator's
  connectivity configuration was adjusted before repeating the live check.
- The repeat reached native video playback with the requested 40-second offset.
  Initial samples showed 6.115, 7.507 and 11.209 seconds buffered. The emulator
  disconnected before its final report could be collected, so a complete
  three-minute stability pass is not claimed. Software HEVC frame drops were
  still present in this emulator.

Local evidence is under `artifacts/sport2/`, including the original manifest,
full-segment timing probe, baseline playback report and log, and isolated build
reports. The installable APK is `artifacts/sport2/Tellyo-Sport2-buffering.apk`.
No physical TV was connected through ADB during this investigation.

APK SHA-256:
`B20BFE69C61993A249AF7A19962495F2763726B842650F19D18AEC36185C2242`.
