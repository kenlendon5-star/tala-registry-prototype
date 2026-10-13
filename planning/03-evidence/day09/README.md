# Day 9 verification artifacts

This directory holds the build logs, JVM XML results, device logs, screenshots and source/APK identities for the Day 9 acceptance run. The final outcome and member identity matrix are recorded in [D09](../D09.md).

Reproduce from `android-native`:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true --console=plain
```

Then from the repository root:

```powershell
rtk proxy powershell -NoProfile -File tools/verify-d09-device.ps1
rtk proxy node tools/port-native-questionnaire.cjs --check
rtk proxy node planning/02-progress/build-board.cjs
rtk proxy node planning/02-progress/verify-board.cjs
rtk proxy node tools/check-docs.cjs
```

The device script checks the isolated package metadata before installing. It runs the Room identity test, writes two fictional members through the actual questionnaire, force-stops the test package, verifies the saved values through Room and the UI, and runs adjacent storage/UI regression. It never clears or uninstalls the normal application. `-DeviceSerial` selects another authorized device; `-SkipRegression` is for a targeted rerun after a device-test failure.

`build-initial.log` is the first build after repairing the source. Subsequent build/device logs and retained `prior-failed-*` logs distinguish intermediate failures from the final run. Use `device-build.txt`, `normal-apk.json` and `tested-source-sha256.json` to identify the verified artifacts; version number alone does not identify an APK.
