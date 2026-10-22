# D16 device acceptance: household map online/offline, pin adjustment, permission
# invariant and coordinate regression on the selected Android device.
# Requires the isolated APKs (rtk proxy .\gradlew.bat :app:testDebugUnitTest
#   :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true)
# and an authorized device. Evidence: planning/03-evidence/day16/.
param([string]$DeviceSerial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$package = 'ph.tala.registry.d08test'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$evidence = Join-Path $workspace 'planning/03-evidence/day16'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$metadata = Get-Content (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) { throw 'Build the isolated APKs first (rtk proxy .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true).' }
$roots = @('android-native/app/src','android-native/app/schemas','android-native/app/build.gradle.kts','android-native/gradle/libs.versions.toml','planning/docs/schemas/questionnaire-current.json','tools/port-native-questionnaire.cjs','tools/verify-d16-device.ps1')
function Fingerprints {
    @($roots | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $workspace $_) -File -Recurse } | Sort-Object FullName | ForEach-Object {
        [ordered]@{ path=$_.FullName.Substring($workspace.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName).Hash }
    }) | ConvertTo-Json -Depth 4
}
$before=Fingerprints
# Shared adb/instrumentation machinery comes from the Day-14 harness so every day
# records evidence in the same shape.
$harness=Get-Content -LiteralPath (Join-Path $workspace 'tools/verify-d14-device.ps1') -Raw
$start=$harness.IndexOf('function Invoke-Adb'); $end=$harness.IndexOf('$stayAwake =')
Invoke-Expression $harness.Substring($start,$end-$start)
& rtk proxy node (Join-Path $workspace 'tools/port-native-questionnaire.cjs') --check
if($LASTEXITCODE -ne 0) { throw 'Questionnaire generation differs.' }
Invoke-Adb @('install','-r',(Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('install','-r',(Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
$airplaneBefore=([string](Invoke-Adb @('shell','settings','get','global','airplane_mode_on'))).Trim()
$stay=([string](Invoke-Adb @('shell','settings','get','global','stay_on_while_plugged_in'))).Trim()
$screen=([string](Invoke-Adb @('shell','settings','get','system','screen_off_timeout'))).Trim()
$ime=([string](Invoke-Adb @('shell','settings','get','secure','show_ime_with_hard_keyboard'))).Trim()
@{ airplaneModeOn=$airplaneBefore; stayAwake=$stay; timeout=$screen; keyboard=$ime } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $evidence 'environment-before.json')
try {
    Invoke-Adb @('shell','svc','power','stayon','usb')
    Invoke-Adb @('shell','settings','put','system','screen_off_timeout','1800000')
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP')
    Invoke-Adb @('shell','wm','dismiss-keyguard')
    Invoke-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
    # The permission invariant runs first: the D16 allow-list replaces the old no-network rule.
    Invoke-Test 'ph.tala.registry.ui.ShellDeviceTest' 'device-shell-permissions.log'
    Invoke-Test 'ph.tala.registry.data.local.CoordinatePersistenceDeviceTest' 'device-coordinate-regression.log'
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#mapListsPinsFlagsOutsidersAndOpensRecords' 'device-map-online.log'
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#adjustPinSavesManualProvenanceAndSurvivesRecreation' 'device-map-adjust.log'
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#outsideExtentSavesAreRejectedOnDeviceAndKeepTheDraft' 'device-map-extent.log'
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#untaggedHouseholdsStayReachableFromTheMap' 'device-map-untagged.log'
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#externalMapHandOffResolvesWithCoordinatesOnly' 'device-map-external.log'
    # Airplane mode last: the test enables it, verifies the offline plot and restores.
    Invoke-Test 'ph.tala.registry.ui.MapDeviceTest#airplaneModeKeepsCoordinatesAccessibleOnTheOfflinePlot' 'device-map-airplane.log'
    foreach($name in @('01-map-selection-out-of-area','02-map-open-record','03-adjust-draft','04-adjust-saved','05-airplane-offline-plot','06-adjust-outside-extent-rejected','07-untagged-open-record')) {
        Invoke-Adb @('pull',"/sdcard/Android/data/$package/files/day16/$name.png",(Join-Path $evidence "$name.png"))
    }
    if((Fingerprints) -ne $before) { throw 'Source changed during acceptance.' }
    $before | Set-Content -Encoding UTF8 (Join-Path $evidence 'tested-source-sha256.json')
    @("Verified UTC: $([DateTime]::UtcNow.ToString('o'))","Scope: Full D16 map acceptance","Package: $package","Device: $DeviceSerial","Model: $(Invoke-Adb @('shell','getprop','ro.product.model'))","Android: $(Invoke-Adb @('shell','getprop','ro.build.version.release'))","API: $(Invoke-Adb @('shell','getprop','ro.build.version.sdk'))","Version: $($metadata.elements[0].versionName) ($($metadata.elements[0].versionCode))","App SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/debug/app-debug.apk')).Hash)","Test SHA256: $((Get-FileHash (Join-Path $workspace 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).Hash)") | Set-Content -Encoding UTF8 (Join-Path $evidence 'device-build.txt')
} finally {
    $restoreErrors = New-Object System.Collections.Generic.List[string]
    function Restore-Adb([string[]]$Arguments) {
        try { Invoke-Adb $Arguments | Out-Null } catch { $restoreErrors.Add($_.Exception.Message + ': ' + ($Arguments -join ' ')) }
    }
    # The airplane toggle is the only device setting D16 changes; the test also
    # restores it, and the harness rejects acceptance if the final state differs.
    $airplaneAfter=([string](Invoke-Adb @('shell','settings','get','global','airplane_mode_on'))).Trim()
    if($airplaneAfter -ne $airplaneBefore) {
        Restore-Adb @('shell','cmd','connectivity','airplane-mode',$(if($airplaneBefore -eq '1'){'enable'}else{'disable'}))
        $airplaneFinal=([string](Invoke-Adb @('shell','settings','get','global','airplane_mode_on'))).Trim()
        if($airplaneFinal -ne $airplaneBefore) { $restoreErrors.Add("airplane_mode_on=$airplaneFinal, expected $airplaneBefore") }
    }
    Restore-Adb @('shell','am','force-stop',$package)
    Restore-Adb @('shell','settings','put','global','stay_on_while_plugged_in',$stay)
    Restore-Adb @('shell','settings','put','system','screen_off_timeout',$screen)
    if($ime -eq 'null') { Restore-Adb @('shell','settings','delete','secure','show_ime_with_hard_keyboard') }
    else { Restore-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard',$ime) }
    ConvertTo-Json -InputObject @($restoreErrors.ToArray()) | Set-Content -Encoding UTF8 (Join-Path $evidence 'restoration-errors.json')
    if($restoreErrors.Count) { throw ('Device restoration incomplete: ' + ($restoreErrors -join '; ')) }
}
Write-Output 'D16 map acceptance passed: online basemap, offline fallback, pin adjustment and permission invariant verified; device settings restored.'
