# App review fixes — 3 October 2026

Implemented fixes for all 14 findings in [the original review](app-code-review-2026-10-03.md). Existing uncommitted work was preserved. The review is a historical record; its line references describe the pre-fix source.

| # | Finding | Implemented behavior | Regression coverage |
|---|---|---|---|
| 1 | Excessive EPG refresh work | Aggregate XMLTV and JOJ feeds refresh at most every six hours. STVR schedule pages are cached separately from five-minute live metadata updates. Home polling stops in the background. | RoomEpgRepositoryTest, StvrEpgProviderTest, JojEpgProviderTest, HomeViewModelTest |
| 2 | Background playback recovery | Host state guards every prepare/play continuation. Stop cancels native retry, watchdog, deferred resume and Home resolution/recovery jobs. Start deliberately resumes pending playback. The shared controller connection survives cancellation of individual callers. | HomeViewModelTest, PlaybackRecoveryTest |
| 3 | Favorites focus and offscreen navigation | The first row receives focus after lazy layout. The second row's Up target points to that attached requester; other rows use Compose's lazy-list traversal and scrolling. | ReviewFocusRegressionTest |
| 4 | Unattached EPG entry focus | Home and Guide choose a rendered programme after window clipping, across channels. Empty guide content has a toolbar/EPG button target. Room queries use half-open time windows. | ReviewFocusRegressionTest, ProgramWindowTest |
| 5 | Persistent Stable bitrate cap | Every policy resets the bitrate limit before Stable adds its own constraints. Auto and High restore unrestricted size/bitrate. | PlaybackRecoveryTest |
| 6 | Home guide expires after a day | Home's observed programme window follows the current hour. Clock ticks within the same hour do not restart the query. | HomeViewModelTest |
| 7 | Partial EPG destroys cached future programmes | Providers report replacement coverage. Room replaces only covered channel windows; live metadata corrects overlapping current records while preserving future records. Failed JOJ future dates declare no replacement coverage. Expired records are pruned transactionally. | RoomEpgRepositoryTest, ProgramWindowTest, StvrEpgProviderTest, JojEpgProviderTest |
| 8 | Embedded playback lacks lifecycle control | WebView observes Start/Stop, pauses/resumes its lifecycle and timers, and pauses HTML media in accessible frames. Resume restores media that was playing while preserving manually paused media. Accepted login redirects survive recomposition. | EmbedLifecycleRegressionTest |
| 9 | Response size checks happen after allocation | Configuration, XMLTV and JOJ responses use bounded streaming reads. Gzip expansion has its own bound. Unknown-length bodies stop after the first byte over the limit. | LimitedResponseTest |
| 10 | Diagnostics clips channel results | Channel results use a weighted, keyed LazyColumn with focusable rows for remote navigation and scrolling. | ReviewFocusRegressionTest |
| 11 | Reentrant diagnostics actions | Job guards reject overlapping channel scans and EPG refreshes; busy buttons suppress activation. | DiagnosticsViewModelTest |
| 12 | Disabled IPTV channels reappear | Provider fallback preserves catalog enabled flags; it adds dynamic provider channels only when the catalog contains no entries for that provider. | ChannelRepositoryTest |
| 13 | Stale clocks and now markers | One foreground AppClock ticks every 30 seconds and drives Home time, programme progress, Guide highlighting and its current-time marker. Ticking stops while the Activity is stopped. | HomeViewModelTest, GuideViewModelTest |
| 14 | Idle telemetry repopulates cleared state | Sampling stops on host stop, pause, idle, end and close, and restarts for active playback. Closing clears media items. Empty/mismatched telemetry sessions ignore samples and late analytics callbacks. | PlaybackTelemetryStoreTest, PlaybackRecoveryTest |

## Verification

- Unit tests: 70 passing, zero failures/errors.
- Debug application and instrumentation APKs build successfully.
- Android lint: zero errors, 37 warnings, matching the warning count before the fixes.
- Android TV API 36 emulator: all 24 selected instrumentation tests pass, including existing Home navigation and Guide scrolling tests, new 60-channel Favorites and 64-channel diagnostics checks, playback lifecycle/recovery, quality transitions, Room migration/update behavior, and real HTML media pause/resume.
- The final combined unit-test, instrumentation-test and lint Gradle invocation completed successfully. Application and test APK packaging also succeeded during this run.
- The API 34 emulator crashed during its first UI run, after passing playback/database tests; the complete suite was subsequently run successfully on API 36.

Device testing used a temporary, headless, read-only emulator session, with software graphics and crash reporting disabled to work around the installed emulator's startup crash-handler failure. Existing AVD data and app work were preserved.

The device tests use local programme/catalog fixtures and offline Media3 players. The embedded-media test uses a local WAV data URI. They do not establish live broadcaster availability, account access, or behavior inside cross-origin frames inaccessible to page JavaScript; WebView lifecycle/timer controls still apply to the view as a whole.
