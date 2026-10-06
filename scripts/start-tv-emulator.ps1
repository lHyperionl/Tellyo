param(
    [string]$AvdName = 'Tellyo_TV_API34',
    [string]$SdkDirectory = ''
)

$ErrorActionPreference = 'Stop'
if (-not $SdkDirectory) {
    $SdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
$emulatorPath = Join-Path $SdkDirectory 'emulator/emulator.exe'
if (-not (Test-Path -LiteralPath $emulatorPath -PathType Leaf)) {
    throw "Android Emulator was not found at $emulatorPath. Supply -SdkDirectory."
}

# The API 34 emulator's host video decoder can leave adaptive H.264 pictures
# in the top-left of a larger frame after pre-roll. Software video decoding
# fixes that emulator defect. GPU rendering and physical TV decoding are intact.
# Cold boot also prevents loading a snapshot with the previous decoder setup.
# Keep the launcher and its QEMU child attached to this PowerShell session.
# A detached GUI launch can stall before Android boots when its parent shell
# exits in the automated terminal. The emulator window remains interactive.
$emulatorProcess = Start-Process -FilePath $emulatorPath -ArgumentList @(
    '-avd', $AvdName,
    '-no-snapshot-load',
    '-no-boot-anim',
    '-feature', '-HardwareDecoder'
) -NoNewWindow -Wait -PassThru
if ($emulatorProcess.ExitCode -ne 0) {
    throw "Android Emulator exited with code $($emulatorProcess.ExitCode)."
}
