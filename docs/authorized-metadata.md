# Authorized metadata integration

Tellyo can consume broadcaster-approved playback, logo, and XMLTV metadata from a static HTTPS
origin. Configure the origin at build time with either:

```powershell
$env:TELLYO_CONFIG_BASE_URL = "https://metadata.example.com/tellyo/"
```

or:

```powershell
.\gradlew.bat :app:assembleDebug -PtellyoConfigBaseUrl=https://metadata.example.com/tellyo/
```

The application requests these documents:

```text
GET /tellyo/config/v1/channels.json
GET /tellyo/config/v1/providers.json
```

`channels.json` uses the same schema as `app/src/main/assets/channels.json`. An approved logo can
be supplied through an HTTPS `logoUrl`. Tellyo falls back to its text treatment if the image is
missing or fails to load.

`providers.json` uses this format:

```json
{
  "schemaVersion": 1,
  "streams": [
    {
      "channelId": "joj",
      "uri": "https://broadcaster-approved.example/joj/master.m3u8",
      "type": "HLS",
      "expiresAt": "2026-08-17T00:00:00Z",
      "enabled": true
    }
  ],
  "epg": {
    "uri": "https://broadcaster-approved.example/epg/v1/guide.xml.gz",
    "format": "XMLTV",
    "enabled": true
  }
}
```

Only HTTPS HLS/DASH routes and HTTPS XMLTV are accepted. Expired stream routes are ignored. The
aggregate XMLTV document must use Tellyo channel IDs (for example `stvr-jednotka`, `joj`, and
`markiza`). Gzip-compressed XMLTV is supported.

Do not put account passwords, cookies, bearer tokens, DRM keys, or other long-lived secrets in
these files. The endpoint is metadata-only and must not proxy video. Stream and logo entries must
be supplied under an agreement that permits their use in Tellyo.

Fallbacks are:

```text
authorized remote config -> last valid DataStore cache -> bundled config
```

If an authorized direct route is absent, the five Markíza channels use their published
embed players and TA3 hosts its official live web player inside Tellyo. JOJ also hosts its
individual official live players, including their login/subscription checks; JOJ 24 uses the
embed published on its homepage. If an authorized aggregate XMLTV feed is configured and later fails, the
database retains its last valid guide instead of parsing a broadcaster webpage.

## Release signing

The signed release workflow expects these GitHub Actions secrets:

```text
TELLYO_KEYSTORE_BASE64
TELLYO_KEYSTORE_PASSWORD
TELLYO_KEY_ALIAS
TELLYO_KEY_PASSWORD
```

Set the non-secret Actions variable `TELLYO_CONFIG_BASE_URL` to the approved metadata origin. The
workflow builds signed APK and AAB artifacts for `v*` tags or a manual dispatch. Keystores and
passwords must never be committed to the repository.
