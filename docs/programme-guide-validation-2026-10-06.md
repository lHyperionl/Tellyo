# Additional programme guides — 6 October 2026

The default app now supplies programme data for the five Markíza channels, TA3,
the four Slovak Prima channels in the IPTV playlist, and Sport1/Sport2. Existing
STVR and JOJ guides remain registered, giving 20 channels with built-in guide sources.

| Channels | Source | Returned programmes in the live check |
| --- | --- | ---: |
| Markíza, Doma, Dajto, Markíza Krimi, Markíza Klasik | [Markíza programme page](https://www.markiza.sk/program) | 640 / 765 / 882 / 707 / 729 |
| TA3 | [TA3 programme page](https://www.ta3.com/program) | 427 |
| Prima SK, Prima COOL SK, Prima KRIMI SK, Prima LOVE SK | [Aktuality listings](https://tv-program.aktuality.sk/stanica/prima-sk/) | 202 / 242 / 179 / 180 |
| Sport1, Sport2 | [Sport1 listings](https://tv-program.aktuality.sk/stanica/sport1/), [Sport2 listings](https://tv-program.aktuality.sk/stanica/sport2/) | 110 / 104 |

These counts are observations from the validation run, not fixed schedule sizes.
The live test confirmed a current programme and programmes beyond the next 24
hours for each of the 12 channels. It also confirmed replacement coverage for
each channel. The retained run output is `artifacts/epg/live-validation.xml`.

The sources refresh at most every six hours through the existing repository.
The Prima/Sport mappings match the bundled M3U IDs exactly, including the
upstream spelling `iptv-PrimaloveSK.sk`. Unknown redirects and pages with no
valid programmes fail without deleting the stored guide. Partial source results
declare coverage only for returned channels. Configured aggregate XMLTV has
higher priority than the new sources.

Verification:

- 83 unit tests passed; the live test is skipped by default.
- The separately enabled live test passed for all 12 requested channels.
- Android lint passed.
- The debug APK built successfully at `app/build/outputs/apk/debug/app-debug.apk`.

Parser tests exercise all channel mappings, the real bundled playlist IDs,
duplicate and invalid records, HTML entities and escaped strings, timezone
offsets across the autumn clock change, XMLTV round trips, and partial/complete
source failures. The existing repository tests cover refresh throttling and
preservation of programmes outside a successful replacement window.

The sources depend on the publishers' current page schemas. This verification
checks live metadata downloads and the application build; it does not establish
installation or visual behaviour on a physical TV.
