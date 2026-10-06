# Emulator startup validation

Investigated on 3 October 2026 with Android Emulator 37.1.11 on Windows 11.

## Failure and correction

Detached launches from the automated PowerShell terminal left the emulator
offline before Android booted. This happened with both the API 34 and API 36
TV images, including with software graphics and CPU acceleration disabled.
Tellyo had not been installed or started during those failed boots.

Keeping the launching PowerShell session alive allowed both images to boot.
An API 36 control run using the original SDK path and graphics settings succeeded
with `Start-Process -Wait`. Thus the accented Windows user path, app APK, and
TV image were not established causes. The launch process lifetime is the
observed difference between failing and successful runs.

`scripts/start-tv-emulator.ps1` now uses `Start-Process -NoNewWindow -Wait
-PassThru`, explicitly waits for the emulator process tree, and checks its exit
code. The amended script successfully cold-booted the existing API 34 TV image
using the original SDK path and host GPU settings. No emulator data was wiped,
and no production Android source was changed for this investigation.

## App verification

The same debug APK was used throughout:
`551DDCA21437417B2B4C82025396367F767C7698DDF4A8EFFCA2BC868DBD9FCA`
(SHA-256).

| Check | Result |
| --- | --- |
| Android TV API 34 / x86 install and cold app launch | Success; cold launch 3.157 seconds |
| Android TV API 36 / x86_64 install and cold app launch | Success; cold launch 1.615 seconds |
| API 34 physical D-pad key events | Opened Jednotka from the dashboard; Back returned to the dashboard |
| API 34 live Media3 playback | `playing` event and decoded live picture; adaptive video reached 1920 x 1080 |
| API 34 app process restart | Fresh source resolved and remembered channel resumed playback |
| API 36 existing navigation and playback recovery instrumentation | All 14 tests passed |
| APK compatibility metadata | Minimum API 23; ARM32, ARM64, x86 and x86_64 native libraries; TV launcher declared; touchscreen optional |

Evidence is stored under `artifacts/startup-investigation/`: `startup.png`,
`playback.png`, `api34-playback-log.txt`, `api36-startup.png`, and
`api36-regression-tests.txt`.

These checks establish that the current APK starts and plays in the TV emulator.
They do not certify a particular Mi Box: its Android version, WebView, hardware
decoders, and network still require an on-device check. No physical Mi Box was
connected during this investigation.
