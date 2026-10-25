# Tala native Android

The native app includes the shell, shared form controls, Room storage, household/member editing, structured registration, profile/pictures and live counts. All 18 section definitions are generated in `QuestionnaireSections`. D09 identity, D10 education/employment and D11 household services have recorded acceptance; D12 disaster/protection and the foundation audit fixes are verified separately in [D12 evidence](../planning/03-evidence/D12.md). D13 adds dated visit history, callbacks, refusal, local interview review and drawn/typed signatures; its current verification status is in [D13 evidence](../planning/03-evidence/D13.md). D14 now has owner-authorized Codex AI peer acceptance; human usability/UAT remains a later gate. **Try a practice interview** remains a separate session-only demonstration.

Cloud accounts are optional: Reports and Profile expose Supabase email/password sign-in, account creation, confirmation resend, and sign-out while Room remains available offline. Email confirmation returns through `tala://auth-callback`; only the publishable project key is embedded, and RLS remains the authorization boundary.

Household add/edit and member add/edit screens save through `RoomHouseholdRepository` into the app-private Room database (version 5). Saving, saved and failed states are explicit and a failed write is never reported as saved; editor drafts and generated UUID identities survive process death through `SavedStateHandle`. The interview workspace saves each section through `saveDraftSection` with a compare-with-base check, so a stale screen cannot overwrite answers saved by another view, and `reviewInterview` grants the Reviewed status only after every section, member form and consent question passes validation. Fictional fixtures (`HH-00452`–`HH-00454`) seed once per installed database, are skipped when already present, and never overwrite user edits. Archived households leave active counts and return through the **Archived** filter. Household/enumerator pictures use Android Photo Picker and app-private normalized JPEG copies; profile settings use DataStore, and new households retain the profile attribution captured at creation. Signatures use app-private PNG attachments; refusal removes the mark and blocks Reviewed. The explicit Record visit result action appends a dated history entry; draft autosaves do not add visits. Migrations 1→2→3→4→5 preserve existing records; v5 adds the durable cloud outbox and owner-scoped revision tracking, while unknown legacy visit dates remain blank.

## Build and run

D15 adds foreground location capture and validated manual coordinates on each
household's Overview. Coarse/fine permission is requested when Capture device
location is chosen. Denial, unavailable location and timeout leave the questionnaire
usable. Manual drafts restore through SavedStateHandle; device fixes retain measured
accuracy/time, while manual entries have no measured accuracy and use entry time.
Storage reuses the current Room schema. See [D15 evidence](../planning/03-evidence/D15.md) for current
verification and device limitations. Map rendering remains the D16 package.

Open this directory in Android Studio. Install Android SDK platform 37 and use the project's pinned Gradle/AGP versions. The current build uses Java 17. Set `sdk.dir` in your local, ignored `local.properties`, or configure `ANDROID_HOME`.

On Windows, escape the drive colon in the properties file, for example `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`. The checked-in Gradle settings use one worker and in-process Kotlin compilation to keep memory use manageable on an 8 GB development machine.

From this directory in PowerShell:

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug
rtk proxy adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first build needs network access to download dependencies. The installed app needs no development server and keeps Room workflows usable offline; Internet access is limited to map tiles and explicit Supabase account/sync actions. Device tests require an unlocked, authorized Android device with a working software keyboard. Use the isolated harness below: some restart probes must run in separate instrumentation invocations with a force-stop between them, so a blanket connected-test run is not the acceptance procedure.

The harness installs the separate `ph.tala.registry.d08test` package and test APK, runs focused classes, and pulls evidence before uninstalling. It preserves normal-app records and media. The test APK is `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## State ownership

- `domain/model`: typed household/member identities, immutable record and questionnaire models.
- `data/HouseholdRepository`: observable read contract plus `RoomHouseholdRepository` — one mutex-serialised write stream over full-snapshot `RecordStore` saves, UUID identities with unique `HH-#####` display labels, reversible archiving, section draft saves with optimistic base comparison, visit-result status derivation, interview review gating and explicit optional fictional fixtures.
- `ui/households`: `HouseholdEditViewModel`/`MemberEditViewModel` with `SavedStateHandle` drafts and generated identities, plus `HouseholdEditScreen`/`MemberEditScreen` with explicit `Idle/Saving/Saved/Failed` states and archive/remove confirmations.
- `data/local`: Room entities with UUID identities and foreign keys, `RegistryDao`, `RegistryDatabase` (schema export to `app/schemas/`, no destructive migration) and `RoomRecordStore`, which saves a whole record snapshot in one transaction and reports `Saved` or `Failed` without leaking database details.
- `ui/RegistryViewModel`: household list/search/filter state. Small UI inputs use `SavedStateHandle`.
- `data/PhotoStore`: bounded image decoding/normalization on the IO dispatcher, reference-first replacement/removal, staged-file failure cleanup and orphan recovery; saved copies have previews.
- `data/DataStoreEnumeratorProfileStore` and `ui/profile`: durable profile name/barangay/picture, explicit write results and saved acknowledgements cleared after editing.
- `ui/InterviewViewModel`: observes one typed household ID, with a separately saved interview tab per navigation entry.
- `ui/Routes`: typed destinations and a `NavType` that stores only the household ID string in the navigation Bundle.
- `ui/TalaApp`: Compose navigation and screens; lifecycle-aware state collection and system/keyboard insets.
- `domain/form/FormRules`: pure validation, strict calendar parsing, exclusive multi-select and fixed-point clearing of hidden dependencies.
- `domain/form/PracticeSection`: representative fields copied from the prototype specification, combined for control acceptance rather than presented as an official section.
- `ui/form/SectionForm`: reusable stateless renderer and date/time pickers; only a deliberate Check action requests first-error focus.
- `ui/form/FormSessionViewModel`: separate immutable drafts keyed by household/member identity; validation state is also scoped by section.
- `domain/form/QuestionnaireSections`: all 18 sections generated from `../planning/docs/schemas/questionnaire-current.json` by `tools/port-native-questionnaire.cjs`; demo labels and scope are retained, and member-scoped sections are flagged so answers key to one member.
- `domain/form/InterviewRules`: reads one section's answers from a stored record, cleans hidden dependents to a fixed point, and computes review issues across household and member scopes (consent is required for Reviewed).
- `ui/interview/InterviewWorkspaceViewModel`: the questionnaire workspace — one section/member scope at a time, debounced auto-save, save-before-switch, explicit save and review, and conflict reporting when the stored section changed elsewhere.

Navigation restores the back stack after activity/system recreation; saved state restores search, filters, interview tab and workspace section/member selection. The workspace saves its pending answers and original comparison base as Bundle-compatible values, so a restored unsaved draft remains retryable and cannot bypass conflict refusal. Switching scope or leaving drains edits that arrive during an awaited write. Questionnaire sections save to Room after a 500 ms debounce and on demand; the practice form remains session-only. Reviewed status is granted only through review; changing reviewed household details reopens the record for review.

Missing records show an explicit unavailable state. Screens pass IDs to repositories rather than sharing record objects or relying on list indices. No fake save action or completed-questionnaire percentage is shown.

## Checks and evidence

- JVM: filtering, restored inputs, repository updates, typed-ID validation, identity across reorder/removal, required/optional inputs, numeric/date/phone limits, conditional clearing, exclusive answers, household/member draft isolation, create/update/archive/remove flows, stable generated identities, failed-write reporting, idempotent fixture seeding, questionnaire section port, draft-section merge with conflict refusal, review gating, archived-count exclusion and saved-ack clearing.
- Device: navigation, keyboard visibility/resize, Android Back, activity recreation, selected household/tab, empty-state recovery, system bars, absence of Internet permission, all shared form controls, first-error focus, pickers, draft isolation across scope changes, household/member add/edit saves, member removal, archive/restore round trip and editing persistence across reopen.
- Device storage: snapshot save and reopen, per-member answer scoping, cleared-answer replacement, transaction rollback, failed-write reporting, export/migration validation (v1→v2 with `archivedAt`) and force-stop/reboot durability.
- [Day 3 acceptance record](../planning/03-evidence/D03.md).
- [Day 4 acceptance record](../planning/03-evidence/D04.md).
- [Day 5 acceptance record](../planning/03-evidence/D05.md).
- [Day 6 acceptance record](../planning/03-evidence/D06.md).
- [Day 8 acceptance record](../planning/03-evidence/D08.md) records registration, media/profile persistence, counts and the current acceptance status.

Day 8 device acceptance uses a separate application ID to preserve the normal app's saved records and media:

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest -PisolatedTestApp=true
```

Then, from the repository root, run `rtk proxy powershell -NoProfile -File tools/verify-d08-device.ps1`. Results are retained under `planning/03-evidence/day08/`. Build again without `-PisolatedTestApp=true` to produce the normal `ph.tala.registry` APK.

Implementation follows Android's [saved-state guidance](https://developer.android.com/develop/ui/compose/state-saving) and [edge-to-edge setup](https://developer.android.com/develop/ui/compose/system/setup-e2e).

- [Day 9 acceptance record](../planning/03-evidence/D09.md): member identity validation, branch clearing, per-member Room saves and actual questionnaire recovery after force-stop. Run `rtk proxy powershell -NoProfile -File tools/verify-d09-device.ps1` from the root after building with `-PisolatedTestApp=true`.

Current foundation/questionnaire regression (D01–D12), from this directory:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedTestApp=true
```

Then from the repository root, substituting the serial from `rtk proxy adb devices -l`:

```powershell
rtk proxy powershell -NoProfile -File tools/verify-d12-device.ps1 -DeviceSerial emulator-5554
```

The [D12 evidence](../planning/03-evidence/D12.md) includes APK/source hashes, the controlled Bundle/Parcel workspace-restoration test, native education/employment/disaster/protection branch checks, confirmed-save force-stop recovery and baseline storage/media/UI regression. Build again without `-PisolatedTestApp=true` to produce the normal application APK.
