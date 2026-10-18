param([string]$DeviceSerial = 'e7d5dc18', [switch]$SkipRegression)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day13'
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build with -PisolatedTestApp=true first.' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$sourceRoots = @('android-native/app/src', 'android-native/app/schemas', 'android-native/app/build.gradle.kts', 'android-native/gradle/libs.versions.toml', 'tools/port-native-questionnaire.cjs', 'tools/verify-d13-device.ps1')
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
function Read-KeyboardHierarchy {
    # A newly opened settings Activity can temporarily have no accessibility root.
    # Poll actual readiness and never consume an old XML dump after a failed dump.
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $dump = Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/tala-d13-keyboard.xml')
        if (($dump -join "`n") -notmatch 'ERROR:|Exception:' -and ($dump -join "`n") -match 'dumped to:') {
            $tree = [xml]((Invoke-Adb @('shell', 'cat', '/sdcard/tala-d13-keyboard.xml')) -join "`n")
            if ($tree.SelectSingleNode("//node[contains(@text, 'System UI') and contains(@text, 'responding')]")) {
                $tree.OuterXml | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $evidence 'systemui-anr-preflight.xml')
                $wait = $tree.SelectSingleNode("//node[@text='Wait']")
                if (!$wait) { throw 'System UI is unresponsive and no Wait control is available.' }
                Write-Host 'System UI ANR observed during settings preflight; selecting Wait. App assertions are unchanged.'
                Tap-Node $wait
                continue
            }
            return $tree
        }
    }
    throw 'Gboard settings did not expose an accessibility root; unlock/wake the test device and retry.'
}
function Tap-Node($Node) {
    if ($null -eq $Node -or $Node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Gboard settings control unavailable.' }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Invoke-Adb @('shell', 'input', 'tap', [string]$x, [string]$y) | Out-Null
}
function Gboard-Visibility($Desired = $null) {
    # Gboard on Android 16+ uses pk_always_show_vk rather than the legacy secure
    # setting. Use its exported settings UI; no root, private files or app changes.
    # Primary reference: chromium commit 613ca493b9e14cb4df5e715507bcfd9eb560d52f.
    Invoke-Adb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-f', '0x10008000', '-n', 'com.google.android.inputmethod.latin/com.google.android.apps.inputmethod.latin.preference.SettingsActivity') | Out-Null
    $tree = $null
    for ($ready = 0; $ready -lt 5; $ready++) {
        $tree = Read-KeyboardHierarchy
        if ($tree.SelectSingleNode("//node[@text='Physical keyboard' or @text='Show on-screen keyboard']")) { break }
        # A valid hierarchy may still be the keyguard or an unpopulated Activity.
        Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_MENU') | Out-Null
        Invoke-Adb @('shell', 'am', 'start', '-f', '0x10008000', '-n', 'com.google.android.inputmethod.latin/com.google.android.apps.inputmethod.latin.preference.SettingsActivity') | Out-Null
    }
    $physical = $tree.SelectSingleNode("//node[@text='Physical keyboard']")
    if ($physical) {
        Tap-Node $physical
        for ($ready = 0; $ready -lt 5; $ready++) {
            $tree = Read-KeyboardHierarchy
            if ($tree.SelectSingleNode("//node[@text='Show on-screen keyboard']")) { break }
        }
    }
    $label = $tree.SelectSingleNode("//node[@text='Show on-screen keyboard']")
    if (!$label) { throw 'Enable Gboard Physical keyboard > Show on-screen keyboard before running the UI checks; the setting could not be located.' }
    $row = $label.ParentNode
    while ($row -and !$row.SelectSingleNode(".//node[@checkable='true']")) { $row = $row.ParentNode }
    if (!$row) { throw 'Gboard keyboard-visibility switch unavailable.' }
    $toggle = $row.SelectSingleNode(".//node[@checkable='true']")
    $before = $toggle.checked -eq 'true'
    if ($null -ne $Desired -and $before -ne [bool]$Desired) {
        Tap-Node $toggle
        $confirmed = $false
        for ($ready = 0; $ready -lt 5; $ready++) {
            $updated = Read-KeyboardHierarchy
            $updatedLabel = $updated.SelectSingleNode("//node[@text='Show on-screen keyboard']")
            $updatedRow = if ($updatedLabel) { $updatedLabel.ParentNode } else { $null }
            while ($updatedRow -and !$updatedRow.SelectSingleNode(".//node[@checkable='true']")) { $updatedRow = $updatedRow.ParentNode }
            if ($updatedRow -and (($updatedRow.SelectSingleNode(".//node[@checkable='true']").checked -eq 'true') -eq [bool]$Desired)) {
                $confirmed = $true
                break
            }
        }
        if (!$confirmed) { throw 'Gboard keyboard-visibility change was not confirmed.' }
    }
    return $before
}
$stayAwake = ([string](Invoke-Adb @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'))).Trim()
if ($stayAwake -notmatch '^\d+$') { $stayAwake = '0' }
$hardwareKeyboardIme = ([string](Invoke-Adb @('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard'))).Trim()
$screenTimeout = ([string](Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout'))).Trim()
$gboardInitial = $null
try {
    # Cover every charging type; the timeout also covers an unplugged emulator.
    Invoke-Adb @('shell', 'svc', 'power', 'stayon', 'true')
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    # API 37 may still show the insecure emulator keyguard after WAKEUP returns.
    # MENU dismisses that surface without changing any lock-screen setting.
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_MENU')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
    # Emulator hardware-keyboard defaults can suppress the IME and invalidate the
    # keyboard/inset assertions. Restore the user's original setting in finally.
    Invoke-Adb @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    $api = [int]([string](Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk'))).Trim()
    $ime = ([string](Invoke-Adb @('shell', 'settings', 'get', 'secure', 'default_input_method'))).Trim()
    if (!$SkipRegression -and $api -ge 36 -and $ime.StartsWith('com.google.android.inputmethod.latin/')) {
        $gboardInitial = Gboard-Visibility
        Gboard-Visibility $true | Out-Null
    }
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    Invoke-Test 'ph.tala.registry.data.local.InterviewLifecyclePersistenceTest' 'device-lifecycle.log'
    Invoke-Test 'ph.tala.registry.ui.WorkspaceRestorationDeviceTest' 'device-workspace-restoration.log'
    if (!$SkipRegression) {
        Invoke-Test 'ph.tala.registry.data.RegistrationDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest,ph.tala.registry.data.local.HouseholdServicesPersistenceTest,ph.tala.registry.data.local.EducationEmploymentPersistenceTest,ph.tala.registry.data.local.DemographicSectionsPersistenceTest,ph.tala.registry.data.local.RecordStoreDeviceTest,ph.tala.registry.data.local.SchemaMigrationTest,ph.tala.registry.data.local.DisasterProtectionPersistenceTest' 'device-storage-regression.log'
        Invoke-Test 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest' 'device-ui-regression.log'
        Invoke-Test 'ph.tala.registry.ui.FoundationSectionsDeviceTest#writeBranchesThroughNativeForms' 'device-questionnaire-write.log'
        Invoke-Adb @('shell', 'am', 'force-stop', $package)
        Invoke-Test 'ph.tala.registry.ui.FoundationSectionsDeviceTest#verifyAfterForceStop' 'device-questionnaire-restart.log'
    }
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#completeThroughReviewWithDrawUndoRedoAndClear' 'device-complete-interview.log'
    Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#signatureAndReviewedStatusSurviveForceStop' 'device-signature-restart.log'
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#callbackNoRespondentRefusalAndPartialResume' 'device-callback-refusal.log'
    foreach ($name in @('signature-reopen.txt', 'visit-outcomes-reopen.txt', 'completed-interview-reopen.txt', 'drawn-signature.png', 'interview-reviewed.png', 'signature-after-force-stop.png', 'refused-interview.png')) {
        Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/day13/$name", (Join-Path $evidence $name))
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
    if ($SkipRegression) { Write-Output 'Day 13 lifecycle checks passed; full regression was skipped.' }
    else { Write-Output 'Day 13 device acceptance passed.' }
} finally {
    try {
        if ($null -ne $gboardInitial) { Gboard-Visibility $gboardInitial | Out-Null }
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
}
