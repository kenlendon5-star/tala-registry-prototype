# Tala native Android

Day 3 provides an offline, read-only Compose shell with fictional records: Home → searchable household list → household overview, members and section preview. It does not save interviews yet. Shared controls are Day 4; Room and editing are Days 5–6.

## Build and run

Open this directory in Android Studio. Install Android SDK platform 37 and use the project's pinned Gradle/AGP versions. The current build uses Java 17. Set `sdk.dir` in your local, ignored `local.properties`, or configure `ANDROID_HOME`.

From this directory in PowerShell:

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug
rtk proxy ./gradlew.bat :app:connectedDebugAndroidTest
rtk proxy adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first build needs network access to download dependencies. The installed shell has no Internet permission and needs neither a development server nor an internet connection. Device tests require an unlocked, authorized Android device with a working software keyboard. They use fictional fixtures and write screenshots into the app's external files directory under `day03/`. The navigation recording is `/data/local/tmp/tala-day03-navigation.mp4` (shell-owned storage, required on some OEMs).

Gradle's connected-test runner may uninstall the test apps afterward, removing their screenshots. For evidence collection, install both APKs, run `adb shell am instrument -w -r -e class ph.tala.registry.ui.ShellDeviceTest#navigationKeyboardBackAndRecreation ph.tala.registry.test/androidx.test.runner.AndroidJUnitRunner`, then pull the screenshots and video before uninstalling. The test APK is `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## State ownership

- `domain/model`: typed household/member identities, immutable record and questionnaire models.
- `data/HouseholdRepository`: observable read contract and explicit demo implementation; replace it with Room in Day 5.
- `ui/RegistryViewModel`: household list/search/filter state. Small UI inputs use `SavedStateHandle`.
- `ui/InterviewViewModel`: observes one typed household ID, with a separately saved interview tab per navigation entry.
- `ui/Routes`: typed destinations and a `NavType` that stores only the household ID string in the navigation Bundle.
- `ui/TalaApp`: Compose navigation and screens; lifecycle-aware state collection and system/keyboard insets.

Navigation restores the back stack after activity/system recreation; saved state restores search, filters and interview tab. A deliberate force-stop/fresh launch starts at Home. This is UI-state restoration, not durable interview storage. Demo data is reconstructed from bundled fixtures after process death.

Missing records show an explicit unavailable state. Screens pass IDs to repositories rather than sharing record objects or relying on list indices. No fake save action or completed-questionnaire percentage is shown.

## Checks and evidence

- JVM: filtering, restored inputs, repository updates, typed-ID validation, identity across reorder/removal.
- Device: navigation, keyboard visibility/resize, Android Back, activity recreation, selected household/tab, empty-state recovery, system bars and absence of Internet permission.
- [Day 3 acceptance record](../planning/evidence/D03.md).

Implementation follows Android's [saved-state guidance](https://developer.android.com/develop/ui/compose/state-saving) and [edge-to-edge setup](https://developer.android.com/develop/ui/compose/system/setup-e2e).
