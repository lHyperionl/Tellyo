# Pre-push review fixes and validation

Validated on 13 October 2026 against the pending app update, startup,
embedded-player and quick-switching changes.

The fullscreen channel rail now leaves broadcaster Up/Down navigation alone
while closed. Channel+/− opens the rail for both native and web playback;
native playback also supports Up/Down. Once the rail opens, arrows browse and
OK commits the selected channel. This preserves consent, login and player menu
navigation without relying on broadcaster DOM structure. Remotes without
Channel+/− can select an embedded channel from the dashboard after Back.

The native buffering watchdog samples buffer growth once per second. It retries
after 20 seconds without growth, with a 90-second overall limit. Five unit
regressions cover slow recovery past 20 seconds, stalled downloads, elapsed time
since the last increase, oscillating buffer values and the overall bound.

The APK verification instrumentation test explicitly returns Unit so JUnit can
run it. Python bytecode caches are excluded from Git.

## Results

- Debug and release: 135 JVM tests each, 134 passed and one opt-in test skipped;
  zero failures or errors.
- Debug application and instrumentation APK assembly passed.
- Debug and release lint: zero errors, 38 warnings each.
- API 36 Android TV emulator: 30 targeted tests passed across quick switching,
  Activity return focus, playback recovery, startup scheduling, update prompts,
  APK verification and settings navigation.
- Five Python release-script tests and 11 JOJ startup tests passed.
- `git diff --check` passed.

Device checks exercise fixtures and lifecycle behavior; this run does not claim
new live broadcaster playback or physical-TV performance measurements.

Local checks used session-only `JAVA_TOOL_OPTIONS` pointing the Java temporary
and Unix-domain socket directories to `C:/tmp`. The instrumentation rebuild also
used `-Pkotlin.compiler.execution.strategy=in-process` after the local Kotlin
daemon failed to connect. Neither workaround changes repository build defaults.
