param([string]$DeviceSerial = 'e7d5dc18')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build with -PisolatedTestApp=true before running this script.' }
$logs = Join-Path $workspace 'logs'
$evidence = Join-Path $workspace 'planning/03-evidence/day08'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null

function Invoke-Adb([string[]]$Arguments) {
    & rtk proxy $adb -s $DeviceSerial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments[0])" }
}
function Invoke-Test([string]$Classes, [string]$Log) {
    $previousLog = Join-Path $logs $Log
    $previousEvidence = Join-Path $evidence "prior-failed-$Log"
    if ((Test-Path -LiteralPath $previousLog) -and !(Test-Path -LiteralPath $previousEvidence)) {
        if ((Get-Content -LiteralPath $previousLog -Raw) -match 'FAILURES!!!') {
            Copy-Item -LiteralPath $previousLog -Destination $previousEvidence
        }
    }
    $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Classes, $runner)
    $output | Set-Content (Join-Path $logs $Log)
    $output | Set-Content -Encoding UTF8 (Join-Path $evidence $Log)
    $output | Select-Object -Last 8
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($output -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Device verification failed; inspect $Log"
    }
}

$previousStayAwake = ([string](Invoke-Adb @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'))).Trim()
if ($previousStayAwake -notmatch '^\d+$') { $previousStayAwake = '0' }
try {
Invoke-Adb @('shell', 'svc', 'power', 'stayon', 'usb')
Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
Invoke-Test 'ph.tala.registry.data.RegistrationDeviceTest' 'day08-device-storage.log'
Invoke-Test 'ph.tala.registry.ui.EditingDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest' 'day08-editing-regression.log'
Invoke-Test 'ph.tala.registry.ui.RegistrationUiDeviceTest#registrationNameDetailsPicturesAndProfileSurviveActivityRecreation' 'day08-device-ui.log'
Invoke-Test 'ph.tala.registry.data.RegistrationRestartProbe#writeFixture' 'day08-restart-write.log'
Invoke-Adb @('shell', 'am', 'force-stop', $package)
Invoke-Test 'ph.tala.registry.data.RegistrationRestartProbe#verifyAfterForceStop' 'day08-restart-read.log'
Invoke-Test 'ph.tala.registry.ui.RegistrationUiDeviceTest#dashboardAfterRestartDisplaysPersistedCounts' 'day08-restart-dashboard.log'
$deviceInfo = @(
    "Test package: $package"
    "Device serial: $DeviceSerial"
    "Model: $(Invoke-Adb @('shell', 'getprop', 'ro.product.model'))"
    "Android: $(Invoke-Adb @('shell', 'getprop', 'ro.build.version.release'))"
    "API: $(Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk'))"
    "Verified UTC: $([DateTime]::UtcNow.ToString('o'))"
    "App APK SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk') -Algorithm SHA256).Hash.ToLower())"
    "Test APK SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') -Algorithm SHA256).Hash.ToLower())"
)
$deviceInfo | Set-Content -Encoding UTF8 (Join-Path $evidence 'device-build.txt')
Write-Output 'Day 8 isolated-device acceptance passed, including force-stop/relaunch.'
} finally {
    Invoke-Adb @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $previousStayAwake)
}
