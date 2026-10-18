# Tala system and D01–D12 review — 17 October 2026

Follow-up: the [D01–D12 repair record](D01-D12-FIXES-2026-10-17.md) tracks fixes and points to the final verification evidence. Findings below describe the audit snapshot and are retained as history.

The native foundation and questionnaire implementation are substantial and largely consistent with the planned offline demo. D01–D11 have recorded completion evidence, with an explicitly deferred D02 device requirement. D12 is implemented but has not completed its acceptance/evidence workflow. The complete interview lifecycle and the release product remain unfinished.

The board contains four weekly sprints and 30 daily work packages. This review interprets “sprints 1–12” as D01–D12. It assesses the current working tree, including the pre-existing untracked D12 tests, coverage document and verification script. It does not change application code or board status.

**Concurrent-work qualification:** during the audit, additional changes appeared in the signature/review code, a D13 harness and the test dependency catalog. Those edits were made outside this review and were left untouched. The verdicts below describe the inspected/tested snapshot; newly appearing D13 implementation and the dependency upgrade are not accepted by these test results. In particular, the catalog now proposes Espresso 3.7.0 / AndroidX test JUnit 1.3.0, while the failed UI APK used the earlier test stack. Validate the proposed upgrade with a rebuilt test APK before closing the failure.

## Scope and system assessment

| Area | Current assessment |
| --- | --- |
| Native Android | Active release target: Kotlin, Compose, one application module, Room v2, DataStore profile and app-private media. Debug version 0.5.0 (5). |
| Browser prototype | Behavior reference with 18 sections / 94 fields. Root npm commands use the actual `planning/prototype as web app` directory. Several README examples still refer to the absent `browser/` directory. |
| Expo starter | Retained separate project; outside the native release. No reason to resume parallel feature development here. |
| Backend tooling | Not connected to the Android application. Native dependencies and manifest support the local-only implementation; cloud work does not complete any of the current offline acceptance gates. |
| Planning | Generated HTML and Markdown match the progress JSON. Mechanical consistency passes, but some notes and prose describe obsolete implementation states. |
| Release readiness | Open: D13 interview lifecycle/signatures, D14 peer review, GPS/map, export/restore, broader lifecycle/accessibility/performance, UAT and signed release. Archive/restore and migration groundwork already exist. |

The strongest parts of the implementation are stable UUID identities, ID-only navigation, explicit write results, serialized repository writes, atomic snapshot replacement, exported Room schemas and a non-destructive v1→v2 migration. Branch clearing runs to a fixed point; member answers have composite relational constraints; section saves compare their original answers with durable answers before writing. Photos are bounded, normalized off the UI thread, referenced durably before replacement cleanup, and recovered through orphan cleanup. These are useful foundations to retain.

The architecture is practical for the capstone, but the layer boundary is imperfect: `domain/form/InterviewRules.kt` imports `data.local.StoredRecord` and `AnswerCodec`, while the repository calls `InterviewRules`. It is pure Kotlin in the sense of not calling Android APIs, but the domain depends on persistence types. A small domain interview snapshot plus mapping at the repository boundary would remove that coupling. This is lower priority than save/lifecycle correctness.

## Work-package verdicts

“Supported” means the implementation and recorded evidence support the package's intended slice. It does not mean every current-device flow was reaccepted on the target phone today.

| Card | Board | Review verdict | Basis / reservation |
| --- | --- | --- | --- |
| D01 Scope | Done | Supported, documentation qualification | Scope, capacity, exclusions, finish lines and evidence exist. A complete requirement→feature→test matrix is still weaker than the plan requests; the new 18-section matrix is section coverage, not full product traceability. |
| D02 Toolchain | Done | Accepted with a deferred criterion | Wrapper, versions, memory limits and builds exist. D02 explicitly deferred its second configuration. An API 37 emulator now exists, but its UI tests fail, so that requirement is not yet closed. |
| D03 Shell | Done | Supported | Native navigation, list/filter, typed IDs, state ownership and historical shell device evidence exist. Current emulator UI acceptance is blocked by the test stack. |
| D04 Form engine | Done | Supported | Stateless shared controls, strict validation, first-error machinery, branch clearing and scoped practice drafts have tests. Practice-session drafts are intentionally distinct from durable questionnaire answers. |
| D05 Storage | Done | Supported | Atomic Room saves, explicit failures, foreign keys, schema export and reopen/rollback tests exist. Current storage regression passes. |
| D06 Editing | Done | Supported with defects to fix | Household/member CRUD, restored editor drafts, stable create identities and reversible archive exist. Reviewed-household editing and inconsistent field limits need correction below. |
| D07 Foundation gate | Done | Historically supported | Saved-record restart evidence exists. Its report still describes Room v1 and keyguard methods absent from current `MainActivity`; refresh current-state prose without rewriting historical results. |
| D08 Registration | Done | Supported | Structured names, profile attribution, real photo handling, counts and recorded picker/restart tests exist. Workspace lifecycle protection is weaker than the household/member editors. |
| D09 Identity | Done | Supported | Three member sections, nested branch clearing, isolation and saved-answer force-stop evidence exist. Current demographics Room regression passes. |
| D10 Education/work | Done | Implementation supported; integration evidence can improve | Generated forms match the prototype, and positive/negative domain tests plus two-member Room reopen tests exist. Dedicated education/work UI branch reversal and process restart evidence is thinner than the card's user-flow finish line; generic UI/storage tests are not the same scenario. |
| D11 Household services | Done | Supported | Six household sections, validation/branch/exclusivity/scoping tests and two Room persistence tests exist. Current storage regression passes. |
| D12 Disaster/protection | Backlog | Implemented; acceptance incomplete | Both forms, 23 new JVM tests, two device persistence tests and the 18-section matrix exist. Fresh persistence tests pass, but the existing D12 UI log crashed, the evidence table still says pending, build identity/pulled summaries are incomplete, and the board note incorrectly says the forms do not exist. Move to Verify while resolving these items. |

## Findings and recommended fixes

### 1. High: UI acceptance cannot run on the available second configuration

Fresh shell/form/editing instrumentation on Android 17 / API 37 failed **10 of 10 tests**. The common failure is `NoSuchMethodException: android.hardware.input.InputManager.getInstance`, raised from Espresso's input-event initialization. The inspected build used Espresso 3.5.1 and AndroidX test JUnit 1.1.5. This identifies a test-stack/platform compatibility problem; it does not establish ten separate application workflow failures. A concurrent catalog edit to 3.7.0 / 1.3.0 appeared afterward and needs verification.

The pre-existing `day12/device-ui-regression.log` also reports `Process crashed`. Therefore the report's opening “implemented and verified” language overstates current D12 acceptance. Use a supported test-library/platform combination and rerun the same UI scenarios, then rerun on the OPPO target when available. Do not turn D12 Done based only on passing JVM and Room tests.

Sources: `android-native/gradle/libs.versions.toml`; `planning/03-evidence/D12.md`; `planning/03-evidence/day12/device-ui-regression.log`. Fresh log: `logs/review-d01-d12-2026-10-17/ui.log` (ignored local artifact).

### 2. High: questionnaire draft and position lack process-death restoration

`InterviewWorkspaceViewModel` holds its route, member selection, answers, dirty flag and base answers in memory. Unlike the household/member editors, it takes no `SavedStateHandle`. It debounces saves by 500 ms and reloads from Room on construction; navigation supplies the original entry route/member, not subsequent in-workspace selections.

Consequently a process killed before a pending edit becomes durable can lose that edit, and a recreated process can reopen the original section/member rather than the current one. Activity recreation tests retain the ViewModel and cannot prove this case. Confirmed saved answers remain protected by Room; this is a draft/position gap, not evidence of losing already confirmed saves.

Restore the selected route/member and a small draft/base representation or equivalent durable draft mechanism. Test a real process recreation while dirty, plus a failed-save draft. Source: `android-native/app/src/main/java/ph/tala/registry/ui/interview/InterviewWorkspaceViewModel.kt:44` and workspace factory in `ui/TalaApp.kt`.

### 3. Medium: Reviewed households require a manual status change before ordinary edits

The household editor defaults to the stored status. `EditRules.householdProblem` rejects **every** draft with status Reviewed, including one loaded from a legitimately reviewed household. Both Save and Name details call the same persistence path. The editor hides Reviewed from its selectable options.

Reproduction by code path: open a Reviewed household, edit its address, press Save; validation rejects it until the user manually selects another status. Name details has the same obstacle. Let an existing reviewed record transition to InProgress on a material edit, while continuing to prevent arbitrary granting of Reviewed. Sources: `domain/edit/EditRules.kt:46`, `ui/households/HouseholdEditViewModel.kt:118`, `ui/households/EditScreens.kt`.

### 4. Medium: form and editor validation disagree about string limits

The standalone editors cap household/member names at 80 characters and addresses at 150. Generated questionnaire text fields have no equivalent limits; `FormRules` accepts arbitrary text and `saveDraftSection` derives display names/addresses directly from those answers. A valid questionnaire save can therefore create a record that its standalone editor refuses to save. A structured name longer than 80 characters is read-only in that editor, which also blocks the Name details action because it first saves the invalid draft.

Centralize the intended constraints, apply them through the schema/generator and edit rules, and test transitions between questionnaire and editor. Do not patch generated Kotlin directly. Sources: `domain/edit/EditRules.kt:20`, `domain/form/FormRules.kt`, `data/HouseholdRepository.kt:125`.

### 5. Medium: save-before-switch has an untested concurrent-edit edge

Code inspection: `persist()` takes a snapshot, awaits the write, detects whether newer answers arrived and sets `dirty` accordingly, but returns true even if dirty remains true. `select()` then calls `load()` immediately; `leave()` exits immediately. Fields continue to accept input during saving. A new answer arriving during the save initiated by switching/leaving can therefore be replaced by `load()` or abandoned on exit.

This is a static concurrency finding, not a reproduced device failure in this audit. Add a delayed-repository test that edits during the awaited save, then switches/leaves. Either drain the newest draft before navigation or prevent further edits while that navigation operation commits. Sources: `ui/interview/InterviewWorkspaceViewModel.kt:97–128`, `ui/interview/InterviewWorkspaceScreen.kt`.

### 6. Low: archive search ignores the search text

`RegistryViewModel` applies the query only to active-filter branches. `HouseholdFilter.Archived` returns every archived household regardless of the search box. Apply search after choosing active versus archived records. Source: `ui/RegistryViewModel.kt:54`.

### 7. Medium: progress documentation needs reconciliation

The generated board is mechanically consistent, but its D12 note is obsolete. `traceability.md` still says D09–D20 are Backlog, root/native README feature summaries lag behind implementation, and several commands use the missing `browser/` path. D12 sources/tests/coverage/harness are pre-existing untracked work; they are not yet a reproducible source revision. Preserve historical evidence, but distinguish it from current status. Record revision and APK/test hashes for the completed D12 acceptance and commit the intended work after review. The audit does not inspect or alter the unrelated `.mcp.json` file.

## Verification performed

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedTestApp=true` | BUILD SUCCESSFUL. Unit task was initially up-to-date; existing XML: 118 tests / 23 suites, zero failures/errors. Lint: zero errors, 14 warnings. |
| Fresh unit execution | `:app:testDebugUnitTest --rerun` completed successfully: 118 tests / 23 suites, zero failures/errors. This was a normal debug unit task; it does not certify later concurrent edits. |
| Fresh isolated emulator storage/migration instrumentation | 14 tests passed, including D09–D12 persistence, transaction rollback and real v1→v2 upgrade. |
| Fresh isolated emulator shell/form/editing instrumentation | 10 tests run, 10 failed with common Espresso input-initialization error. |
| Device configuration | `emulator-5554`, Android 17 / API 37, 16 KB x86_64 emulator. OPPO target phone not attached. Normal application package/data was not used for instrumentation. |
| Generated questionnaire check | Passed: all 18 definitions match the inventory. |
| Board verifier | Passed: 30 cards, 11 Done, dependency/WIP/Done checks and browser board interaction checks. |
| Root `npm run check` / documentation checks | Passed; 72 Markdown files, 382 local links, JSON/spec/inventory consistency. These checks do not validate the truth of acceptance prose. |
| Artifact verifier | Passed: schema checks, executable SQLite reference DDL and responsive wireframes. |

The full browser scenario suite, complete native instrumentation suite, physical-phone rerun, UAT, release signing, accessibility and performance acceptance were not executed in this review. Browser checks and wireframe checks do not substitute for native workflow acceptance.

Tested isolated APK SHA-256: `5640C4B503AB7772671390EC0DEDDAB5747B12B231D70EA07E316E1A3F4F55D3`. Test APK SHA-256: `FBA6C56841C6925E345DE7BD0DEB1DCC4932D1F945013C039676A596DADD5788`. Local instrumentation logs are retained under `logs/review-d01-d12-2026-10-17/`; the directory is ignored by Git and is not a committed acceptance artifact.

## Recommended sequence

1. Close D12 honestly: move it to Verify, repair second-configuration UI testing, finish pending evidence, update the progress inputs and regenerate the board. Preserve failed logs. Retain the OPPO rerun as an explicit target-device check.
2. Resolve the workspace draft/concurrent-save issues and the Reviewed/editor constraint defects before extending the interview lifecycle. Add focused behavioral regression cases for these defects.
3. Implement D13 as a full lifecycle slice: visit history rather than overwriting the latest visit fields, callback list/scheduling, refusal/no-respondent rules, drawn/typed signature persistence and consent clearing, review navigation, and correct invalidation of review after changes. Existing visit/consent fields and review gating are useful groundwork, but do not complete this card.
4. Use D14 as a real gate: one scripted offline interview with at least two distinct members across all 18 sections, branch reversals, save failure/retry, actual process restart and reopened signatures. Obtain the planned peer/adviser label and flow review; record unresolved wording separately.
5. Continue GPS/manual location and map selection with explicit offline fallback. Decide the map/network contract deliberately: the current manifest and shell test assert no Internet permission, while D16 plans an online basemap. Update that invariant and its tests when implementing the approved map approach.
6. Prioritize versioned export/transactional restore and attachment round trips before polishing the map or adding backend scope. Existing app backup is disabled and no user export currently exists, so an uninstall/device loss has no implemented recovery flow.
7. Complete D19 archive/export interaction and D20 lifecycle/accessibility/performance checks, then protect regression, UAT, signed-build and handover gates. Keep cloud accounts/sync and real-resident deployment as separately scoped work.

Rebaseline dates using the actual remaining work. Eleven accepted work packages are not a reliable percentage of release completion, and presence of all 18 definitions is not evidence that the complete product workflow is accepted.
