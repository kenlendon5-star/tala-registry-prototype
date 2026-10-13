param([string]$DeviceSerial = 'e7d5dc18', [switch]$SkipRegression)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day09'
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build with -PisolatedTestApp=true first.' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    & rtk proxy adb -s $DeviceSerial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments[0])" }
}
function Invoke-Test([string]$Classes, [string]$Log) {
    $path = Join-Path $evidence $Log
    if (Test-Path -LiteralPath $path) {
        $previous = Get-Content -LiteralPath $path -Raw
        if ($previous -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
            Copy-Item -LiteralPath $path -Destination (Join-Path $evidence "prior-failed-$([DateTime]::UtcNow.ToString('HHmmss'))-$Log")
        }
    }
    $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Classes, $runner)
    $output | Set-Content -Encoding UTF8 -LiteralPath $path
    $output | Select-Object -Last 10
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($output -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw "Device verification failed: $Log" }
}
$stayAwake = ([string](Invoke-Adb @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'))).Trim()
if ($stayAwake -notmatch '^\d+$') { $stayAwake = '0' }
try {
    Invoke-Adb @('shell', 'svc', 'power', 'stayon', 'usb')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    Invoke-Test 'ph.tala.registry.data.local.DemographicSectionsPersistenceTest' 'device-persistence.log'
    Invoke-Test 'ph.tala.registry.ui.IdentityDeviceTest#writeThroughQuestionnaire' 'device-identity-write.log'
    Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Test 'ph.tala.registry.ui.IdentityDeviceTest#verifyAfterForceStop' 'device-identity-restart.log'
    if (!$SkipRegression) {
        Invoke-Test 'ph.tala.registry.data.RegistrationDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest,ph.tala.registry.data.local.SchemaMigrationTest,ph.tala.registry.data.local.RecordStoreDeviceTest' 'device-storage-regression.log'
        Invoke-Test 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest' 'device-ui-regression.log'
    }
    Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/day09", $evidence)
    @(
        "Verified UTC: $([DateTime]::UtcNow.ToString('o'))"
        "Package: $package"
        "Version: $($metadata.elements[0].versionName) ($($metadata.elements[0].versionCode))"
        "Device: $DeviceSerial"
        "Model: $(Invoke-Adb @('shell', 'getprop', 'ro.product.model'))"
        "Android: $(Invoke-Adb @('shell', 'getprop', 'ro.build.version.release'))"
        "API: $(Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk'))"
        "App SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk')).Hash)"
        "Test SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).Hash)"
    ) | Set-Content -Encoding UTF8 (Join-Path $evidence 'device-build.txt')
    Write-Output 'Day 9 device acceptance passed.'
} finally {
    Invoke-Adb @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $stayAwake)
}
