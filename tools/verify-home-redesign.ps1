param(
    [string]$DeviceSerial = 'emulator-5554',
    [string]$EvidenceDirectory = 'planning/03-evidence/home-redesign-2026-10-21',
    [switch]$CollectOnly
)
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notmatch '^emulator-\d+$') { throw 'Use an emulator for this visual verification.' }
$workspace = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $workspace $EvidenceDirectory
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$apk = Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$metadata = Get-Content (Join-Path (Split-Path $apk -Parent) 'output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build with -PisolatedTestApp=true first.' }
function Adb {
    # adb pull writes successful progress to stderr; PowerShell 5 must not treat it as failure.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & rtk proxy adb -s $DeviceSerial @args 2>&1
        $resultCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($resultCode -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}
$before = @{
    font = (Adb shell settings get system font_scale).Trim()
    autoRotate = (Adb shell settings get system accelerometer_rotation).Trim()
    rotation = (Adb shell settings get system user_rotation).Trim()
}
$before | ConvertTo-Json | Set-Content (Join-Path $evidence 'settings-before.json')
@{
    device = $DeviceSerial
    api = (Adb shell getprop ro.build.version.sdk).Trim()
    size = Adb shell wm size
    density = Adb shell wm density
} | ConvertTo-Json | Set-Content (Join-Path $evidence 'device.json')
function RunCase([string]$name, [string]$class) {
    $result = Adb shell am instrument -w -r -e class $class -e evidenceCase $name $runner
    $result | Set-Content (Join-Path $evidence "$name.log")
    if ($result -notmatch 'OK \(\d+ tests?\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Failed device check: $name. Inspect its log."
    }
    Write-Output "Passed: $name"
}
try {
    if (-not $CollectOnly) {
    Adb install -r $apk | Out-Null
    Adb install -r $testApk | Out-Null
    Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
    Adb shell wm dismiss-keyguard | Out-Null
    RunCase 'persistence-and-room' 'ph.tala.registry.ui.HomeDashboardDeviceTest#recentInterviewSurvivesStoreRecreation,ph.tala.registry.ui.HomeDashboardDeviceTest#roomPurokQueryUpdatesAfterRealSectionWrite'
    foreach ($rotation in @(0, 1)) {
        foreach ($font in @('1.0', '1.5', '2.0')) {
            $orientation = if ($rotation -eq 0) { 'portrait' } else { 'landscape' }
            $case = "$orientation-$font"
            Adb shell settings put system accelerometer_rotation 0 | Out-Null
            Adb shell settings put system user_rotation $rotation | Out-Null
            Adb shell cmd window user-rotation lock $rotation | Out-Null
            Adb shell settings put system font_scale $font | Out-Null
            RunCase $case 'ph.tala.registry.ui.HomeDashboardDeviceTest#homeActionsCategoriesAndResponsiveLayout'
        }
    }
    }
    foreach ($caseName in @('persistence-and-room','portrait-1.0','portrait-1.5','portrait-2.0','landscape-1.0','landscape-1.5','landscape-2.0')) {
        $caseLog = Get-Content (Join-Path $evidence "$caseName.log") -Raw
        if ($caseLog -notmatch 'OK \(\d+ tests?\)' -or $caseLog -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
            throw "Cannot collect accepted evidence: $caseName has not passed."
        }
    }
    Adb pull "/sdcard/Android/data/$package/files/home-redesign" $evidence | Out-Null
    (Adb logcat -d -s System.out) -split "`n" | Select-String HOME_VIEWPORT | Select-Object -Last 6 |
        ForEach-Object { $_.Line } | Set-Content (Join-Path $evidence 'actual-viewports.log')
    $hashes = foreach ($path in @($apk,$testApk)) {
        $stream = [System.IO.File]::OpenRead($path)
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { @{ Path = $path; Hash = [System.BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','') } }
        finally { $sha.Dispose(); $stream.Dispose() }
    }
    $hashes | ConvertTo-Json | Set-Content (Join-Path $evidence 'apk-sha256.json')
} finally {
    foreach ($item in @(@('font_scale',$before.font),@('accelerometer_rotation',$before.autoRotate),@('user_rotation',$before.rotation))) {
        if ($item[1] -eq 'null') { Adb shell settings delete system $item[0] | Out-Null }
        else { Adb shell settings put system $item[0] $item[1] | Out-Null }
    }
    if ($before.autoRotate -eq '1') { Adb shell cmd window user-rotation free | Out-Null }
    else { Adb shell cmd window user-rotation lock $before.rotation | Out-Null }
    $after = @{
        font = (Adb shell settings get system font_scale).Trim()
        autoRotate = (Adb shell settings get system accelerometer_rotation).Trim()
        rotation = (Adb shell settings get system user_rotation).Trim()
    }
    $after | ConvertTo-Json | Set-Content (Join-Path $evidence 'settings-after.json')
    foreach ($key in $before.Keys) {
        if ($before[$key] -ne $after[$key]) { throw "Device setting was not restored: $key" }
    }
}
