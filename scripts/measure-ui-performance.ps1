param(
    [string]$AdbPath = 'adb',
    [string]$DeviceSerial = '',
    [ValidateRange(1, 20)][int]$Iterations = 5,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../artifacts/performance')
)

$ErrorActionPreference = 'Stop'
$deviceArgs = if ($DeviceSerial) { @('-s', $DeviceSerial) } else { @() }
function Invoke-Adb([string[]]$AdbArguments) {
    $result = & $AdbPath @deviceArgs @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$samples = @()
for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    $launch = Invoke-Adb @('shell', 'am', 'start', '-W', '-S', '-n', 'com.tellyo.app/.MainActivity')
    if ($launch -notmatch 'Status: ok' -or $launch -notmatch 'TotalTime:\s+(\d+)') {
        throw "Launch measurement failed: $launch"
    }
    $samples += [int]$Matches[1]
    Start-Sleep -Milliseconds 700
}

# Reset after startup, then visit Guide, Favorites, Settings and Diagnostics
# through the sidebar and return Home. No playback or data reset is performed.
Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', 'com.tellyo.app', 'reset') | Out-Null
for ($cycle = 0; $cycle -lt 3; $cycle++) {
    for ($destination = 0; $destination -lt 4; $destination++) {
        Invoke-Adb @('shell', 'input', 'keyevent', '20', '23') | Out-Null
        Start-Sleep -Milliseconds 350
    }
    Invoke-Adb @('shell', 'input', 'keyevent', '19', '19', '19', '19', '23') | Out-Null
    Start-Sleep -Milliseconds 350
}
$frames = Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', 'com.tellyo.app', 'framestats')
$frames | Set-Content -LiteralPath (Join-Path $OutputDirectory 'navigation-frames.txt') -Encoding utf8
$sortedSamples = @($samples | Sort-Object)
$middle = [int][Math]::Floor($samples.Count / 2)
$median = if ($samples.Count % 2 -eq 0) {
    ($sortedSamples[$middle - 1] + $sortedSamples[$middle]) / 2.0
} else { $sortedSamples[$middle] }
$report = [ordered]@{
    recordedAt = (Get-Date).ToString('o')
    device = Invoke-Adb @('shell', 'getprop', 'ro.product.model')
    apiLevel = Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')
    installedPackage = Invoke-Adb @('shell', 'dumpsys', 'package', 'com.tellyo.app')
    coldActivityLaunchMillis = $samples
    medianColdActivityLaunchMillis = $median
    note = 'am start measures the initial activity window, not channel readiness. gfxinfo records navigation frames. Compare identical build types, devices, cache and network conditions.'
}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'ui-performance.json') -Encoding utf8
Write-Output "Cold activity launch samples (ms): $($samples -join ', ')"
Write-Output "Median (ms): $($report.medianColdActivityLaunchMillis)"
Write-Output "Reports: $([IO.Path]::GetFullPath($OutputDirectory))"
