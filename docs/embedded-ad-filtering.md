# Embedded-player ad filtering

Tellyo's Android WebView does not share Brave Shields or installed browser
extensions. Its built-in network filter runs before the official page loads,
returning an empty HTTP 204 response for subresource requests to dedicated
ad domains. It applies to embedded Markiza, JOJ and TA3 players.

This is a small independently written host filter, not uBlock Origin, a port of
its engine, or an imported EasyList/uAssets list. It blocks DoubleClick,
Google Syndication, Google Ad Services, Smart AdServer and Teads domains and
their subdomains. On 12 October 2026, the published Markiza embed contained
a DoubleClick VAST pre-roll URL; JOJ's live page contained Smart AdServer VAST
tags and a Teads script.

Filtering defaults to enabled and is persisted in DataStore. Settings exposes
**Blokovať reklamy v prehrávači**. Disable it if a broadcaster's player waits
for ads or refuses playback. Changing the setting reloads an open embedded
page; Reset restores enabled filtering. Native Media3 playback is unaffected.

Player SDKs, consent pages, authentication and media CDNs remain available.
No stream manifest is extracted or rewritten, and no login or subscription
requirement is removed. Commercials already encoded in the live broadcast
cannot be removed by this request filter.

WebView interception does not cover blob URLs, later URLs in a redirect chain,
or service-worker requests. There are no cosmetic rules, scriptlets or automatic
filter-list updates. This is a limited first implementation; a broadcaster may
change its ad hosts or require its ad system for playback.

Unit tests cover the published ad hosts, hostname boundaries, player and
authentication exceptions, disabling, top-level navigation and settings reset.

## Validation on 12 October 2026

Debug build and lint passed. The unit suite reported 130 tests with no failures
and one skipped test. Both existing Settings remote-navigation tests passed.

The opt-in production-WebView test passed on the Android TV API 36 emulator:
Markiza reached audible, advancing live video in 16.5 seconds, Doma in 13.0
seconds and JOJ 24 in 23.3 seconds. Back removed each WebView. Logs recorded
blocked Google Syndication and DoubleClick requests. This proves live playback
works with filtering enabled, not that every ad system on every channel is
covered. JOJ and Plus account-based live playback was not verified.

The initial MainActivity-based attempt stopped before opening the player because
Android did not validate the emulator's Ethernet connection. The final test
hosts the production embed directly to isolate filtering from this network gate.

Evidence: `artifacts/ad-filtering/live-playback-log.txt` and
`artifacts/ad-filtering/live-test-results.xml`.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.player.OfficialEmbedPlaybackTest#filteredPrerollStillReachesLiveProgramme' '-Pandroid.testInstrumentationRunnerArguments.filteredPreroll=true'
```
