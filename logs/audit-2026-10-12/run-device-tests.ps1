$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$appApk = Join-Path $projectRoot 'android-native/app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $projectRoot 'android-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$aapt = 'C:/Users/kenle/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe'
$appBadging = (& rtk proxy $aapt dump badging $appApk) -join "`n"
$testBadging = (& rtk proxy $aapt dump badging $testApk) -join "`n"
if ($appBadging -notmatch "package: name='ph.tala.registry.audit'") { throw 'Refusing to install an APK outside the audit package' }
if ($testBadging -notmatch "package: name='ph.tala.registry.audit.test'") { throw 'Refusing to install test APK outside the audit package' }
$jvmOutput = Join-Path $projectRoot 'android-native/app/build/test-results/testDebugUnitTest'
New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'baseline-jvm') -Force | Out-Null
Get-ChildItem -LiteralPath $jvmOutput -Filter '*.xml' -File | Copy-Item -Destination (Join-Path $PSScriptRoot 'baseline-jvm')
Copy-Item -LiteralPath $appApk -Destination (Join-Path $PSScriptRoot 'app-audit.apk')
Copy-Item -LiteralPath $testApk -Destination (Join-Path $PSScriptRoot 'tests-audit.apk')
Get-FileHash -LiteralPath $appApk,$testApk -Algorithm SHA256 | Format-Table -AutoSize
& rtk proxy adb install -r $appApk
if ($LASTEXITCODE -ne 0) { throw 'Audit application install failed' }
& rtk proxy adb install -r $testApk
if ($LASTEXITCODE -ne 0) { throw 'Audit test install failed' }
$classes = 'ph.tala.registry.ui.ShellDeviceTest,ph.tala.registry.ui.FormDeviceTest,ph.tala.registry.ui.EditingDeviceTest,ph.tala.registry.data.local.RecordStoreDeviceTest,ph.tala.registry.data.local.EditingPersistenceDeviceTest,ph.tala.registry.data.local.SchemaMigrationTest'
& rtk proxy adb shell am instrument -w -r -e class $classes ph.tala.registry.audit.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'device-suite.txt')
