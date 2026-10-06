# UI performance pass

The startup channel path now uses the saved catalogue and playlist, or bundled
assets on a first launch. Playlist requests run in the background with one
refresh in flight and a six-hour retry interval. Remote configuration remains
owned by the existing startup/periodic WorkManager job. Downloads happen outside
metadata read locks; disk reads and decoding run on an IO dispatcher.

Home and Guide each observe one complete programme window rather than creating
one Room query and UI-state update per channel. The programme table now has a
`(channelId, startEpochMillis)` index, with a non-destructive version 2 to 3
migration. Identical database emissions are filtered before domain conversion.
XML parsing and refresh processing run off the UI thread.

Guide displays cached programmes immediately and shares Home's periodic refresh
instead of starting a second refresh loop. Its explicit refresh button remains
available. Guide and Diagnostics initialize on first navigation; their UI flows
are collected only while their screen is displayed. Activity collections are
lifecycle-aware. Channel order/focus requester allocation is cached, and focus
background animation reads its color in the drawing phase rather than composition.
Guide rows are now virtualized with stable channel keys, so entering a large
guide composes visible rows instead of rendering the entire channel catalogue.

Local-first loading means a newly downloaded catalogue appears on a subsequent
channel-list load (currently a new HomeViewModel/app restart). Existing cached
provider routes remain immediately usable; new authorized configuration becomes
available after the background job completes. Playlist URL lookups use the latest
successfully refreshed snapshot. Failed refreshes retain the local snapshot.

## Verification

Unit regressions cover concurrent local reads during a suspended network request,
refresh throttling/failure recovery, and cached guide navigation during a pending
manual refresh. Android tests verify batch/single-channel query equivalence, index
use, preservation of cached programmes during migration, and existing D-pad focus
behavior including navigation to the last item in a large channel list.
An additional 60-channel guide test checks that offscreen rows are deferred and
that D-pad scrolling can still reach and select the final programme.

Run the local checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.core.database.ProgramWindowTest,com.tellyo.app.feature.home.HomeScreenFocusTest,com.tellyo.app.feature.guide.GuideScreenPerformanceTest'
```

On Windows, if Java 21 cannot create its selector pipe because the default temp
path contains accented characters, the session-only workaround used here is:

```powershell
New-Item -ItemType Directory -Force .gradle/java-tmp | Out-Null
$taskJavaTemp = (Resolve-Path .gradle/java-tmp).Path
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$taskJavaTemp -Djava.io.tmpdir=$taskJavaTemp"
```

## Repeatable device measurements

Install the desired build on an otherwise idle TV/emulator, then run:

```powershell
.\scripts\measure-ui-performance.ps1 -AdbPath '<Android SDK>/platform-tools/adb.exe'
```

This records five cold activity-window launch times and frame statistics for
three sidebar navigation cycles under `artifacts/performance/`. It force-stops
the app between launch samples but preserves application data. The device should
be unlocked, with no other test using it. The launch number measures the first
activity window, not usable channel readiness or playback startup.

Use identical device, build type, network and cache conditions for comparisons.
Debug emulator measurements do not establish production latency or a before/after
speedup. The next profiling pass should use a release/profileable build on a
physical TV, measuring channel-ready startup, rapid D-pad scrolling and long
guide windows. Baseline Profiles and release shrinking should follow measurement
and playback/provider regression checks.

## Recorded local result

On the Android 14 `Tellyo_TV_API34` emulator, the debug build passed 40 unit tests
and 10 selected Android tests. Debug assembly and lint passed; lint reported
0 errors and 36 warnings. The five cold activity-window samples were 1322, 1137,
1208, 1166 and 1176 ms (median 1176 ms). Sidebar navigation rendered 137 frames;
`gfxinfo` reported 32 janky frames (23.36%) and a 27 ms median / 250 ms 95th
percentile. This is a diagnostic sample with debug/emulator overhead, and it shows
that further frame-time work is needed before claiming consistently smooth
navigation. There is no controlled pre-change performance baseline for this pass.
