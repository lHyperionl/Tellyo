# Cold app startup improvements

Validated on 7 October 2026 using the API 34 Android TV emulator.

## Changes

- WorkManager uses on-demand initialization. The Activity posts refresh scheduling after its first frame, and the Application enqueues work on an IO coroutine once per process. Unique periodic work retains its existing identity across Activity recreation.
- The native MediaController connection and ExoPlayer service start when native playback is requested. Browsing, embedded players, host lifecycle notifications, and quality preferences no longer start the native playback service. The connection remains shared when a request is cancelled; saved quality settings apply to the first native source.
- Saved-channel preferences and the local channel catalogue load concurrently. Both complete before channel selection and autoplay are restored, preserving the existing behavior with slow preferences, invalid saved channels, and offline launches.
- Channel providers are injected lazily and constructed during IO reads, rather than while the home ViewModel is created on the UI thread. EPG providers are also injected lazily: cached guide observation does not construct network providers, and refresh constructs them on IO.
- `ReportDrawnWhen` reports channel readiness to Android once loading completes. This measures the populated UI, including a displayed loading error; it does not measure decoded video or completion of guide downloads.

## Validation

- Debug application and instrumentation APK builds passed.
- JVM suite: 105 tests, 104 passed, one opt-in live-provider test skipped, zero failures/errors.
- TV instrumentation: all 28 selected tests passed on the final build. Classes: `PlaybackRecoveryTest`, `StartupSchedulingTest`, `HomeScreenFocusTest`, `EmbedLifecycleRegressionTest`, and `ProgramWindowTest`. The final run is recorded in `artifacts/cold-start/verification/final-tv-tests.txt`.
- New regressions cover concurrent startup reads, absence of native-player initialization while browsing, saved quality on the first native source, reuse of the connection, periodic-work identity across Activity recreation, and deferred provider construction on a background thread. Existing tests cover delayed connection, cancellation, manual pause, host stop/resume, offline recovery, autoplay, and remote focus.
- Debug lint passed with zero errors and 38 warnings. `git diff --check` passed.

Local build checks used the previously documented session-only JDK workaround:
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Programming/Tellyo/.gradle`.

## Timing method

The baseline was built in an isolated source directory from commit
`043fd812b158fd49df72c7c372f9d7769baebeb3`. Both APKs include the same existing JOJ
player asset. The intermediate optimized APK was frozen after lazy playback,
deferred background scheduling, and concurrent preference reads, before the final
provider-initialization changes and concurrent native-buffering/IPTV changes.
The timing comparison therefore covers that intermediate startup snapshot.

Both builds are debug APKs, tested on the same API 34/x86 TV emulator with retained
app data and the same saved native channel. Four alternating phases
(baseline, optimized, baseline, optimized) each discard one initial launch and
record five `am start -W -S` process-cold launches. App data and device caches are
not cleared. Each recorded launch is followed by two seconds of foreground time
and a force stop. Earlier exploratory timings collected during compilation are
excluded.

`am start` records the first Activity window. It does not measure guide readiness
or the first decoded live video frame. Only the optimized build reports channel
readiness, so its fully-drawn logs cannot provide a before/after comparison.
Emulator timings are subject to host contention and do not establish physical-TV
performance.

Raw timing samples, APKs, comparison script, logs, and test/lint XML are retained
under `artifacts/cold-start/`.

| First-window measurement | Baseline | Intermediate optimized build |
| --- | ---: | ---: |
| Median over 10 launches | 1,891 ms | 1,329.5 ms |
| Range | 1,189–3,553 ms | 1,167–3,401 ms |
| Median in the second phase for each build | 1,255 ms | 1,309 ms |

The phase variation is larger than the final-phase difference. The lower overall
median is insufficient evidence for a reliable percentage speedup. The final
provider-initialization changes are verified by threading and lazy-initialization
regressions; they have no comparable timing series here.
