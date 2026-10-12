# Tala system and sprint audit — 12 October 2026

**Verdict: the project is not feature-complete, and the Kanban board overstates completion.** Repository defaults mark D01–D20 Done, but neither the complete-interview gate nor the complete-phone-workflows gate is supported by the native implementation. The foundation has useful, reusable engineering; it still needs a stable build and renewed end-to-end acceptance before dependent cards can be closed.

This review covers the native application, browser reference, Expo starter, local Supabase setup, specifications, four weekly sprints, daily acceptance criteria, evidence records, and verification tooling. D01–D30 are daily work packages, not thirty separate sprints. The local board and repository defaults were inspected; a user's independently saved browser-board state may differ.

**Moving-source limitation.** Base commit was `01e4591`. The working tree was already dirty when the review began, and additional native source/tests appeared during the review without edits by this reviewer. The last captured source snapshot is **12 October 2026, 11:19:56 Asia/Manila**. Its [hash manifest](../../logs/system-review-2026-10-12/source-manifest.json) and source copies preserve the assessed state. Later edits require retesting. This report does not certify an in-progress change as fixed merely because its source appeared.

**What exists across the system**

| Area | Supported implementation | Material gaps |
|---|---|---|
| Native architecture | Compose, ID-based navigation, ViewModels/Flow, reusable validation, repository contracts, Room | Latest registration changes did not compile in the review; no complete native interview |
| Native storage | Eight relational entities, UUID relationships, transactional snapshot replacement, rollback handling, exported schemas, real v1→v2 archive-column migration | No revision/conflict protocol; attachments are references rather than implemented file lifecycle; migration tests assert household rows rather than a complete populated graph |
| Native editing | Household editor, member repository operations, saved-state drafts, archive marker and restore operations | Add-member UI was broken in the initial state; correction appeared during review but was not accepted by a new device run |
| Native questionnaire | Ten-field practice form; new work adds setup, visit and household-profile definitions and UI wiring | Only three registration sections are being ported; remaining questionnaire, pictures, consent/signatures and review workflow are not complete |
| Browser | All 18 reference sections, photos, signatures, callbacks, mapping, export; existing workflow suites pass | Confirmed durability/recovery/validation defects; no full restore workflow or persistent archive feature |
| Expo | Retained Expo Router starter | No Tala workflows; two TypeScript errors; not the selected delivery target |
| Supabase | CLI/configuration documentation and an intentionally empty migration | No application integration. Hosted metadata requests were denied; hosted schema/security were not assessed |
| Documentation/planning | Six specifications, field inventory, backup schema/example, wireframes, board controls and evidence files | Several status narratives conflict with code; passing document/schema checks do not prove app implementation |

**Sprint verdicts**

| Weekly sprint | Board claim | Audit verdict | Gate still required |
|---|---|---|---|
| Sprint 1, D01–D07 | 7/7 Done | Substantial foundation, acceptance must be reopened | Build the current source, add a household/member through the UI, save, force-stop and reopen; resolve current failures |
| Sprint 2, D08–D14 | 7/7 Done | Not complete | All 18 native sections, two-member persistence, callbacks, review and signatures; adviser review |
| Sprint 3, D15–D21 | D15–D20 Done; D21 Backlog | Not complete | Real GPS/permissions, native map/fallback, phone export/import, media recovery, archive filtering, lifecycle/accessibility/performance evidence |
| Sprint 4, D22–D30 | Backlog | Not complete; release gates not reached | Full regression, second configuration, UAT, signed artifact, install/upgrade, handover |

The displayed 20/30 Done count must not be presented as 67% product completion. Missing work includes large, interconnected user workflows; equal card counts are not a defensible effort or readiness measure.

**Card-by-card acceptance**

| Card | Evidence-based assessment | Recommended treatment |
|---|---|---|
| D01 Scope | Scope, inventory and acceptance baseline exist. Developer approval is recorded; independent sign-off and detailed test traceability remain qualifications | Retain with explicit qualifications |
| D02 Toolchain | Wrapper, pinned dependencies and target-phone history exist. Second configuration was explicitly deferred in D02 evidence | Retain the recorded deferral; fix current build/lint and keep second-device gate open |
| D03 Shell | Native navigation, filtering, ID routing and historical device tests exist | Retain, subject to current regression |
| D04 Shared controls | Representative fields and pure validation have meaningful JVM/device coverage | Retain for shared controls only |
| D05 Room | Transactions, scoping, rollback/reopen tests and durability evidence exist | Retain for local storage only |
| D06 Editing/saves | Real repository integration exists; initial add-member UI defect contradicts end-to-end completion. New correction is unverified | Reopen to Verify |
| D07 Foundation review | Depends on a successful user-facing create→member→save→restart demonstration | Block until D06 and current build pass |
| D08 Registration | New three-section port is underway; photos, editable enumerator profile and sex-specific counts remain absent | Incomplete; finish after foundation repair |
| D09 Member identity | Full demographics/other-demographics/migration native definitions and durable member workflows absent | Reopen |
| D10 Education/employment | Practice employment controls do not implement both complete member sections | Reopen |
| D11 Household services | Six full native service sections absent | Reopen |
| D12 Disaster/protection | Native sections and accepted 18-section matrix absent | Reopen |
| D13 Interview lifecycle | No complete visit-history/callback/review/signature workflow | Reopen |
| D14 Coverage review | Cannot pass while required sections and signatures are absent; report cites practice tests | Block |
| D15 Location | Coordinate row/storage tests exist; no native permission/provider/capture/manual-location UI | Reopen |
| D16 Map | No native map SDK integration, map screen, attribution or offline-map acceptance | Reopen |
| D17 Export | Backup specification and answer codec exist; no Android document-export workflow or complete serialized backup | Reopen |
| D18 Restore/migration | Real v1→v2 migration exists; import preview, validation, conflict handling and export→restore absent | Reopen; retain migration credit |
| D19 Archive | Reversible stored marker exists. New active filtering appears in source but needs tests; media/map/export acceptance remains incomplete | Reopen; partial implementation |
| D20 Hardening | Some insets, touch targets and status semantics exist; no complete lifecycle/accessibility report or measured dataset performance | Reopen |
| D21 Feature freeze | Required feature set is missing | Block |
| D22 Regression | Full native questionnaire/backup suite and second-device run absent | Keep Backlog |
| D23 UAT | No completed reviewer scenario log found | Keep Backlog |
| D24 Blocker fixes | Known required-flow and data-integrity defects remain | Keep Backlog |
| D25 Signed release | No accepted signed, non-debuggable candidate with tested fresh install/upgrade found | Keep Backlog |
| D26 Contingency | Cannot close until release decision and blockers are resolved | Keep Backlog |
| D27 Handover documents | Useful specifications exist, but final user guides/results/demo pack cannot yet describe a completed app | Keep Backlog; acknowledge preparatory documentation |
| D28 Rehearsal | Exact release rehearsal and second configuration absent | Keep Backlog |
| D29 Final verification | No final accepted artifact/hash/source tag/test package | Keep Backlog |
| D30 Handover | No reviewer acceptance/handover manifest | Keep Backlog |

These are audit recommendations, not automatic changes to the board. Preserve completed subtask credit when reopening a card. Keep only one active implementation card and one verification card.

**Highest-priority findings**

1. **Completion evidence is not tied to the claimed behavior.** [D14](../../planning/03-evidence/D14.md) uses practice-form tests as proof of all 18 sections. [D15](../../planning/03-evidence/D15.md) calls permission handling passed based on design/specifications despite no location permissions or provider. [D17](../../planning/03-evidence/D17.md) cites `AnswerCodec`, which encodes an individual answer using `org.json`, as a versioned backup implementation. [D18](../../planning/03-evidence/D18.md) proves a database migration, not backup restore. Replace these claims with actual command results and scenario artifacts before closing the cards.

2. **The current native baseline is not green.** The first build reused cached assemble/JVM results, then lint crashed in `androidx.annotation.experimental.lint.ExperimentalDetector` while analyzing `RegistryViewModel.kt` (`Unexpected owner function: null`). A later build, after external edits appeared, failed Kotlin compilation: missing `AnswerState`, `SectionSpec`, `AnswerValue`, and `isAnswered` imports in [HouseholdRepository.kt](../../android-native/app/src/main/java/ph/tala/registry/data/HouseholdRepository.kt), and `isAnswered`/`stateIn` in [InterviewSectionsViewModel.kt](../../android-native/app/src/main/java/ph/tala/registry/ui/interview/InterviewSectionsViewModel.kt). The new interface also needs all test implementations updated. Do not interpret a previous APK or cached tests as verification of these new files.

3. **Saved incomplete interviews are still not implemented correctly.** The [practice ViewModel](../../android-native/app/src/main/java/ph/tala/registry/ui/form/FormSessionViewModel.kt) is memory-only. The new registration ViewModel explicitly refuses to save while any required answer is missing. PR-18/TR-09 require incomplete drafts to persist independently of completion validation, with explicit save flushing pending edits. Implement draft persistence first, then enforce full validation only for completion/review. Android distinguishes transient saved state from disk persistence; saved state alone is not recovery after explicit dismissal/reboot. [Android state guidance](https://developer.android.com/topic/libraries/architecture/saving-states).

4. **Reviewed status can bypass the interview.** [EditScreens.kt](../../android-native/app/src/main/java/ph/tala/registry/ui/households/EditScreens.kt) exposes every `HouseholdStatus`; [EditRules.kt](../../android-native/app/src/main/java/ph/tala/registry/domain/edit/EditRules.kt) validates only name/address. A new empty household can therefore receive Reviewed status without completed answers, consent or signature. Make review a validated repository/domain transition; editing a reviewed answer must invalidate review.

5. **Foundation fixes need complete regression coverage.** The initial `MemberEditViewModel` treated every unsaved new member as missing, preventing the add form from appearing. The earlier device log reproduces this. A correction appeared during this review. Likewise, new `RegistryViewModel` code separates active/archived records, but its Archived branch ignores the search query. The household editor now clears a saved acknowledgement on later edits; the member editor still does not. Inputs also remain editable while a save is pending, so acknowledgement needs to identify the saved draft revision. Test these scenarios through both ViewModels and actual screens.

6. **The archive UI test has its own incorrect expectation.** Current [EditingDeviceTest.kt](../../android-native/app/src/androidTest/java/ph/tala/registry/ui/EditingDeviceTest.kt) calls `waitUntilSaved()` after archive and restore, while those operations deliberately return messages beginning `Archived.` and `Restored.`. Once add-member works, this can become the next failure. Assert the operation-specific acknowledgement and restored data/counts, not merely a generic word.

7. **Full-record durability needs additional cases before media/restore work.** Snapshot writes are atomic and repository writes use a mutex, which are strengths. However, there is no base revision or Conflict result for stale editors, and repository ownership is per activity instance. `removeMember` removes member answers but retains member-linked attachments; the store rejects references to the removed member. This is a code-level future-media defect, not a currently reproduced user photo-loss incident. Cover full graph preservation, member attachment removal and concurrent edits before expanding those workflows.

8. **Browser data-loss defects remain reproducible despite green happy-path tests.** In isolated browser contexts: tab A saved `NEWER AUDIT NAME`; stale tab B merely opened Reports and restored `Maria Santos` in storage. Injecting a quota error into profile saving displayed `Profile saved.` while the stored name remained unchanged. Stored `{}` caused script errors and an empty app. Invalid calendar dates, callback strings and invented multiselect options also passed validation; malformed phone text was normalized into a valid-looking number. See [probe output](../../logs/system-review-2026-10-12/browser-probes.json). Treat the browser as a behavior reference with known defects, not an unquestioned specification.

9. **Release/privacy configuration remains unfinished.** Unconditional `setShowWhenLocked(true)`/`setTurnScreenOn(true)` belong to the current activity and should not be introduced into a registry release just to facilitate device testing. Backup extraction XML has `<exclude domain="root" />` without explicit paths and does not demonstrate exclusion across all relevant storage domains. Resolve and test backup/transfer policy before making a no-transfer claim; this review did not demonstrate an actual transfer. [Android backup rules](https://developer.android.com/identity/data/autobackup). Continue using fictional data under the stated demo scope.

10. **Specifications need reconciliation before the next port.** Resolve DEC-02 (social-protection ownership) and DEC-03 (school type versus SNED/SPED). Keep evacuation/boundary data visibly illustrative until approved. The D20 plan proposes 200 households/1,000 members, whereas TR targets 1,000/10,000; choose one explicit acceptance baseline. Update stale README/traceability statements and distinguish database schema, questionnaire version and backup format. The existing `SYSTEM-ANALYSIS.md` is historical and should not be treated as a current status report.

**Verification performed**

| Check | Result and interpretation |
|---|---|
| Root `npm run check` | Passed browser syntax and documentation checks |
| `npm run docs:verify` | Passed backup example/schema checks, executable eight-table SQL and wireframe checks; these are design artifacts |
| Board verifier | Passed: 30 cards, 20 default Done statuses, gates, persistence/import/export and layouts. It explicitly asserts 20 Done; it does not test native features |
| Browser workflow suite | Passed all reported assertions |
| Browser map suite | Passed all reported assertions |
| Browser revision suite | Passed all reported assertions |
| Browser targeted negative probes | Reproduced stale overwrite, false save success, invalid-storage blank UI and validation gaps; malformed URL throws `URIError` in an isolated server-handler probe |
| Native initial assemble/JVM invocation | Assemble and JVM tasks UP-TO-DATE, not freshly executed tests. Existing XML records 30 passing tests |
| Native lint | Failed inside lint detector; no clean lint report |
| Native later compile/test/APK attempt | Failed compiling in-progress registration code; no fresh JVM or device acceptance from that attempt |
| Existing device evidence | Reviewed earlier 12 October log: 20 tests, 1 failure at new-member field visibility. Historical evidence only |
| Expo TypeScript | Failed: declarations missing for `animated-icon.module.css` and side-effect `@/global.css` import |
| Supabase hosted metadata | Tables/migrations/functions requests denied by connector permissions; no hosted readiness conclusion |

Browser tests used isolated contexts and a wrapper that preserved existing test screenshots and bounded Edge teardown. The document-verification command regenerated its normal wireframe previews. No production data was intentionally edited, no new APK was installed by this review, and no application implementation or board status was changed by the reviewer. Audit scripts/results were added under `logs/system-review-2026-10-12/`.

**Recommended next sequence**

1. **Stabilize the source and reopen unsupported Done cards.** Finish or isolate the concurrent registration edits; fix compile errors and lint compatibility; correct misleading evidence. Use one identified source revision for each test run. Exit: build and meaningful JVM/device regression pass, including add-member, truthful save state and archive/restore counts.
2. **Close the foundation gate, then implement one durable interview slice.** Save incomplete setup/household/visit drafts and two members' answers to Room; preserve identity and section scope; recover after force-stop; simulate failed writes. Exit: real UI entry survives reopen with an honest Saved/Unsaved state.
3. **Complete Sprint 2 against a field/scope/test matrix.** Port the remaining sections, resolve product decisions, add media lifecycle, callbacks/history, consent, signatures and validated review. Require both branch reversals and restart recovery per section. Exit: adviser-observed offline interview covering all 18 sections and two members.
4. **Complete recovery and phone integrations.** Implement versioned export and transactional restore as one round-trip capability; implement location/maps with denial/offline fallbacks; finish archive graph/media behavior and performance/accessibility evidence. Exit: clean-install restore reproduces the complete record and attachment set, and failures leave prior data intact.
5. **Only then enter release validation.** Run UAT and a second configuration; fix blockers; test a signed non-debuggable APK, fresh install and upgrade; record source revision/hash and hand over matching guides/results. Keep Expo inactive and cloud integration in the separately scoped later phase.

Do not promise the original October 28 finish based on the current Done count. Re-estimate after the stable durable slice and one complete questionnaire family are accepted; measure actual implementation plus verification time and preserve the release/UAT contingency. The immediate milestone should be a demonstrably reliable foundation and durable interview, not D21 feature freeze.
