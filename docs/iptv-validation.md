# Slovak IPTV validation

Validated on 2 October 2026 using the bundled IPTV Nexus Slovak-language playlist and an Android TV API 34 emulator. This is a point-in-time check from this network; availability can change.

- Source entries: 55. Supported HLS entries: 54. Existing STVR duplicates: 4. Added channels: 50.
- Desktop delivery probe: 44 entries delivered valid manifests and media segments; 10 failed; 1 unsupported.
- Android TV decoding: all 50 added channels attempted; 40 produced a decoded video frame and advancing playback; 10 failed.
- TV Poprad is omitted because its extensionless transport-stream URL is outside the existing HLS/DASH player contract.
- Build and lint passed. All 36 unit tests passed. The normal Android test run passed 13 tests and skipped the opt-in live test; a separate live run passed both playlist-resolution and full channel-decoding tests.
- A local HTTP server verified that VLC headers reached requests made by the production MediaSession service and were cleared for the next channel. The D-pad navigation test reached and played the final channel in a row containing 50 additions.

The ten failing channels remain in the refreshable catalogue so they can recover when their sources return. None are reported as verified playable. Existing Markíza/JOJ routes are not replaced by this playlist.

| Channel | Android TV result | Evidence |
| --- | --- | --- |
| Antik Info TV | played | Decoded frame and advancing playback |
| Arena Sport 1 | played | Decoded frame and advancing playback |
| Arena Sport 2 | played | Decoded frame and advancing playback |
| Canal+ Action Europe | played | Decoded frame and advancing playback |
| ducktv PLUS | played | Decoded frame and advancing playback |
| EbS | played | Decoded frame and advancing playback |
| EbS+ | played | Decoded frame and advancing playback |
| HAHA TV | played | Decoded frame and advancing playback |
| JimJam Europe | failed | ERROR_CODE_IO_BAD_HTTP_STATUS: Response code: 403 |
| LifeTv | played | Decoded frame and advancing playback |
| Mana Cirkev Online | played | Decoded frame and advancing playback |
| MTR | played | Decoded frame and advancing playback |
| NZTV | played | Decoded frame and advancing playback |
| Prima Cool SK | played | Decoded frame and advancing playback |
| Prima Krimi SK | played | Decoded frame and advancing playback |
| Prima love SK | played | Decoded frame and advancing playback |
| Prima SK | played | Decoded frame and advancing playback |
| Senzi | played | Decoded frame and advancing playback |
| Spektrum | played | Decoded frame and advancing playback |
| Spektrum Home | played | Decoded frame and advancing playback |
| Sport1 | played | Decoded frame and advancing playback |
| Sport2 | played | Decoded frame and advancing playback |
| Televizia Mocenok | played | Decoded frame and advancing playback |
| Turbo TV | played | Decoded frame and advancing playback |
| TV8 | played | Decoded frame and advancing playback |
| TV9 | played | Decoded frame and advancing playback |
| TV Brezova | failed | No decoded frame within 15 seconds |
| TV Central | played | Decoded frame and advancing playback |
| TV DK | played | Decoded frame and advancing playback |
| TV Liptov | played | Decoded frame and advancing playback |
| TV LocAll | played | Decoded frame and advancing playback |
| TV Lux | played | Decoded frame and advancing playback |
| TV Mistral | played | Decoded frame and advancing playback |
| TV Myjava | failed | No decoded frame within 15 seconds |
| TV Nitricka | played | Decoded frame and advancing playback |
| TV NRSR | played | Decoded frame and advancing playback |
| TV Panorama | played | Decoded frame and advancing playback |
| TV Povazie | played | Decoded frame and advancing playback |
| TV Raj | failed | ERROR_CODE_IO_BAD_HTTP_STATUS: Response code: 403 |
| TV Romana | played | Decoded frame and advancing playback |
| TV Ruzinov | played | Decoded frame and advancing playback |
| TV Sen | failed | No decoded frame within 15 seconds |
| TV Severka | failed | ERROR_CODE_IO_BAD_HTTP_STATUS: Response code: 503 |
| TV Skalica | failed | No decoded frame within 15 seconds |
| TV Stara Tura | failed | No decoded frame within 15 seconds |
| TVT | played | Decoded frame and advancing playback |
| TV Vega | failed | ERROR_CODE_IO_BAD_HTTP_STATUS: Response code: 503 |
| TV Vita | played | Decoded frame and advancing playback |
| VIO TV | failed | No decoded frame within 15 seconds |
| Zapadoslovenska TV | played | Decoded frame and advancing playback |

Reproduction commands and report paths are documented in README.md. Video checks use the same StreamMediaSourceFactory as the production MediaSession service. These short startup checks do not establish sustained playback, device-specific codec support, language-track correctness, or broadcaster authorization.
