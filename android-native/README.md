# Tala native Android

The native app includes the Day 3 shell, Day 4 shared form controls and the Day 5 Room storage layer. Open a household, then **Try a practice interview**. The practice form supports text, notes, phone, integer, date, date/time, choice, dropdown and multi-select controls, including conditional questions and inline errors. Switch **Answering for** between the household and its members to keep separate session drafts.

**Check answers** validates the visible questions and scrolls/focuses the first error. It does not save an interview: session drafts survive activity recreation and navigation in the running app, but closing the process clears them. Room storage for households, members, visits, scoped answers, coordinates and attachment references exists and is verified by device tests; connecting it to the editing UI is Day 6. Photos and signatures are later cards.

## Build and run

Open this directory in Android Studio. Install Android SDK platform 37 and use the project's pinned Gradle/AGP versions. The current build uses Java 17. Set `sdk.dir` in your local, ignored `local.properties`, or configure `ANDROID_HOME`.

On Windows, escape the drive colon in the properties file, for example `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`. The checked-in Gradle settings use one worker and in-process Kotlin compilation to keep memory use manageable on an 8 GB development machine.

From this directory in PowerShell:

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug
rtk proxy ./gradlew.bat :app:connectedDebugAndroidTest
rtk proxy adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first build needs network access to download dependencies. The installed app has no Internet permission and needs neither a development server nor an internet connection. Device tests require an unlocked, authorized Android device with a working software keyboard. They use fictional fixtures and write screenshots into the app's external files directory under `day03/` and `day04/`.

Gradle's connected-test runner may uninstall the test apps afterward, removing their screenshots. For evidence collection, install both APKs, run `rtk proxy adb shell am instrument -w -r -e class ph.tala.registry.ui.FormDeviceTest ph.tala.registry.test/androidx.test.runner.AndroidJUnitRunner`, then pull `/sdcard/Android/data/ph.tala.registry/files/day04` before uninstalling. The test APK is `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## State ownership

- `domain/model`: typed household/member identities, immutable record and questionnaire models.
- `data/HouseholdRepository`: observable read contract and explicit demo implementation; the UI keeps reading these fixtures until Day 6 swaps in the Room-backed repository.
- `data/local`: Room entities with UUID identities and foreign keys, `RegistryDao`, `RegistryDatabase` (schema export to `app/schemas/`, no destructive migration) and `RoomRecordStore`, which saves a whole record snapshot in one transaction and reports `Saved` or `Failed` without leaking database details.
- `ui/RegistryViewModel`: household list/search/filter state. Small UI inputs use `SavedStateHandle`.
- `ui/InterviewViewModel`: observes one typed household ID, with a separately saved interview tab per navigation entry.
- `ui/Routes`: typed destinations and a `NavType` that stores only the household ID string in the navigation Bundle.
- `ui/TalaApp`: Compose navigation and screens; lifecycle-aware state collection and system/keyboard insets.
- `domain/form/FormRules`: pure validation, strict calendar parsing, exclusive multi-select and fixed-point clearing of hidden dependencies.
- `domain/form/PracticeSection`: representative fields copied from the prototype specification, combined for control acceptance rather than presented as an official section.
- `ui/form/SectionForm`: reusable stateless renderer and date/time pickers; only a deliberate Check action requests first-error focus.
- `ui/form/FormSessionViewModel`: separate immutable drafts keyed by household/member identity; validation state is also scoped by section.

Navigation restores the back stack after activity/system recreation; saved state restores search, filters and interview tab. A deliberate force-stop/fresh launch starts at Home. This is UI-state restoration, not durable interview storage. Demo data is reconstructed from bundled fixtures after process death.

Missing records show an explicit unavailable state. Screens pass IDs to repositories rather than sharing record objects or relying on list indices. No fake save action or completed-questionnaire percentage is shown.

## Checks and evidence

- JVM: filtering, restored inputs, repository updates, typed-ID validation, identity across reorder/removal, required/optional inputs, numeric/date/phone limits, conditional clearing, exclusive answers and household/member draft isolation.
- Device: navigation, keyboard visibility/resize, Android Back, activity recreation, selected household/tab, empty-state recovery, system bars, absence of Internet permission, all shared form controls, first-error focus, pickers and draft isolation across scope changes.
- Device storage: snapshot save and reopen, per-member answer scoping, cleared-answer replacement, transaction rollback, failed-write reporting, export/migration validation and force-stop/reboot durability.
- [Day 3 acceptance record](../planning/evidence/D03.md).
- [Day 4 acceptance record](../planning/evidence/D04.md).
- [Day 5 acceptance record](../planning/evidence/D05.md).

Implementation follows Android's [saved-state guidance](https://developer.android.com/develop/ui/compose/state-saving) and [edge-to-edge setup](https://developer.android.com/develop/ui/compose/system/setup-e2e).
