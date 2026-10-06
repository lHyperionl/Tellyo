# App code review — 3 October 2026

The findings below describe the pre-fix working tree. Fixes were subsequently implemented at the user's request; see [implementation and verification](app-code-review-fixes-2026-10-03.md) for current status.

Reviewed the current working tree, including its existing uncommitted and untracked app code. Scope covered startup and lifecycle, Media3 and broadcaster WebView playback, providers and configuration, Room/EPG, Compose focus/navigation, diagnostics, settings, and build configuration. This is a correctness and performance review; no application source was changed.

Found **14 actionable issues: 2 P1, 11 P2, and 1 P3**. P1 means address promptly; P2 means a normal-priority functional or performance defect; P3 means a lower-impact defect. Findings below distinguish executed evidence from source inspection. Device-dependent behavior still needs Android verification.

## Validation

- The existing `:app:testDebugUnitTest` suite passed: **56 tests, 0 failures, 0 errors, 0 skipped**.
- `:app:lintDebug` passed: **0 errors, 37 warnings**. Of these, 31 concern dependency versions. The remaining warnings concern layout inflation, vector size, a modifier parameter, an exported service, overdraw, and an unused resource. These warnings are not the basis of the findings below.
- An isolated regression test against the real `DefaultChannelRepository` and `SlovakIptvProvider` reproduced finding 12: a catalogue-disabled IPTV channel returns as enabled through provider fallback. Its expected-empty assertion failed.
- Inspected the locally resolved Media3 bytecode: `clearVideoSizeConstraints()` resets width/height only, confirming finding 5.
- Inspected the locally resolved Compose UI implementation, which is **1.10.3**, despite some explicit 1.7.6 declarations. An unattached focus requester logs a warning and returns failure in this implementation. Findings 3–4 are navigation failures, not established crashes.
- `adb devices` returned no connected devices. No instrumentation, broadcaster playback, frame-time measurement, or long-running TV session was executed for this review.
- Gradle initially failed because Java could not establish its selector-pipe connection. Checks ran with installed JDK 23 and the session-only environment value `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Programming/Tellyo/.gradle`. Repository build settings were not edited.

The isolated reproducer lives outside the normal source sets in [.gradle/code-review/test/ReviewRegressionTest.kt](C:/Programming/Tellyo/.gradle/code-review/test/ReviewRegressionTest.kt). Its captured failure is in [disabled-iptv-regression.xml](C:/Programming/Tellyo/.gradle/code-review/disabled-iptv-regression.xml). Run it with:

```powershell
.\gradlew.bat --no-daemon -I .gradle/code-review/review.init.gradle :app:testDebugUnitTest --tests com.tellyo.app.review.ReviewRegressionTest
```

## Findings

### 1. [P1] Full EPG downloads and database replacements can run every minute

**Location:** [HomeViewModel.kt:224](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/home/HomeViewModel.kt:224), [ConfiguredXmlTvEpgProvider.kt:27](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/ConfiguredXmlTvEpgProvider.kt:27), [EpgRepository.kt:153](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/EpgRepository.kt:153).

Home refreshes EPG every 60 seconds for the ViewModel's lifetime. `ConfiguredXmlTvEpgProvider` inherits the zero minimum interval, so each tick downloads and parses the aggregate XMLTV feed and deletes/reinserts all programmes for its channels. Without a configured feed, the same provider calls STVR's fallback, fetching two schedule pages and the live metadata for up to four channels on every tick. Activity lifecycle-aware collection does not stop this ViewModel job when the app is backgrounded.

The existing `distinctUntilChanged()` reduces downstream UI updates but does not prevent downloads, DOM allocation, or database writes. A 1 MB feed downloaded every minute would transfer roughly 1.4 GB over 24 hours of continuous operation, before other traffic. This is an illustrative calculation, not measured traffic.

**Fix:** Give full schedule/aggregate feeds a meaningful refresh interval, cache schedule pages separately from current-show metadata, and suspend foreground polling while the host is stopped. Keep local clock updates separate from network refreshes. Verify with a counting provider across repeated refresh calls and Activity stop/start.

### 2. [P1] Pending recovery jobs can restart playback after the Activity stops

**Location:** [Media3PlaybackClient.kt:194](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/player/Media3PlaybackClient.kt:194), [Media3PlaybackClient.kt:271](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/player/Media3PlaybackClient.kt:271), [HomeViewModel.kt:361](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/home/HomeViewModel.kt:361).

After a player error, `scheduleRetry()` waits and then calls `prepare()`, which unconditionally calls `controller.play()`. If the user presses Home during that delay, the player is idle: `onHostStopped()` neither marks the host as stopped nor cancels the retry. The retry can therefore start audio/video while another app is visible. Stopping during buffering leaves the watchdog active, and Home's separate stream-resolution recovery also runs without a host-state guard.

**Fix:** Track host activity independently of player state. Cancel or suspend recovery while inactive, and check host state before every prepare/play continuation. Resume deliberately on start. Test stopping during retry delay, buffering timeout, initial resolution, and controller connection.

### 3. [P2] Moving up from the second Favorites row targets an unattached requester

**Location:** [FavoritesScreen.kt:90](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/home/FavoritesScreen.kt:90).

The first row attaches `entryFocus`, but the second row's `up` property points to `rowFocusRequesters[0]`, which is never attached. Open Favorites, move down once, and press Up: the explicit target cannot receive focus. The same scheme also assigns explicit targets to lazy rows that may not yet be composed. The resolved Compose implementation warns and fails the request; no crash was reproduced.

**Fix:** Point the second row upward to `entryFocus`. Use lazy-list-aware focus traversal or scroll/compose the target before explicitly requesting it. Add a Down–Up regression and navigation across an offscreen boundary.

### 4. [P2] EPG entry focus is attached to the first stored programme rather than the first visible programme

**Location:** [HomeScreen.kt:900](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/home/HomeScreen.kt:900), [GuideScreen.kt:62](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/guide/GuideScreen.kt:62), [GuideScreen.kt:304](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/guide/GuideScreen.kt:304).

Both grids assign their entry requester only to row zero/programme index zero, inside a visibility condition. Home loads programmes starting two hours before launch, while its preview starts only one hour before the current hour. Its index-zero programme can be offscreen, leaving the channel cards' `down = epgFocus` unattached even when later programme cells are visible. In Guide, Room's inclusive `end >= from` query can return a first programme ending exactly at the window boundary: it has zero visible width and never attaches the requester, but the autofocus effect still requests it. An empty first row similarly leaves sidebar entry without a target.

**Fix:** Select the first rendered cell across rows after clipping, and attach the entry requester there. Provide a toolbar or empty-state target when no cell exists. Test an expired first programme, an exact boundary, and a first channel without EPG.

### 5. [P2] Leaving Stable quality retains its 3 Mbps cap

**Location:** [Media3PlaybackClient.kt:175](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/player/Media3PlaybackClient.kt:175).

Stable sets `maxVideoBitrate` to 3,000,000. Subsequent Auto/High changes rebuild from the current parameters and clear only video-size constraints. The bitrate limit survives, including across future channel preparations, so available higher-bitrate tracks remain excluded. The actual Media3 builder implementation confirms that clearing size does not clear bitrate.

**Fix:** Reset maximum video bitrate when applying every policy, then add Stable's cap. Verify Stable → Auto and Stable → High on a multi-rendition stream or against the resulting parameters.

### 6. [P2] Home's EPG query window never advances

**Location:** [HomeViewModel.kt:232](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/home/HomeViewModel.kt:232).

The observation job captures `from` and `to` once, with `to = launch time + 24 hours`. Continued refreshes update Room but do not move these query arguments. Programmes starting beyond the original upper bound never reach Home. A TV that stays in the app, or keeps the ViewModel while sleeping, eventually loses current/upcoming programme metadata despite a fresh database.

**Fix:** Recreate the observation from a rolling time window, for example with an hourly clock flow and `flatMapLatest`, and renew it when the host resumes. Test a session spanning the original upper bound.

### 7. [P2] A partial STVR fallback erases a previously cached full schedule

**Location:** [StvrEpgProvider.kt:27](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/StvrEpgProvider.kt:27), [EpgRepository.kt:164](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/EpgRepository.kt:164), [TellyoDatabase.kt:72](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/database/TellyoDatabase.kt:72).

If schedule fetching fails or returns no records but live metadata succeeds, STVR returns valid XML containing only current shows. The repository treats this as a complete replacement and deletes all stored programmes for each returned channel. A transient schedule outage therefore removes tomorrow's cached programmes instead of retaining them. A missing future JOJ day can produce the same narrowing when its partial response is applied.

**Fix:** Carry completeness/covered time range with provider results. Merge live-only updates, and replace only a successfully fetched authoritative range. Test full-cache → schedule-failure/live-success and a missing future day.

### 8. [P2] Broadcaster WebViews have no background playback lifecycle handling

**Location:** [OfficialEmbedPlayerScreen.kt:43](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/player/OfficialEmbedPlayerScreen.kt:43), [MainActivity.kt:53](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/MainActivity.kt:53).

The Activity stop callback pauses the Media3 client only. Broadcaster playback is owned by a separate WebView, which is destroyed only when the composable leaves composition. Backgrounding the Activity does not dispose that composition. There is no lifecycle observer or explicit broadcaster/media pause path, so Tellyo does not enforce its background-stop behavior for these channels; actual continuation depends on the page/WebView/device.

**Fix:** Add lifecycle handling for the embedded player and explicitly pause its media on stop, preserving page/session state for resume. Verify Home/app-switch and screen sleep for Markíza, JOJ, and TA3 on a TV. This finding was established by source inspection, not a device playback test.

### 9. [P2] Response size guards run after the complete response is allocated

**Location:** [RemoteConfigEndpoint.kt:52](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/config/RemoteConfigEndpoint.kt:52), [ConfiguredXmlTvEpgProvider.kt:45](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/ConfiguredXmlTvEpgProvider.kt:45), [JojEpgProvider.kt:91](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/epg/JojEpgProvider.kt:91).

These paths call `body.bytes()` before enforcing their byte limits. A response with no declared length, such as chunked delivery, is fully read into memory before rejection. The XMLTV compressed-body path has no pre-read length check at all. Oversized or erroneous feed responses can cause substantial allocation or an out-of-memory failure before the intended fallback executes. Gzip expansion itself is bounded, but downloading the input is not.

**Fix:** Read at most the allowed bytes plus one from the response stream and reject immediately on overflow. Retain the separate decompression bound. Test oversized responses without `Content-Length` and compressed data that expands over the XML limit.

### 10. [P2] Diagnostics cannot scroll its full channel list

**Location:** [DiagnosticsScreen.kt:149](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/diagnostics/DiagnosticsScreen.kt:149).

Diagnostics renders every health row in a plain Column within a fixed screen, with no scroll container. The normal catalogue contains 14 broadcaster channels plus roughly 50 IPTV channels; 64 rows at 44 dp require 2,816 dp before the status panels and buttons. Most results are outside the usable viewport and have no D-pad scroll path. All rows are also composed whenever relevant diagnostics state changes.

**Fix:** Put the results in a weighted LazyColumn and provide TV focus/scroll access to its rows. Verify that the final channel's result is reachable with the normal catalogue.

### 11. [P2] Repeated diagnostics actions start overlapping scans

**Location:** [DiagnosticsViewModel.kt:117](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/diagnostics/DiagnosticsViewModel.kt:117), [DiagnosticsScreen.kt:128](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/diagnostics/DiagnosticsScreen.kt:128).

The channel-test button stays actionable while scanning. Each activation launches another full sequential scan; `isCheckingChannels` changes the label but never guards entry. A TV remote repeat or repeated confirmation starts duplicate HTTP probes, interleaves row results, and lets the first completed scan set `isCheckingChannels=false` while another still runs. The EPG diagnostics action is also reentrant and can queue multiple repository refreshes.

**Fix:** Track the active job or guard admission before launching, and make the busy button ignore activation. Verify two rapid actions produce one scan and keep the busy state accurate.

### 12. [P2] Disabling all configured IPTV channels restores them through fallback

**Location:** [ChannelRepository.kt:24](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/provider/ChannelRepository.kt:24).

Filtering disabled entries before grouping loses the distinction between an absent provider configuration and a provider whose channels were explicitly disabled. When the enabled group is empty, `ifEmpty` calls `provider.getChannels()`. The IPTV provider enumerates its playlist as enabled channels, so disabling the final configured IPTV entry restores the provider's playlist. This is reproducible with a one-channel catalogue and the real IPTV provider; the isolated regression failed its expected-empty assertion.

**Fix:** Determine provider presence from the complete catalogue before filtering enabled entries. Apply dynamic/development fallback only when configuration is genuinely absent, or make that behavior explicit per provider. Add the isolated regression to the permanent suite when fixing it.

### 13. [P2] Guide's current-time marker never advances automatically

**Location:** [GuideViewModel.kt:30](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/guide/GuideViewModel.kt:30), [GuideViewModel.kt:78](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/guide/GuideViewModel.kt:78), [GuideScreen.kt:158](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/feature/guide/GuideScreen.kt:158).

`GuideUiState.now` is initialized once and updated only by ShiftWindow. Programme emissions and manual refresh do not change it, so the blue marker continues to display the old time while the guide is open. Cell highlighting separately reads `Instant.now()` during composition, which can disagree with the marker. Home's clock/progress also depends on unrelated recompositions rather than a clock state.

**Fix:** Use one lifecycle-aware clock state for markers, current-show highlighting, and progress. Update it locally at a suitable interval without fetching EPG. Test time advancing without user actions or changed programme data.

### 14. [P3] Idle telemetry polling can undo the reset performed by Stop

**Location:** [Media3PlaybackClient.kt:240](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/player/Media3PlaybackClient.kt:240), [PlaybackTelemetry.kt:100](C:/Programming/Tellyo/app/src/main/java/com/tellyo/app/core/player/PlaybackTelemetry.kt:100).

The process-wide telemetry job samples every second indefinitely, including after `stop()` resets telemetry to an empty state. Stop does not clear the retained media item, and `sample()` writes `currentMediaItem.mediaId` back into the store. Diagnostics can therefore recover the previous channel identity after the user closed playback. Sampling also continues while idle/backgrounded, and the diagnostics ViewModel keeps collecting once initialized.

**Fix:** Gate sampling on an active playback session, cancel or suspend it on stop, and clear telemetry consistently with the media item. Verify the empty state remains empty beyond the next sampling interval.

## Fix order and remaining verification

Address background recovery and EPG polling first, followed by TV focus, quality-policy reset, and EPG cache/window correctness. Then handle broadcaster lifecycle, bounded network reads, and diagnostics. The suite currently exercises providers, parsers, caching, and basic ViewModel behavior well, but lacks coverage for the actual Media3 client's recovery/quality lifecycle and these focus/long-session edge cases.

No frame-time improvement, production memory limit, or broadcaster runtime behavior is claimed from this review. After fixes, run the focused regressions and existing suite, then exercise the affected D-pad and lifecycle paths on Android TV and profile a release/profileable build. Catalogue updates appearing after a new ViewModel/app restart are already documented in `docs/ui-performance.md`; that deliberate limitation was not counted as a new defect.
