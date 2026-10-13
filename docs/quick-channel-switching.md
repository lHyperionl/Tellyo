# Quick channel switching

The fullscreen player has a full-height right-hand channel rail, flush with the
top, right and bottom screen edges. Its left edge fades from transparent into
navy over the playing stream. The channel content retains safe inset spacing
and the outer panel has square corners. It preserves the
existing navy surfaces, blue accent, broadcaster logos and native Compose
typography. Only the selected row receives the bright outline. The stream stays
full size and keeps playing behind the rail.

- During native playback, Up/Down opens the rail at the previous/next enabled
  channel. Channel+/− opens it during both native and embedded playback. Within
  the rail, Up/Down and Channel+/− move through the catalogue, wrapping at the
  ends. Held arrows repeat naturally.
- Broadcaster pages retain Up/Down while the rail is closed, so consent, login
  and player menus remain navigable. Remotes without Channel+/− can select
  another embedded channel from the dashboard after pressing Back.
- Rows show the channel number, logo, current programme and broadcast times.
  Missing guide metadata has an explicit fallback. The playing channel has a
  separate “Práve sledujete” label.
- OK commits the highlighted channel through the existing PlayChannel action.
  Confirming the already playing channel closes the rail without reloading it.
- Back closes the rail first. A subsequent Back exits playback as before.
- The rail dismisses after 10 seconds without input. Remote presses (including
  repeats and keys that leave the selection unchanged) and pointer interaction
  reset the timer. Timeout leaves the playing channel and player focus intact.
- Left/Right stays within the rail; volume and other system keys retain their
  existing handling. Closing the rail preserves player focus.

Browsing uses local state and does not resolve streams, change the selected
channel, reload broadcaster pages or recreate the media view. The Activity key
boundary covers both native PlayerView and official WebView playback. Compose
also supports key interception when the component is hosted independently.
The rail uses immediate selection and scrolling, including reduced motion.

Design variance 5, motion intensity 3, visual density 5. The supplied frontend
skill is applied to hierarchy and brand preservation; web marketing layout,
dual-theme and image-generation requirements do not apply to this native TV UI.

Instrumentation coverage includes browse versus commit, enabled-channel wrapping,
same-channel confirmation, empty/removing channel lists, retained player focus
and composition, and real Activity Back handling for native and web routes.

## Verification

For the initial implementation, debug app and instrumentation APK assembly passed. All 130 JVM unit tests and
9 targeted TV instrumentation tests passed on the API 36 emulator. Lint passed
with 0 errors and 38 warnings. The rail's layout was reviewed from the captured
fixture screenshot at `artifacts/quick-channel-switching/rail.png`.

Another active Gradle run locked the normal output directory, so verification
used an isolated build under `.gradle/quick-channel-build/`. The instrumentation
APKs were installed from that directory and the three player test classes were
run through `adb shell am instrument`. The complete device test output is saved
at `artifacts/quick-channel-switching/instrumentation.txt`.

The emulator reported no internet connection during verification. Stream
continuity is covered by retained composition/focus and the official WebView
fixture; actual live broadcaster playback was not verified in this run.

The full-height gradient follow-up passed debug app and instrumentation APK
assembly and all 6 QuickChannelSwitcher instrumentation tests. The refreshed
fixture screenshot confirms the panel touches the top, right and bottom edges
and fades into the stream on the left. Device output is saved at
`artifacts/quick-channel-switching/edge-to-edge-instrumentation.txt`.

The heading/subtitle removal and idle-dismiss follow-up passed debug assembly,
lint (0 errors, 38 warnings) and all 10 player instrumentation tests. A controlled
Compose clock regression verifies that input without a selection change resets
the 10-second deadline, expiry preserves playback/focus, and arrows reopen the
rail. The fixture screenshot was refreshed; device output is saved at
`artifacts/quick-channel-switching/idle-timeout-instrumentation.txt`.
