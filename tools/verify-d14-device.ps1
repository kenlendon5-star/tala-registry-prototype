param([string]$DeviceSerial = 'e7d5dc18', [switch]$SkipRegression)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day14'
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build with -PisolatedTestApp=true first.' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$sourceRoots = @('android-native/app/src', 'android-native/app/schemas', 'android-native/app/build.gradle.kts', 'android-native/gradle/libs.versions.toml', 'tools/port-native-questionnaire.cjs', 'tools/verify-d14-device.ps1')
function Source-Fingerprints {
    @($sourceRoots | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $workspace $_) -Recurse -File } |
        Sort-Object FullName | ForEach-Object {
            [ordered]@{ path = $_.FullName.Substring($workspace.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName).Hash }
        }) | ConvertTo-Json -Depth 4
}
$testedSources = Source-Fingerprints
function Invoke-Adb([string[]]$Arguments) {
    & rtk proxy adb -s $DeviceSerial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments[0])" }
}
function Invoke-Test([string]$Classes, [string]$Log) {
    $path = Join-Path $evidence $Log
    if (Test-Path -LiteralPath $path) {
        $previous = Get-Content -LiteralPath $path -Raw
        if ($previous -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' -or $previous -notmatch 'OK \(\d+ tests?\)') {
            Copy-Item -LiteralPath $path -Destination (Join-Path $evidence "prior-failed-$([DateTime]::UtcNow.ToString('HHmmss'))-$Log")
        }
    }
    # Keep partial evidence even if the terminal/session is interrupted mid-test.
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
    $writer = New-Object System.IO.StreamWriter($stream, (New-Object System.Text.UTF8Encoding($true)))
    $writer.AutoFlush = $true
    try {
        $output = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Classes, $runner) | ForEach-Object {
            $writer.WriteLine([string]$_)
            $_
        }
    } finally { $writer.Dispose() }
    $output | Select-Object -Last 10
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($output -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw "Device verification failed: $Log" }
}
$stayAwake = ([string](Invoke-Adb @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'))).Trim()
if ($stayAwake -notmatch '^\d+$') { $stayAwake = '0' }
$hardwareKeyboardIme = ([string](Invoke-Adb @('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard'))).Trim()
$screenTimeout = ([string](Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout'))).Trim()
try {
    # Cover every charging type; the timeout also covers an unplugged device.
    Invoke-Adb @('shell', 'svc', 'power', 'stayon', 'usb')
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_MENU')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
    Invoke-Adb @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    if (!$SkipRegression) {
        Invoke-Test 'ph.tala.registry.data.RegistrationDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest,ph.tala.registry.data.local.HouseholdServicesPersistenceTest,ph.tala.registry.data.local.EducationEmploymentPersistenceTest,ph.tala.registry.data.local.DemographicSectionsPersistenceTest,ph.tala.registry.data.local.RecordStoreDeviceTest,ph.tala.registry.data.local.SchemaMigrationTest,ph.tala.registry.data.local.DisasterProtectionPersistenceTest,ph.tala.registry.data.local.InterviewLifecyclePersistenceTest' 'device-storage-regression.log'
        Invoke-Test 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest,ph.tala.registry.ui.WorkspaceRestorationDeviceTest' 'device-ui-regression.log'
    }
    # D14 gate: scripted 18-section, two-member interview through the real UI.
    Invoke-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#fullInterviewThroughAllSectionsWithTwoMembers' 'device-full-coverage-write.log'
    Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#verifyFullInterviewAfterForceStop' 'device-full-coverage-restart.log'
    foreach ($name in @('all-sections-consent.png', 'review-18-of-18.png', 'interview-reviewed.png', 'coverage-after-force-stop.png')) {
        Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/day14/$name", (Join-Path $evidence $name))
    }
    if ((Source-Fingerprints) -ne $testedSources) { throw 'Source changed during device verification. Rebuild and rerun before acceptance.' }
    $testedSources | Set-Content -Encoding UTF8 (Join-Path $evidence 'tested-source-sha256.json')
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
    if ($SkipRegression) { Write-Output 'Day 14 full-coverage checks passed; full regression was skipped.' }
    else { Write-Output 'Day 14 device acceptance passed.' }
} finally {
    Invoke-Adb @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $stayAwake)
    if ($hardwareKeyboardIme -eq 'null') {
        Invoke-Adb @('shell', 'settings', 'delete', 'secure', 'show_ime_with_hard_keyboard')
    } else {
        Invoke-Adb @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', $hardwareKeyboardIme)
    }
    if ($screenTimeout -eq 'null') {
        Invoke-Adb @('shell', 'settings', 'delete', 'system', 'screen_off_timeout')
    } else {
        Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', $screenTimeout)
    }
}
