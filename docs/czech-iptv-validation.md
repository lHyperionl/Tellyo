# Selected Czech IPTV feeds

Added on 7 October 2026 from https://iptv-org.github.io/iptv/countries/cz.m3u.
The bundled `app/src/main/assets/czech-iptv.m3u` records the original 50 requested feeds,
their display names and source order. After removing Disney Channel HU, the
`czech-iptv` provider contributes 46 channels
with the current Slovak playlist: Spektrum, Spektrum Home and Canal+ Action Europe
already exist there and retain their channel identities and playback routes.
Prima's Czech feeds remain distinct from the existing Prima SK feeds. Disney
Channel CZ remains included and both AXN White feeds remain separate entries.

The subsequent channel cleanup removes all 28 channels in the former 126–153
group, followed by Antik Info TV, JimJam Europe, LifeTv, Mana Cirkev Online and
Disney Channel HU. The default lineup is now 78 entries: 14 broadcaster
channels, 18 Slovak playlist channels and 46 Czech additions. The original
bundles remain source snapshots; provider exclusions hide removed channels from
bundled, cached and refreshed lists and reject playback from saved selections.

Disney Channel CZ, Disney Junior, Minimax CEE, Nick Jr., Nickelodeon and Nicktoons
have channel numbers 25–30, immediately after Sport2 (24). A one-time favorites
order migration applies the same placement to existing installations. These six
channels match Sport2's favorite status so the Home screen's grouping keeps the
block together. Other favorites retain their relative order; later manual
changes to channel order and favorites remain available.

The provider starts from the bundle or saved playlist and refreshes in the
background at most every six hours. Remote streams update only the selected
feed IDs; new unrequested channels are ignored. Names and channel numbers stay
stable across upstream reordering. Missing selected feeds retain their bundled
fallback. A refresh with no selected supported feeds is rejected. Single-feed
channels use the existing `iptv-<channel-id>` identity; AXN White retains its
feed suffix to distinguish the two requested variants.

The M3U parser now accepts an explicit provider ID and reads HTTP User-Agent,
Referer and Origin options in both EXTINF metadata and EXTVLCOPT directives.
VLC options override inline values, and headers do not leak to subsequent feeds.

IPTV-org's Czech Canal+ Action Europe entry currently has an extensionless URL
outside the player's HLS/DASH contract. Its bundled Czech fallback uses the
existing Slovak HLS URL. Its current app route remains with the Slovak provider.

## Verification

- Debug app APK and instrumentation APK built successfully.
- Unit suite after channel cleanup: 102 tests, zero failures/errors, one skipped. Regressions cover the
  full selection, duplicate suppression, separate feeds, stable ordering,
  refresh filtering/fallback, invalid channel routing, inline HTTP headers, and
  removed channels returning in remote playlists or old saved selections,
  children's channel placement, and migration of existing favorites order.
- Android lint: zero errors, 37 warnings.
- Desktop delivery probe before cleanup: 49 of the original 50 feeds delivered HLS manifests and
  a media segment. Generic AXN White (`AXNWhite.us@SD`) failed with a TLS EOF;
  `AXNWhite.us@CzechRepublic` delivered successfully. Both requested entries
  remain in the selection so the generic feed can recover on refresh.
- Android decoding test compiled but could not run: the installed API 34 and
  API 36 emulators remained offline before boot, and one API 34 launch exited
  with `0xC0000005`. Android playback is unverified for this selection.

Desktop evidence: `artifacts/iptv/czech-stream-probe.json`. Delivery checks do
not establish sustained playback, decoder compatibility or audio language.
No programme guide source was added for the new channels in this change.

Reproduction (PowerShell):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python scripts/probe_slovak_iptv.py --playlist app/src/main/assets/czech-iptv.m3u --output artifacts/iptv/czech-stream-probe.json
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.core.provider.CzechIptvPlaybackTest -Pandroid.testInstrumentationRunnerArguments.liveCzechIptv=true
```

The opt-in Android test uses the production `StreamMediaSourceFactory`, resolves
all 49 retained Czech source feeds (including the three Slovak duplicates), and
records decoded frames plus advancing playback in
`files/czech-iptv-playback-report.json` inside the app's private directory.

## Discovery Channel / Jak se to dělá

Checked IPTV-org's complete playlist and streams API, plus IPTV Nexus's complete
playlist and Czech-language playlist. Neither has a Czech Discovery Channel
stream. The complete lists contain Asharq Discovery (Arabic), CND Film Discovery
Channel (Chinese), and Discovery Turbo (English), which do not provide the
requested Czech Discovery Channel feed. These alternatives were not added.

IPTV-org's blocklist marks `DiscoveryChannelEurope.uk` as removed for a DMCA
takedown: https://github.com/iptv-org/iptv/issues/16839. There is no usable Czech
Discovery stream to add from these sources.

Oneplay lists the requested programme on Discovery Channel:
https://www.oneplay.cz/porad/15810-jak-se-to-dela. This is an external viewing
option, not an integrated stream or a verified source of Czech audio for Tellyo.
