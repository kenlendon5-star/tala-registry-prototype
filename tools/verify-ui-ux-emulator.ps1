param(
    [string]$DeviceSerial = 'emulator-5554',
    [string]$EvidenceDirectory = 'planning/03-evidence/ui-ux-2026-10-20'
)
$ErrorActionPreference = 'Stop'
if ($DeviceSerial -notmatch '^emulator-\d+$') { throw 'This UI review harness requires an emulator serial.' }
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace $EvidenceDirectory
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build the isolated app and test APKs first.' }
$harness = Get-Content (Join-Path $PSScriptRoot 'verify-d14-device.ps1') -Raw
$start = $harness.IndexOf('function Invoke-Adb'); $end = $harness.IndexOf('$stayAwake =')
Invoke-Expression $harness.Substring($start, $end - $start)
function Identity {
    $paths = @('android-native/app/src','android-native/app/schemas','android-native/app/build.gradle.kts','android-native/gradle/libs.versions.toml')
    @($paths | ForEach-Object { Get-ChildItem (Join-Path $workspace $_) -File -Recurse } | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path=$_.FullName.Substring($workspace.Length+1).Replace('\','/'); sha256=(Get-FileHash $_.FullName).Hash }
    }) | ConvertTo-Json -Depth 4
}
$source = Identity
$appApk = Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$appHash = (Get-FileHash $appApk).Hash; $testHash = (Get-FileHash $testApk).Hash
$caseResults = New-Object System.Collections.Generic.List[object]
function Audit-Test([string]$Classes, [string]$Log, [int]$Expected) {
    $passed = $false; $problem = $null
    try { Invoke-Test $Classes $Log; $passed = $true }
    catch { $problem = $_.Exception.Message; Write-Output "Retained failure: $Log" }
    $raw = if (Test-Path (Join-Path $evidence $Log)) { Get-Content (Join-Path $evidence $Log) -Raw } else { '' }
    $successCount = [regex]::Matches($raw, '(?m)^INSTRUMENTATION_STATUS_CODE: 0\r?$').Count
    $failureCount = [regex]::Matches($raw, '(?m)^INSTRUMENTATION_STATUS_CODE: -[1234]\r?$').Count
    if ($successCount -ne $Expected -or $failureCount) { $passed = $false }
    $caseResults.Add([ordered]@{ classes=$Classes; log=$Log; expected=$Expected; passed=$passed; successfulChecks=$successCount; unsuccessfulChecks=$failureCount; error=$problem })
}
$settings = @(
    @('global','stay_on_while_plugged_in'),
    @('system','screen_off_timeout'),
    @('secure','show_ime_with_hard_keyboard'),
    @('system','font_scale'),
    @('system','accelerometer_rotation'),
    @('system','user_rotation')
)
$before = @($settings | ForEach-Object {
    [ordered]@{ namespace=$_[0]; key=$_[1]; value=([string](Invoke-Adb @('shell','settings','get',$_[0],$_[1]))).Trim() }
})
ConvertTo-Json -InputObject $before | Set-Content -Encoding UTF8 (Join-Path $evidence 'automated-environment-before.json')
$originalAc = ((Invoke-Adb @('shell','dumpsys','battery')) -join "`n") -match 'AC powered: true'
try {
    Invoke-Adb @('emu','power','ac','on')
    Invoke-Adb @('shell','svc','power','stayon','true')
    Invoke-Adb @('shell','settings','put','system','screen_off_timeout','1800000')
    Invoke-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
    Invoke-Adb @('shell','settings','put','system','font_scale','1.0')
    Invoke-Adb @('shell','settings','put','system','accelerometer_rotation','0')
    Invoke-Adb @('shell','settings','put','system','user_rotation','0')
    Invoke-Adb @('shell','cmd','window','user-rotation','lock','0')
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP')
    Invoke-Adb @('shell','wm','dismiss-keyguard')
    Invoke-Adb @('install','-r',$appApk)
    Invoke-Adb @('install','-r',$testApk)
    Audit-Test 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest,ph.tala.registry.ui.WorkspaceRestorationDeviceTest,ph.tala.registry.ui.WorkspaceSemanticsDeviceTest' 'ui-regression.log' 16
    Audit-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#fullInterviewThroughAllSectionsWithTwoMembers' 'full-interview.log' 1
    Invoke-Adb @('shell','am','force-stop',$package)
    Audit-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#verifyFullInterviewAfterForceStop' 'full-interview-restart.log' 1
    Audit-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#completeThroughReviewWithDrawUndoRedoAndClear' 'signature-review.log' 1
    Invoke-Adb @('shell','am','force-stop',$package)
    Audit-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#signatureAndReviewedStatusSurviveForceStop' 'signature-restart.log' 1
    Audit-Test 'ph.tala.registry.ui.InterviewLifecycleDeviceTest#callbackNoRespondentRefusalAndPartialResume' 'callback-refusal-resume.log' 1
    ConvertTo-Json -InputObject @($caseResults.ToArray()) -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $evidence 'test-results.json')
    if ((Identity) -ne $source -or (Get-FileHash $appApk).Hash -ne $appHash -or (Get-FileHash $testApk).Hash -ne $testHash) {
        throw 'Source or APK changed during the review.'
    }
    $source | Set-Content -Encoding UTF8 (Join-Path $evidence 'tested-source-sha256.json')
    @("Verified UTC: $([DateTime]::UtcNow.ToString('o'))", "Device: $DeviceSerial", "API: $(Invoke-Adb @('shell','getprop','ro.build.version.sdk'))", "Package: $package", "App SHA256: $appHash", "Test SHA256: $testHash", 'Coverage: 20 UI checks across 6 invocations') | Set-Content -Encoding UTF8 (Join-Path $evidence 'device-build.txt')
} finally {
    $restoreErrors = New-Object System.Collections.Generic.List[string]
    try { Invoke-Adb @('emu','power','ac',$(if ($originalAc) { 'on' } else { 'off' })) | Out-Null } catch { $restoreErrors.Add($_.Exception.Message) }
    foreach ($setting in $before) {
        try {
            if ($setting.value -eq 'null') { Invoke-Adb @('shell','settings','delete',$setting.namespace,$setting.key) | Out-Null }
            else { Invoke-Adb @('shell','settings','put',$setting.namespace,$setting.key,$setting.value) | Out-Null }
        } catch { $restoreErrors.Add($_.Exception.Message) }
    }
    try { Invoke-Adb @('shell','am','force-stop',$package) | Out-Null } catch { $restoreErrors.Add($_.Exception.Message) }
    $after = @($before | ForEach-Object {
        [ordered]@{ namespace=$_.namespace; key=$_.key; value=([string](Invoke-Adb @('shell','settings','get',$_.namespace,$_.key))).Trim() }
    })
    ConvertTo-Json -InputObject $after | Set-Content -Encoding UTF8 (Join-Path $evidence 'automated-environment-after.json')
    if (($before | ConvertTo-Json) -ne ($after | ConvertTo-Json)) { $restoreErrors.Add('Settings do not match the original values.') }
    ConvertTo-Json -InputObject @($restoreErrors.ToArray()) | Set-Content -Encoding UTF8 (Join-Path $evidence 'automated-restoration-errors.json')
    if ($restoreErrors.Count) { throw ($restoreErrors -join '; ') }
}
if (@($caseResults | Where-Object { -not $_.passed }).Count) { throw 'UI audit completed with failed checks; inspect test-results.json and retained logs.' }
'All 20 emulator UI checks passed; source/APK identity and settings restoration verified.'
