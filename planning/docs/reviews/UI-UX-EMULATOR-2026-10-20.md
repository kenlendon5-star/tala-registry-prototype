# Tala native UI/UX emulator review and shared action log

Review date: 20 October 2026. Reviewer: Codex. **Review complete: 16 automated UI checks passed. Reproduced findings and remaining verification work are tracked below. Implementation was subsequently reviewed: see the peer-verification update below; acceptance is incomplete.**

This is the working handoff for agents implementing and verifying UI/UX corrections. Preserve the earlier [19 October static review](UI-UX-REVIEW-2026-10-19.md); this follow-up separates rendered behavior, automated results, and code-only findings. Scope is the native Compose app, using fictional data and isolated package `ph.tala.registry.d08test`.

## Test environment and evidence

- Tested source baseline: commit `8849a54`; application code is unchanged during this review.
- APK identity matches the accepted [D15 build](../../03-evidence/day15/device-build.txt). The retained JVM report contains 173 tests across 30 suites; references to 166 were corrected to match those artifacts.
- [Evidence directory](../../03-evidence/ui-ux-2026-10-20/).
- Initial API 37.2 Play Store/16 KB emulator: System UI ANR during the keyboard/navigation test. [Screenshot](../../03-evidence/ui-ux-2026-10-20/emulator-system-ui-anr.png). Its AVD requested 8192 MiB on a host with approximately 8 GB RAM. A requested 2048 MiB override was raised by the image to 4096 MiB; emulator diagnostics recommended 16 GB host RAM. Do not attribute this run to an app crash or count it as passing acceptance.
- Recovery uses a separate API 35 AOSP AVD, `Tala_UI_API_35`, with a requested 1536 MiB limit and two cores; this image enforces a 2560 MiB minimum (the guest reports 2,532,900 KiB usable RAM). API 35 matches the target OPPO phone. The original AVD and its data are preserved. [Image/checksum/setup record](../../03-evidence/ui-ux-2026-10-20/emulator-setup.json).

The lighter image needed one System UI **Wait** dismissal on its first boot; the manual review then ran without another system dialog. The final automated run uses this warmed image. These conditions make this review unsuitable for startup-time or frame-rate claims.

TalkBack speech, uncoached human usability, sunlight readability and release performance are separate checks; screenshots and semantics inspection do not certify them. No application code was changed to hide or bypass the observed UI issues.

## Final automated results

The final API 35 run passed **16 checks across six invocations**, at the restored 100% font scale/portrait baseline. The earlier two form-test failures remain retained; the passing repeat used unchanged APKs. Passing baseline tests do not cover or invalidate the manually reproduced 200%/landscape failure.

| Test group | Result | Retained evidence |
| --- | --- | --- |
| Shell, forms, household/member editing, archive/restore, draft restoration | 11 passed | [Log](../../03-evidence/ui-ux-2026-10-20/ui-regression.log) |
| Full 18-section, two-member interview | 1 passed | [Log](../../03-evidence/ui-ux-2026-10-20/full-interview.log) |
| Full interview after real force-stop | 1 passed | [Log](../../03-evidence/ui-ux-2026-10-20/full-interview-restart.log), [rendered review](../../03-evidence/ui-ux-2026-10-20/auto-interview-after-force-stop.png) |
| Draw, undo, redo, clear, consent and reviewed status | 1 passed | [Log](../../03-evidence/ui-ux-2026-10-20/signature-review.log), [signature screenshot](../../03-evidence/ui-ux-2026-10-20/auto-drawn-signature.png) |
| Signature and reviewed status after force-stop | 1 passed | [Log](../../03-evidence/ui-ux-2026-10-20/signature-restart.log), [reopened signature](../../03-evidence/ui-ux-2026-10-20/auto-signature-after-force-stop.png) |
| Callback, no respondent, refusal and partial resume | 1 passed | [Log](../../03-evidence/ui-ux-2026-10-20/callback-refusal-resume.log) |

[Machine-readable results](../../03-evidence/ui-ux-2026-10-20/test-results.json), [APK/device identity](../../03-evidence/ui-ux-2026-10-20/device-build.txt), and [tested source fingerprints](../../03-evidence/ui-ux-2026-10-20/tested-source-sha256.json) identify this run. The harness verified source and APKs were unchanged during execution.

Automated settings [before](../../03-evidence/ui-ux-2026-10-20/automated-environment-before.json) and [after](../../03-evidence/ui-ux-2026-10-20/automated-environment-after.json) match, with [no restoration errors](../../03-evidence/ui-ux-2026-10-20/automated-restoration-errors.json). Manual review settings also match [before](../../03-evidence/ui-ux-2026-10-20/manual-environment-before.json) and [after](../../03-evidence/ui-ux-2026-10-20/manual-environment-after.json), with [no restoration errors](../../03-evidence/ui-ux-2026-10-20/manual-restoration-errors.json). The normal app package was not installed over or cleared.

## Executed manual review

Screen configuration: 1080×2400 px at 420 dpi, approximately 411 dp portrait width; rotated 2400×1080 px for the phone landscape/two-pane case. The available landscape content width exceeds the existing 840 dp breakpoint. Both 100% and 200% font scale were exercised. The screenshot XML files alongside each PNG retain the accessible tree at capture time.

| Flow/configuration | Observed result | Evidence |
| --- | --- | --- |
| Home, light, 100% | Primary browse/profile navigation and counts are readable. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/01-home-light-100.png) |
| Home, light, 200% | Header remains readable. Three-column metrics split words such as Households and Callbacks across lines; use an adaptive metric layout. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/02-home-light-200.png) |
| Household list and Overview, 200% | Text wraps and scrolling exposes actions; no blanket top-bar clipping reproduced. | [List](../../03-evidence/ui-ux-2026-10-20/03-households-light-200.png), [Overview](../../03-evidence/ui-ux-2026-10-20/04-overview-light-200.png) |
| Workspace, portrait, 100% / 200% | At 200%, the counter splits `Section 1 of 17` awkwardly and the footer takes much more form space. Controls remain reachable in this portrait snapshot. | [100%](../../03-evidence/ui-ux-2026-10-20/06-workspace-light-100.png), [200%](../../03-evidence/ui-ux-2026-10-20/05-workspace-light-200.png) |
| Member form, landscape, 100% / 200% | Two-pane layout activates. At 200%, only a sliver of the first field is initially visible below the title. | [100%](../../03-evidence/ui-ux-2026-10-20/09-member-landscape-100.png), [200%](../../03-evidence/ui-ux-2026-10-20/10-member-landscape-200.png) |
| Member form, landscape, 200%, keyboard open | **Confirmed failure:** editable content disappears and task controls are clipped above the IME. This is an app layout issue observed after emulator recovery. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/11-member-landscape-200-keyboard.png) |
| Wide member selector | Selected Maria has a Ready-style check while required demographics fields are empty. Reaching the selector requires scrolling past the entire section index. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/12-wide-member-scope-100.png) |
| Section directory versus overview Sections tab | Directory calls Demographics **04**; overview calls Household members **04** and Demographics **05**. | [Directory](../../03-evidence/ui-ux-2026-10-20/07-directory-light-100.png), [Overview list](../../03-evidence/ui-ux-2026-10-20/14-sections-tab-dark-100.png) |
| Empty member-scoped form | Message explains the prerequisite, but there is no Add member action or concrete navigation guidance. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/15-no-member-dark-100.png) |
| Dark workspace | Text and footer are visibly separated; the earlier blanket dark-footer concern was not reproduced. | [Screenshot](../../03-evidence/ui-ux-2026-10-20/13-workspace-dark-100.png) |
| Touch/accessibility bounds | Survey/visit/member chips and Next report 48 dp clickable bounds; directory rows about 52 dp; header/menu controls approximately 47–48 dp after pixel rounding. Visible pill height alone overstated the earlier size concern. | [Measurements](../../03-evidence/ui-ux-2026-10-20/touch-and-semantics-bounds.json) |

## Shared issue tracker

P1: resolve before accessibility/readiness sign-off. P2: resolve before uncoached UAT. P3: optional polish after the higher priorities. These are UI review priorities, not new sprint completion claims.

Implementation anchors: [workspace, directory and member selector](../../../android-native/app/src/main/java/ph/tala/registry/ui/interview/InterviewWorkspaceScreen.kt), [shell, home and overview](../../../android-native/app/src/main/java/ph/tala/registry/ui/TalaApp.kt), [signature pad](../../../android-native/app/src/main/java/ph/tala/registry/ui/interview/SignaturePad.kt), [form tests](../../../android-native/app/src/androidTest/java/ph/tala/registry/ui/FormDeviceTest.kt).

| ID | Priority | Status | Owner | Recommendation and implementation location | Verification required |
| --- | --- | --- | --- | --- | --- |
| UX-01 | P1 | Ready for verification — exact-state/TalkBack pending | Implementing agent | Expose the real section readiness as accessible state on the section row/chip in `InterviewWorkspaceScreen.kt`. The drawn `SectionStatusIcon` has no status semantics. Keep navigation selection separate from readiness. | An untouched section, an invalid answered section and a valid answered section each announce the correct distinct state; selection changes must not announce completion. Verify merged Compose semantics and TalkBack. |
| UX-02 | P1 | Ready for verification — source changed | Implementing agent | Remove the Ready check used only to indicate the selected member in `TwoPaneWorkspace`. It currently calls `SectionStatusIcon(if (selected) SectionStatus.Ready else null)`. Use selection styling/semantics or a distinct selection mark. | Select a member with incomplete answers in the wide layout: no readiness check appears solely because that member is selected. Section validation and review eligibility remain unchanged. |
| UX-03 | P1 | Needs fix — peer rendered failure persists | Codex (peer review) | Make the workspace adapt to **available height and IME**, not width alone. In `InterviewWorkspaceScreen.kt` the fixed header, two-pane structure and sticky footer consume the short landscape viewport; `TalaApp.kt` applies IME insets. Collapse/reflow nonessential chrome when necessary and keep the focused field visible. Reflow the portrait counter/footer at 200%; do not shrink user-selected text. | Reproduce from Demographics at 200%, rotate to landscape and focus First Name: the field, its label/value and a usable save/navigation path must remain reachable with the keyboard open. Repeat portrait, 100%, and keyboard dismissal; preserve drafts and member scope. See screenshots 05, 10 and 11. |
| UX-04 | P2 | Partial — footer still inconsistent | Codex (peer review) | Use one canonical section order/numbering for the overview's Sections tab and workspace directory/wide index. `TalaApp.kt` includes Members in its 18-item list; workspace excludes it and renumbers subsequent sections. Do not hand-edit generated questionnaire definitions. | The same section has the same number/name in every list and review jump. Members remains accessible through its actual editor; all 18 questionnaire sections retain coverage. |
| UX-05 | P2 | Ready for verification — source changed | Implementing agent | Give the member-scoped empty state a usable path to add a member, or explicit directions to the Members tab. The current workspace message provides no action. Any new navigation must respect save-before-exit. | Open a member-scoped section with zero members; reach Add member, save a fictional member, and return to the intended section without losing other drafts. |
| UX-06 | P2 check | Blanket defect not reproduced | Codex | Do **not** blanket-resize every chip or header button based on its visible shape. The retained clickable bounds are approximately 48 dp; directory rows are larger. Retain targeted physical-tap/overlap checks during accessibility testing. | [Measured bounds](../../03-evidence/ui-ux-2026-10-20/touch-and-semantics-bounds.json), 420 dpi. Minor sub-dp rounding differences are not by themselves proof of a functional target defect. Reopen this item with a specific failing target if one is found. |
| UX-07 | P2 | Ready for verification — regressions pending | Implementing agent | Label the typed-name signature path clearly as the accessible alternative, and expose meaningful semantics for the drawn pad and its saved/empty state. Preserve the existing draw/undo/redo/clear behavior. | Typed and drawn paths both remain usable; assistive-tech users can identify the alternative without exploring an unexplained canvas. Run signature/restart tests after changes. |
| UX-08 | P3 | Needs spoken verification — source changed | Implementing agent | Verify how TalkBack announces the literal `▾`, `‹` and `›` characters; use plain accessible labels if they add noise. Dark footer separation is already visible in screenshot 13 and does not justify a theme redesign. | Record actual spoken output before changing labels; retain clear Previous/Next/select actions and first-error focus. |
| UX-09 | P2 | Verified — 200% portrait labels | Codex (peer review) | Reflow the home metric row for large text rather than keeping all three metrics in narrow columns. `TalaApp.kt` HomeScreen/Metric layout currently breaks words at 200%. | At 200% portrait, Households/Members/Callbacks remain readable as whole labels in an adaptive layout; counts and their existing actions remain correct. See screenshot 02. |
| UX-10 | P2 | Ready for verification — wide/scope tests pending | Implementing agent | Keep the active member context and member switcher discoverable in wide layouts. `TwoPaneWorkspace` currently places Member scope after all 17 section rows in one scrolling sidebar. Move/pin it or surface the current member above the form without losing access to the index. | On short landscape screens, identify the active member and switch scope without scrolling through the entire section index. Save-before-switch and two-member isolation tests must still pass. Compare screenshots 09 and 12. |
| UX-11 | P2 verification | Intermittent — unchanged-APK retry passed | Unassigned | Reproduce the form suite's navigation-tree and appointment-confirmation failures before changing product logic. Initial API 35 run: `allControlsValidateAndHiddenRequiredAnswersAreCleared` failed during `openPractice` because the screen was absent from the merged tree; `calendarAndTimePickersCommitOnlyOnConfirmation` timed out waiting for Use appointment. These results do not by themselves prove broken validation or lost dates. | Capture screen/semantics on failure, wait for the actual destination/window/IME state, and verify Cancel versus confirm outcomes. Preserve assertions. Retain the first failure and any unchanged-APK retry; do not claim a product fix solely from a passing retry. |

Recommended implementation order: **UX-03 → UX-02 → UX-01 → UX-10/UX-05 → UX-04/UX-09 → UX-07/UX-08**. UX-06 is a measured correction to the earlier static review, not a request for cosmetic resizing.

### Reproduce the highest-priority findings

- **UX-03:** open HH-00452 → Continue questionnaire → section directory → Demographics. Set font scale to 2.0, rotate landscape, and focus First Name. Dismiss the keyboard to recover; restore the original settings after testing.
- **UX-02/UX-10:** with the same incomplete record at 100% landscape, scroll the **left** section index to its bottom. The selected member shows the check despite empty required fields. The form header does not otherwise keep the active member visible.
- **UX-04:** compare Demographics in the workspace directory with Interview → Sections. The former is 04, the latter 05.
- **UX-05:** open HH-00453, which has no members → Continue questionnaire → Demographics. The only body content is the prerequisite message.

The first API 35 form run is retained in [its failed log](../../03-evidence/ui-ux-2026-10-20/prior-failed-030422-ui-regression.log); the unchanged-APK repeat is [11 passing checks](../../03-evidence/ui-ux-2026-10-20/ui-regression.log). The earlier API 37/System UI interruption is retained [separately](../../03-evidence/ui-ux-2026-10-20/prior-failed-030006-ui-regression.log).

Section readiness semantics and the signature accessibility alternative remain code-confirmed concerns; this review did not claim a completed spoken TalkBack pass. The older claims that every chip is undersized, headers necessarily clip at 200%, or dark footers have no separation were not reproduced as blanket defects.

## How agents should work and update this file

1. Claim one issue by setting Owner and Status to `In progress`; record the starting commit in the change log. Read the linked evidence and current code before editing.
2. Keep each change focused. Preserve explicit Idle/Saving/Saved/Failed states, actual review eligibility, member/household scoping, conflict refusal, archive recovery and offline operation. Do not add percentages or label selection as completion.
3. Follow repository generators for generated files. Do not add network permissions, map SDKs, new persistence schemas or dependencies as part of a UI correction.
4. Add meaningful behavior/semantics coverage where needed; reuse the existing Compose tests. Retain failed attempts and exact APK/source identities. A source edit or passing build alone is not visual verification.
5. Set Status to `Ready for verification` with changed files, commit, commands, results and before/after screenshots. A verifying agent may set `Verified` only after reproducing the acceptance criteria. Explain any `Blocked` or `Deferred` item and its next action.
6. Update the test matrix and append a dated change-log row. Preserve IDs and prior evidence; do not erase failures or silently recast unexecuted tests as passing.

## Reproduction

Build isolated APKs if the application or test source changes, from `android-native/`:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true --no-daemon --console=plain
```

From the repository root, against the connected emulator:

```powershell
rtk proxy adb devices -l
rtk proxy powershell -NoProfile -File tools/verify-ui-ux-emulator.ps1 -DeviceSerial emulator-5554 -EvidenceDirectory planning/03-evidence/ui-ux-2026-10-20
```

Use a new dated evidence directory for follow-up runs. The harness checks the isolated application ID, records source/APK fingerprints, retains partial/failing logs, and restores the settings it changes. It executes 11 shell/form/editing/restoration checks plus five full-interview/signature/lifecycle checks, across six invocations. It does not clear app data or perform the manual layout review.

The harness continues the remaining invocations after a failed test group, writes `test-results.json`, and exits unsuccessfully if any group fails or has fewer successes than expected. Read the per-group results rather than treating the existence of metadata as a passing result.

For manual layout verification, record original font scale, rotation, night mode, size/density overrides and keyboard settings before changing them. Inspect 1.0 and 2.0 font scale, light/dark theme, phone portrait and a width of at least 840 dp. Restore the exact original values afterward. Do not run UI Automator or manual input concurrently with instrumentation; they can interfere with its automation connection.

## Guidance supporting the review

[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) describe 48 dp touch targets, automatic target expansion and its overlap risk. [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) explains exposing custom control state. Android's [200% font scaling guidance](https://developer.android.com/about/versions/14/features#non-linear-font-scaling) supports testing large text rather than assuming a fixed header remains usable.

## Change log

| Date | Agent | Issue/scope | Change and evidence | Result |
| --- | --- | --- | --- | --- |
| 2026-10-20 | Codex | Review setup | Preserved static review; retained initial System UI ANR; prepared isolated emulator harness and lighter AVD. Corrected D15 JVM documentation to its retained 173/30 result. | Setup complete; no application changes |
| 2026-10-20 | Codex | UI/UX execution | Captured 15 manual screen configurations with accessible trees and measured bounds; reproduced UX-02/03/04/05/09/10, inspected readiness/signature semantics in code, and corrected blanket size/header/dark-theme claims. | Open recommendations have reproduction and verification criteria |
| 2026-10-20 | Codex | Automated regression | Final six invocations: 16 passed. Initial API 35 form failures retained, unchanged-APK repeat passed. Source/APK checks and both settings restorations passed. | Baseline functional review complete; UX corrections remain open |

## Peer verification update — 20 October 2026

[Detailed findings, evidence and next-agent instructions](UI-UX-PEER-VERIFICATION-2026-10-20.md). Current implementation is **not accepted as complete**: UX-03 still hides the form in 200% true landscape even without an IME; UX-04 fixes directory numbering but leaves the footer inconsistent. UX-09 rendered labels are verified. Two new semantics tests passed after waking the emulator, but exact readiness-state coverage and TalkBack remain pending. Build/lint passed; 175 JVM results were retained and the JVM task was up-to-date. The original 16 passing baseline checks are historical, not execution evidence for the changed build. All original priorities and failure artifacts remain preserved.
