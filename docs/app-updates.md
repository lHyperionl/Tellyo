# Public app updates

Tellyo checks the latest public GitHub release once after the first Activity
frame on launch. The check runs off the main thread and survives Activity
recreation. A newer compatible Android `versionCode` produces a remote-friendly
Slovak dialog with **Aktualizovať** and **Neskôr**. Choosing Later dismisses the
offer until the next launch. Offline, missing-release and rate-limit failures
are quiet and do not delay the dashboard or playback.

Accepting the update downloads the APK with progress and a cancel action. The
app verifies its exact size, SHA-256, package name, version code and signing
certificate before offering **Nainštalovať**. This opens Android's system
installer using a read-only FileProvider URI. Android 8+ may first ask the user
to allow Tellyo to install apps. Declining permission or cancelling the system
installer leaves the ready update available to retry. Android 6/7 use the
device-wide unknown-sources setting. The app never installs silently.

## Automatic releases

The **Signed Android release** workflow runs on pushes to the default branch
(`main` or `master`), stable `vMAJOR.MINOR.PATCH` tags, and manual dispatch.
Every run tests, lints, builds and signs the APK before publishing a release
in the same repository. Pull requests use the verification workflow and
never receive signing secrets or publish an update.

- `versionCode` is `1000 + GITHUB_RUN_NUMBER` of the release workflow. Keep
  this workflow's run counter when maintaining the pipeline; if replacing
  the workflow, raise the offset above the largest published version code.
- Default-branch/manual runs use `versionName = 0.1.<versionCode>` and tag
  `build-<versionCode>`. Stable version tags use the tag's version name.
- Public assets are `tellyo.apk` and `tellyo-update.json`. The manifest is
  generated from Gradle's actual APK output metadata and the APK's checksum.
- Uploads finish while the release is a draft, so a TV cannot see an incomplete
  release. Reruns do not replace published assets; an older run cannot become
  latest over a newer build. Auto-generated `build-*` tags do not retrigger
  the `v*` release workflow.
- AABs remain workflow artifacts for possible store distribution. The
  GitHub APK updater is intended for direct distribution on TVs.

## Repository setup

The repository must be public for TVs to read releases without credentials.
The release job uses its built-in `GITHUB_TOKEN` with `contents: write`; no
personal access token or separate distribution repository is needed.

Commit and push the updater, workflows and MIT license to `main` to activate
the pipeline. The first successful run creates the first public release;
before that the startup check safely finds no update.

Configure these GitHub Actions repository secrets under Settings → Secrets
and variables → Actions. All releases must use the same signing key:

| Secret | Value |
| --- | --- |
| `TELLYO_KEYSTORE_BASE64` | Base64 of the release keystore bytes |
| `TELLYO_KEYSTORE_PASSWORD` | Keystore password |
| `TELLYO_KEY_ALIAS` | Release key alias |
| `TELLYO_KEY_PASSWORD` | Key password |

Initial setup configured all four secrets and saved the signing backup on
the development workstation under `%USERPROFILE%\.tellyo\signing\`.
`tellyo-release.jks` holds the release key, and
`tellyo-release.credentials.xml` holds Windows-protected credentials for
the current Windows account. Export the password privately when making a
backup for a different machine, because Windows protection is account-specific.

Back up the release keystore and its credentials privately. Do not commit
them. Changing or losing the signing key prevents installed builds from
accepting future updates. An existing debug APK uses a different certificate:
install the first official release manually (uninstall the debug build first
if Android reports a signature conflict). Builds predating this updater also
need one manual installation. Subsequent official builds offer updates at launch.

The default update repository is `lHyperionl/Tellyo`. Forks should set the
`TELLYO_UPDATE_REPOSITORY` environment variable or `-PtellyoUpdateRepository=owner/repo`
when building locally. CI automatically uses `GITHUB_REPOSITORY`.
Version overrides are `TELLYO_VERSION_CODE` / `TELLYO_VERSION_NAME` or
`-PtellyoVersionCode=...` / `-PtellyoVersionName=...`.

## Checks

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python -m unittest discover -s scripts -p test_update_release.py
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tellyo.app.feature.update.UpdatePromptFocusTest,com.tellyo.app.core.update.AndroidUpdateDownloaderTest"
```

Unit tests cover newer/equal/older versions, incompatible devices, missing
and unfinished releases, URL and manifest validation, HTTP failures, download
integrity/size limits and cleanup, consent, dismissal, retry and cancellation.
Release-script tests cover metadata generation, draft-before-upload ordering,
immutable published reruns and preventing latest-release downgrades. TV tests
exercise Update, Later, Cancel and Install using the remote.

Validation on 12 October 2026: debug and signed release builds, unit tests
and lint passed. Each variant ran 126 unit tests with zero failures and one
skipped live-provider test. All five release-script tests passed, and the
release APK verified with Android signing schemes v1, v2 and v3. Instrumentation
tests compiled, but execution was blocked by both installed TV emulators
remaining offline before app installation.
