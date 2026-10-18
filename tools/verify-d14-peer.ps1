param([string]$DeviceSerial = 'e7d5dc18')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day14/peer-verification'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$inputRoots = @('android-native/app/src', 'android-native/app/schemas', 'android-native/app/build.gradle.kts', 'android-native/gradle/libs.versions.toml', 'planning/docs/schemas/questionnaire-current.json', 'tools/port-native-questionnaire.cjs', 'tools/verify-d14-device.ps1', 'tools/verify-d14-peer.ps1')
function Peer-Fingerprints {
    @($inputRoots | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $workspace $_) -Recurse -File } | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path = $_.FullName.Substring($workspace.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName).Hash }
    }) | ConvertTo-Json -Depth 4
}
$before = Peer-Fingerprints
$harness = Get-Content -LiteralPath (Join-Path $workspace 'tools/verify-d14-device.ps1') -Raw
$start = $harness.IndexOf('function Invoke-Adb')
$end = $harness.IndexOf('$stayAwake =')
Invoke-Expression $harness.Substring($start, $end - $start)
$stay = ([string](Invoke-Adb @('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'))).Trim()
$timeout = ([string](Invoke-Adb @('shell', 'settings', 'get', 'system', 'screen_off_timeout'))).Trim()
$ime = ([string](Invoke-Adb @('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard'))).Trim()
try {
    Invoke-Adb @('shell', 'svc', 'power', 'stayon', 'usb')
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_MENU')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
    Invoke-Adb @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install', '-r', (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    Invoke-Test 'ph.tala.registry.ui.FoundationSectionsDeviceTest#writeBranchesThroughNativeForms' 'device-branches-write.log'
    Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Test 'ph.tala.registry.ui.FoundationSectionsDeviceTest#verifyAfterForceStop' 'device-branches-restart.log'
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#completeThroughReviewWithDrawUndoRedoAndClear' 'device-signature-review.log'
    Invoke-Adb @('shell', 'am', 'force-stop', $package)
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#signatureAndReviewedStatusSurviveForceStop' 'device-signature-restart.log'
    Invoke-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#callbackNoRespondentRefusalAndPartialResume' 'device-callback-refusal.log'
    foreach ($name in @('drawn-signature.png', 'signature-after-force-stop.png', 'refused-interview.png')) {
        Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/day13/$name", (Join-Path $evidence $name))
    }
} finally {
    Invoke-Adb @('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', $stay)
    Invoke-Adb @('shell', 'settings', 'put', 'system', 'screen_off_timeout', $timeout)
    if ($ime -eq 'null') { Invoke-Adb @('shell', 'settings', 'delete', 'secure', 'show_ime_with_hard_keyboard') }
    else { Invoke-Adb @('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', $ime) }
}
# Finish with two fresh two-member interviews, leaving the latest sample available
# for another reviewer. This also executes all 37 storage/UI regression checks.
& rtk proxy powershell -NoProfile -File (Join-Path $workspace 'tools/verify-d14-device.ps1') -DeviceSerial $DeviceSerial
if ($LASTEXITCODE -ne 0) { throw 'D14 full regression failed during peer verification.' }
if ((Peer-Fingerprints) -ne $before) { throw 'Source changed during peer verification.' }
$before | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $evidence 'tested-source-sha256.json')
Copy-Item -LiteralPath (Join-Path $workspace 'planning/03-evidence/day14/device-build.txt') -Destination (Join-Path $evidence 'device-build.txt')
Write-Output 'D14 peer technical verification passed: 46 instrumentation tests. Reviewer assessment is recorded separately.'
