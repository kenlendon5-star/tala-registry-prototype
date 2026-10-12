$ErrorActionPreference = 'Stop'
& rtk proxy adb shell am instrument -w -r -e class 'ph.tala.registry.data.local.DurableSaveTest#test1_seedSavedRecord' ph.tala.registry.audit.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'durability-seed.txt')
& rtk proxy adb shell am force-stop ph.tala.registry.audit
& rtk proxy adb shell am instrument -w -r -e class 'ph.tala.registry.data.local.DurableSaveTest#test2_savedRecordSurvivesProcessRestartAndReboot' ph.tala.registry.audit.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'durability-after-force-stop.txt')
