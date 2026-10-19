param([string]$DeviceSerial = 'e7d5dc18', [switch]$RegressionOnly, [switch]$ManualFallbacks)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day15'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build the isolated APKs first.' }
$roots = @('android-native/app/src','android-native/app/schemas','android-native/app/build.gradle.kts','android-native/gradle/libs.versions.toml','planning/docs/schemas/questionnaire-current.json','tools/port-native-questionnaire.cjs','tools/verify-d14-device.ps1','tools/verify-d15-device.ps1')
function Fingerprints {
    @($roots | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $workspace $_) -File -Recurse } | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path=$_.FullName.Substring($workspace.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName).Hash }
    }) | ConvertTo-Json -Depth 4
}
$before=Fingerprints
$harness=Get-Content -LiteralPath (Join-Path $workspace 'tools/verify-d14-device.ps1') -Raw
$start=$harness.IndexOf('function Invoke-Adb'); $end=$harness.IndexOf('$stayAwake =')
Invoke-Expression $harness.Substring($start,$end-$start)
function Permission-State([string]$Permission) {
    $dump=(Invoke-Adb @('shell','dumpsys','package',$package)) -join "`n"
    $line=($dump -split "`n" | Where-Object { $_ -match "$Permission`: granted=" } | Select-Object -Last 1)
    [ordered]@{ permission=$Permission; granted=($line -match 'granted=true'); userSet=($line -match 'USER_SET'); userFixed=($line -match 'USER_FIXED') }
}
function Reset-Permissions([bool]$Grant) {
    Invoke-Adb @('shell','am','force-stop',$package) | Out-Null
    foreach($p in @('android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION')) {
        Invoke-Adb @('shell','pm','revoke',$package,$p) | Out-Null
        Invoke-Adb @('shell','pm','clear-permission-flags',$package,$p,'user-set','user-fixed') | Out-Null
    }
    if($Grant) {
        Invoke-Adb @('shell','pm','grant',$package,'android.permission.ACCESS_COARSE_LOCATION') | Out-Null
        Invoke-Adb @('shell','pm','grant',$package,'android.permission.ACCESS_FINE_LOCATION') | Out-Null
    }
}
& rtk proxy node (Join-Path $workspace 'tools/port-native-questionnaire.cjs') --check
if($LASTEXITCODE -ne 0) { throw 'Questionnaire generation differs.' }
Invoke-Adb @('install','-r',(Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('install','-r',(Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
$permissions=@(Permission-State 'android.permission.ACCESS_FINE_LOCATION'; Permission-State 'android.permission.ACCESS_COARSE_LOCATION')
if($ManualFallbacks -and (-not $RegressionOnly -or @($permissions | Where-Object granted).Count)) {
    throw '-ManualFallbacks requires -RegressionOnly and already-denied location permissions; it never changes permission grants.'
}
$stay=([string](Invoke-Adb @('shell','settings','get','global','stay_on_while_plugged_in'))).Trim()
$screen=([string](Invoke-Adb @('shell','settings','get','system','screen_off_timeout'))).Trim()
$ime=([string](Invoke-Adb @('shell','settings','get','secure','show_ime_with_hard_keyboard'))).Trim()
$locationEnabled=([string](Invoke-Adb @('shell','cmd','location','is-location-enabled'))).Trim()
$mockOutput=(Invoke-Adb @('shell','appops','get','com.android.shell','android:mock_location')) -join "`n"
$mockMode='default'; if($mockOutput -match 'mock_location:\s*(allow|deny|ignore|default|foreground)') { $mockMode=$Matches[1] }
$gps=$false; $fused=$false
@{ permissions=$permissions; stayAwake=$stay; timeout=$screen; keyboard=$ime; locationEnabled=$locationEnabled; shellMockMode=$mockMode } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 (Join-Path $evidence 'environment-before.json')
try {
    Invoke-Adb @('shell','svc','power','stayon','usb')
    Invoke-Adb @('shell','settings','put','system','screen_off_timeout','1800000')
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP')
    Invoke-Adb @('shell','input','keyevent','KEYCODE_MENU')
    Invoke-Adb @('shell','wm','dismiss-keyguard')
    Invoke-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
    # Fail before lengthy regression runs when the vendor blocks ADB permission control.
    if(-not $RegressionOnly) { Reset-Permissions $false }
    Invoke-Test 'ph.tala.registry.data.RegistrationDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest,ph.tala.registry.data.local.HouseholdServicesPersistenceTest,ph.tala.registry.data.local.EducationEmploymentPersistenceTest,ph.tala.registry.data.local.DemographicSectionsPersistenceTest,ph.tala.registry.data.local.RecordStoreDeviceTest,ph.tala.registry.data.local.SchemaMigrationTest,ph.tala.registry.data.local.DisasterProtectionPersistenceTest,ph.tala.registry.data.local.InterviewLifecyclePersistenceTest' 'device-storage-regression.log'
    Invoke-Test 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest,ph.tala.registry.ui.WorkspaceRestorationDeviceTest' 'device-ui-regression.log'
    Invoke-Test 'ph.tala.registry.data.local.CoordinatePersistenceDeviceTest' 'device-coordinate-storage.log'
    if($RegressionOnly -and $ManualFallbacks) {
        Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#manualWithoutPermissionSurvivesActivityRecreation' 'device-manual-write.log'
        Invoke-Adb @('shell','am','force-stop',$package)
        Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#manualReopensAfterForceStop' 'device-manual-restart.log'
        Invoke-Adb @('shell','cmd','location','set-location-enabled','false')
        Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#locationOffAndManualFallbackKeepInterviewUsable' 'device-location-off.log'
    }
    if(-not $RegressionOnly) {
    Invoke-Adb @('shell','cmd','location','set-location-enabled','true')
    Reset-Permissions $false
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#deniedThenPermanentDenialDoesNotBlockInterview' 'device-permission-denied.log'
    Reset-Permissions $false
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#manualWithoutPermissionSurvivesActivityRecreation' 'device-manual-write.log'
    Invoke-Adb @('shell','am','force-stop',$package)
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#manualReopensAfterForceStop' 'device-manual-restart.log'
    Invoke-Adb @('shell','appops','set','com.android.shell','android:mock_location','allow')
    Invoke-Adb @('shell','cmd','location','providers','add-test-provider','gps'); $gps=$true
    Invoke-Adb @('shell','cmd','location','providers','set-test-provider-enabled','gps','true')
    Invoke-Adb @('shell','cmd','location','providers','add-test-provider','fused'); $fused=$true
    Invoke-Adb @('shell','cmd','location','providers','set-test-provider-enabled','fused','true')
    Reset-Permissions $false
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#preciseDialogStoresMockFixAndManualCorrection' 'device-permission-precise.log'
    Invoke-Adb @('shell','am','force-stop',$package)
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#fixReopensAndManualCorrectionRemovesMeasuredAccuracy' 'device-fix-restart.log'
    Reset-Permissions $false
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#approximateDialogStoresMockFix' 'device-permission-approximate.log'
    Reset-Permissions $true
    Invoke-Adb @('shell','cmd','location','set-location-enabled','false')
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#locationOffAndManualFallbackKeepInterviewUsable' 'device-location-off.log'
    Invoke-Adb @('shell','cmd','location','set-location-enabled','true')
    Invoke-Test 'ph.tala.registry.ui.LocationDeviceTest#timeoutRejectsCachedFixAndInterviewStillSaves' 'device-location-timeout.log'
    }
    Invoke-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#fullInterviewThroughAllSectionsWithTwoMembers' 'device-full-interview.log'
    Invoke-Adb @('shell','am','force-stop',$package)
    Invoke-Test 'ph.tala.registry.ui.FullCoverageDeviceTest#verifyFullInterviewAfterForceStop' 'device-full-interview-restart.log'
    if(-not $RegressionOnly) { foreach($name in @('permission-denied','manual-saved','manual-reopened','precise-mock-fix','corrected-device-fix','approximate-mock-fix','location-off','location-timeout')) {
        Invoke-Adb @('pull',"/sdcard/Android/data/$package/files/day15/$name.png",(Join-Path $evidence "$name.png"))
    } }
    if($RegressionOnly -and $ManualFallbacks) { foreach($name in @('manual-saved','manual-reopened','location-off')) {
        Invoke-Adb @('pull',"/sdcard/Android/data/$package/files/day15/$name.png",(Join-Path $evidence "$name.png"))
    } }
    if((Fingerprints) -ne $before) { throw 'Source changed during acceptance.' }
    $prefix=if($RegressionOnly) { 'regression-' } else { '' }
    $before | Set-Content -Encoding UTF8 (Join-Path $evidence "${prefix}tested-source-sha256.json")
    @("Verified UTC: $([DateTime]::UtcNow.ToString('o'))","Scope: $(if($RegressionOnly) { 'Regression only; permission acceptance pending' } else { 'Full D15 acceptance' })","Package: $package","Device: $DeviceSerial","Model: $(Invoke-Adb @('shell','getprop','ro.product.model'))","Android: $(Invoke-Adb @('shell','getprop','ro.build.version.release'))","API: $(Invoke-Adb @('shell','getprop','ro.build.version.sdk'))","Version: $($metadata.elements[0].versionName) ($($metadata.elements[0].versionCode))","App SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk')).Hash)","Test SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).Hash)") | Set-Content -Encoding UTF8 (Join-Path $evidence "${prefix}device-build.txt")
} finally {
    # Attempt every restoration even if a disconnected device or vendor restriction
    # rejects one command. A partial cleanup must never be reported as acceptance.
    $restoreErrors = New-Object System.Collections.Generic.List[string]
    function Restore-Adb([string[]]$Arguments) {
        try { Invoke-Adb $Arguments | Out-Null } catch { $restoreErrors.Add($_.Exception.Message + ': ' + ($Arguments -join ' ')) }
    }
    if($gps) { Restore-Adb @('shell','cmd','location','providers','remove-test-provider','gps') }
    if($fused) { Restore-Adb @('shell','cmd','location','providers','remove-test-provider','fused') }
    if(-not $RegressionOnly -or $ManualFallbacks) { Restore-Adb @('shell','cmd','location','set-location-enabled',$locationEnabled) }
    Restore-Adb @('shell','am','force-stop',$package)
    if(-not $RegressionOnly) {
    Restore-Adb @('shell','appops','set','com.android.shell','android:mock_location',$mockMode)
    foreach($p in $permissions) {
        Restore-Adb @('shell','pm','revoke',$package,$p.permission)
        Restore-Adb @('shell','pm','clear-permission-flags',$package,$p.permission,'user-set','user-fixed')
    }
    foreach($p in ($permissions | Sort-Object permission)) {
        if($p.granted) { Restore-Adb @('shell','pm','grant',$package,$p.permission) }
        if($p.userSet) { Restore-Adb @('shell','pm','set-permission-flags',$package,$p.permission,'user-set') }
        if($p.userFixed) { Restore-Adb @('shell','pm','set-permission-flags',$package,$p.permission,'user-fixed') }
    }
    }
    Restore-Adb @('shell','settings','put','global','stay_on_while_plugged_in',$stay)
    Restore-Adb @('shell','settings','put','system','screen_off_timeout',$screen)
    if($ime -eq 'null') { Restore-Adb @('shell','settings','delete','secure','show_ime_with_hard_keyboard') }
    else { Restore-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard',$ime) }
    ConvertTo-Json -InputObject @($restoreErrors.ToArray()) | Set-Content -Encoding UTF8 (Join-Path $evidence 'restoration-errors.json')
    if($restoreErrors.Count) { throw ('Device restoration incomplete: ' + ($restoreErrors -join '; ')) }
}
if($RegressionOnly) { Write-Output 'D15 regression subset passed; device settings restored. Permission/location acceptance remains pending.' }
else { Write-Output 'D15 location and regression acceptance passed; device settings restored. Coordinates supplied by controlled mock providers, not live GPS.' }
