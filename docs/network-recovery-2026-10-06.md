# Internet outage recovery

Tellyo observes Android's validated Internet access. On API 24 and newer it
follows the default network; API 23 retains a network callback with an active
network capability check. A newly available network does not count as online
until its capabilities include both INTERNET and VALIDATED.

While offline, a Slovak connection error appears over the dashboard, native
player, and broadcaster web players without taking remote focus. Native playback
and pending retries pause, provider resolutions are cancelled, and manual Retry
waits for a connection. The native transport controls are hidden while offline.

When Internet returns, the channel that was active at the outage resolves a new
provider source and receives a fresh retry budget. Broadcaster pages reload even
when their embed URL is unchanged. A saved channel opened while offline follows
the same recovery path. Recovery waits while the Activity is stopped, does not
autoplay a manually paused native stream, uses the latest channel selected
during the outage, and does not reopen a closed player.

## Validation

- All 78 JVM unit tests passed, including eight new network recovery cases.
- Android TV API 34: six PlaybackRecoveryTest checks, two NetworkStatusOverlayTest
  checks, and the existing EmbedLifecycleRegressionTest check passed (nine total).
- The UI check verifies a visible offline error over existing content and its
  removal on reconnect. The embed check verifies that a JavaScript marker is
  cleared by reloading the same URL in the existing WebView.
- Debug app and instrumentation APKs built successfully; lint reported zero
  errors and 37 warnings.

Recovery tests use controlled network status and playback failures. They cover
the recovery contract without depending on broadcaster availability; a physical
TV/router outage was not exercised. Windows build validation used the previously
documented process-local JDK Unix-domain socket fallback.
