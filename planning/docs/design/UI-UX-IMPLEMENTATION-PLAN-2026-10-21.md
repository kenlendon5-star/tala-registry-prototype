# Portrait and landscape UI/UX implementation guide

Date: 21 October 2026 (Asia/Manila)

Status: Home redesign implemented; see [the current Home handoff](HOME-REDESIGN-2026-10-21.md) for the latest scope and actual verification results. The original recommendations below remain a historical planning checklist; map and questionnaire redesign work is still separate. Later user-approved Home decisions supersede conflicting suggestions below.

## Objective and review baseline

Improve the native Tala Android app's fieldwork efficiency, responsive layout, and accuracy of action labels. Keep the existing teal visual identity, Material 3 components, readable form controls, explicit save feedback, and signature accessibility alternative.

The review gave provisional ratings of **7/10 overall, 7.5/10 portrait, and 6/10 landscape**. These are design judgments, not measured usability scores or release certification. The review inspected current source and eight screenshots from 20 October 2026. Those screenshots predate the latest home redesign. Current home and map findings were source-based; map overflow and new home wrapping need fresh rendered verification.

Primary scope: `android-native/`. The browser reference and retained Expo starter are outside this implementation.

Approved terminology: Home uses **Residents** for the people recorded across active households, and **Add household member** for the add-person action. This count does not claim to represent the barangay's entire population. Household-specific member terminology and internal model names remain appropriate. These two Home labels have been updated in source; the broader layout and routing work below remains planned.

## Instructions for implementing agents

1. Read the applicable repository instructions, the referenced RTK instructions, and relevant Android skills. Prefix shell commands with `rtk`.
2. Inspect current source and `rtk git status --short` before editing. At planning time, the workspace already contains uncommitted map, navigation, repository, test, and documentation changes. Preserve that work and reconcile against the current files; do not reset files or replace them with an older screenshot's design.
3. Use this guide as the implementation checklist. Recheck each baseline against current code before changing it. Record any resolved or superseded finding with evidence.
4. Implement within the existing Compose, navigation, ViewModel, repository, DataStore, and Room boundaries. Pass typed IDs between screens. Keep the questionnaire's save-before-switch/exit and conflict handling intact.
5. Select layout using available window width, height, and font scale. Rotation alone is insufficient. Keep task screens free of the global bottom navigation as currently intended.
6. Keep questionnaire definitions, validation rules, consent behavior, stored records, and map coordinate provenance intact. This work does not authorize implementing backup import/export or changing questionnaire content.
7. Use fictional records and isolated app/device verification where practical. Do not clear the normal app's data to obtain a clean screenshot. Run UI automation sequentially on a device.
8. Update the checklist and verification record as work finishes. Distinguish source inspection, automated assertions, visual observations, and remaining checks. Do not report a screenshot as evidence of persistence or successful saving.

## Source map

Typography update: use Title Case for Home headings, action labels, metric labels, and status labels (for example, **Continue Interview**, **Register Household**, **Recorded Residents**, **Sex Not Recorded**, and **Households By Purok**). The user's intended convention capitalizes the first letter of each word. Preserve the lowercase **tala** brand wordmark and user-entered names and addresses. The latest mockup also removes the slogan, Quick actions, LOCAL DEMO badge, and Saved on this device row, and adds a household-by-purok dashboard; these visual revisions supersede conflicting earlier Home layout suggestions below. Do not remove actual save/error feedback from editing flows.

Paths below are relative to the repository root.

Latest branding update: the Home header must read **Tala Registry** beside the user-supplied logo, with **Barangay Cogon** (or the configured barangay) below. This supersedes the earlier lowercase tala wordmark exception. See [the latest Home mockup](mockups/tala-registry-home-2026-10-21.png).

| Area | Starting files |
|---|---|
| App shell, home, shortcuts, metrics, reports | `android-native/app/src/main/java/ph/tala/registry/ui/TalaApp.kt` |
| Typed destinations | `android-native/app/src/main/java/ph/tala/registry/ui/Routes.kt` |
| Household list/filter state | `android-native/app/src/main/java/ph/tala/registry/ui/RegistryViewModel.kt` |
| Questionnaire layout and navigation | `android-native/app/src/main/java/ph/tala/registry/ui/interview/InterviewWorkspaceScreen.kt` |
| Questionnaire state and save lifecycle | `android-native/app/src/main/java/ph/tala/registry/ui/interview/InterviewWorkspaceViewModel.kt` |
| Map layout | `android-native/app/src/main/java/ph/tala/registry/ui/map/MapScreen.kt` |
| Map selection and editing | `android-native/app/src/main/java/ph/tala/registry/ui/map/MapViewModel.kt` |
| Editor layouts and save feedback | `android-native/app/src/main/java/ph/tala/registry/ui/households/EditScreens.kt` |
| Existing profile persistence pattern | `android-native/app/src/main/java/ph/tala/registry/data/DataStoreEnumeratorProfileStore.kt` |
| Existing device/JVM checks | `android-native/app/src/androidTest/`, `android-native/app/src/test/` |

Reference: [Android layout and navigation guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns).

## Work items

### UX-R1: Adapt the map to short and wide windows

Priority: High. Baseline: source-confirmed fixed sizing; rendered failure not yet reproduced.

The map currently stacks a heading and notes, a map with a 300dp minimum and 320dp requested height, status lines, selected-household or adjustment controls, and a weighted household list inside a non-scrolling column. Short landscape windows can leave insufficient space for the controls and list.

Implementation direction:

- Use a map/details split when the available width supports readable panes. Let the map occupy available height instead of retaining a universal fixed height.
- In narrow portrait windows, use a collapsible details sheet or an equivalent responsive details region. Ensure the list and selected-household actions remain reachable.
- When editing coordinates with the keyboard open, keep the active field and save/cancel actions reachable. Provide a usable fallback at large text sizes or very short heights.
- Preserve online/offline rendering, attribution, pin selection, coordinate adjustment, write feedback, external map actions, and the distinction between device and manual coordinates.

Acceptance:

- [ ] Portrait and landscape expose the map, household selection, and relevant actions without inaccessible content.
- [ ] Selecting a pin and adjusting it work in both orientations, including with the keyboard open.
- [ ] Rotation does not discard selected household, pending coordinates, or save/error state.
- [ ] Large text can reflow or scroll without obscuring save/cancel controls.
- [ ] Existing map behavior checks still pass.

### UX-R2: Compact questionnaire navigation at short heights

Priority: High. Baseline: observed in saved landscape screenshots and supported by current source.

The save/review row, horizontally scrolling section chips, member chips, and section heading occupy substantial space before the first question. The existing compact layout correctly allows navigation to scroll away with the form; retain that benefit.

Implementation direction:

- At short heights, replace the full section-chip strip with a compact current-section selector that opens the section directory.
- For member-scoped sections, show a compact, clearly labeled current-member selector. Keep the active member unambiguous while editing.
- Keep the full section directory one tap away. Retain section readiness cues, progress position, previous/next navigation, and review access.
- Preserve a readable form viewport while the keyboard is open. Allow navigation to scroll away; do not add fixed bars that recreate the height problem.
- Preserve the existing two-pane approach on windows with enough width and height. Do not force tablet panes onto short landscape phones.

Acceptance:

- [ ] Short landscape shows less navigation chrome before the first field than the baseline capture.
- [ ] Section and member switching still save safely and display the correct scope.
- [ ] Focused fields and validation messages remain reachable above the keyboard.
- [ ] At 200% text, selectors and actions reflow without overlapping or losing labels.
- [ ] Save, review, directory access, and previous/next remain reachable by touch and accessibility navigation.

### UX-R3: Make labels and destinations agree

Priority: High. Baseline: confirmed in source.

| Current mismatch | Required behavior |
|---|---|
| Households metric says “Browse all” but calls registration | Open the active household list with search cleared and the All filter selected. Keep registration on its own action. |
| “Add household member” opens the general household list | Open an explicit household selection step, then open `Destination.MemberAdd` for that household. Explain that a household must be selected. |
| “Capture location” opens the map | For this change, rename the shortcut to “View map” and retain its map destination. Actual device capture remains in the household location flow. |
| Reports offers enabled export/restore buttons with empty handlers | Mark export and restore as unavailable, explain their planned status, and remove or disable the inert actions. Do not implement backup functionality in this UI pass. |

Acceptance:

- [ ] “Browse all” opens the unfiltered active list even after previous searching/filtering.
- [ ] “Add household member” clearly requests a household and reaches that household's member form.
- [ ] With no active households, the member action explains the prerequisite and offers registration.
- [ ] Map shortcut wording describes the destination it opens.
- [ ] Reports has no enabled action that silently does nothing or implies a successful export/restore.
- [ ] Back navigation from the selection step is predictable and creates no member record.

### UX-R4: Put fieldwork actions before the slogan card

Priority: Medium. Baseline: older screenshots show the large hero; current source still places it before task actions.

Implementation direction:

- Prioritize the available Continue interview action, Register household, and callback access near the top of Home.
- Move the slogan card below frequent actions and operational information, or reduce it to a compact brand treatment.
- Keep enumerator/profile access discoverable without letting repeated greeting/profile text dominate the screen.
- Adapt Home to short windows as well as narrow ones. Preserve readable spacing and text scaling rather than shrinking text to fit.

Acceptance:

- [ ] At normal text on the chosen reference portrait phone, primary work actions appear before the slogan card and without scrolling past it.
- [ ] Landscape and 200% text preserve the same task-first reading order.
- [ ] Empty, loading, and populated states each present an appropriate next step.

### UX-R5: Reflow home shortcuts by available space

Priority: Medium. Baseline: current source uses four equal-width buttons in one row.

Implementation direction:

- Use two columns on narrow phones; allow one column when enlarged text or long labels require it. Use a wider row only when all labels fit comfortably.
- Base the decision on available width and text scale rather than orientation alone.
- Keep action order consistent, full labels readable, and touch targets at least 48dp. Avoid fixed heights that clip wrapped text.
- Check the five-item bottom navigation at narrow widths and enlarged text while changing Home. Choose any alternate navigation treatment based on actual available space and rendered evidence.

Acceptance:

- [ ] All shortcut labels remain readable at 100%, 150%, and 200% font scale.
- [ ] Buttons do not overlap, truncate essential wording, or break words into unusable narrow columns.
- [ ] Touch targets and accessibility traversal follow the visible action order.
- [ ] Home navigation destinations remain reachable in both orientations.

### UX-R6: Resume the last active interview

Priority: High. Baseline: Home currently uses `state.households.first()` for Continue rather than an explicit recent-interview reference.

Implementation direction:

- Persist a small resume reference containing household ID, section route, and optional member ID using an appropriate durable store. Do not store a duplicate household snapshot.
- Update the reference when the user enters an interview or changes its active section/member, following the existing save lifecycle. Opening a list or map alone should not replace it.
- Continue should reopen the recorded questionnaire scope, using typed destinations and existing draft restoration/conflict protection.
- Validate the reference against current records and section/member availability. Exclude archived or missing households. If a member is removed or a section becomes invalid, use a safe scope and explain the fallback where needed.
- When there is no valid recent interview, hide Continue and show registration/browsing actions. Do not silently substitute the first household in the list.
- Persist navigation context only; do not imply that unsaved answers are durably stored. Preserve existing save failure and conflict messages.

Acceptance:

- [ ] Open household A, switch section/member, then open household B: Continue targets B's recorded scope.
- [ ] Rotation, activity recreation, and a normal app restart retain the resume reference.
- [ ] Reordering households does not change the selected resume target.
- [ ] Missing/archived households and removed members cause a safe fallback without a crash or scope leakage.
- [ ] Failed saves and conflicts retain existing recovery behavior and never display false success.
- [ ] A clean installation with no recent interview does not display a fabricated Continue target.

## Suggested implementation sequence

1. UX-R3: correct destinations and unavailable Reports actions.
2. UX-R6: implement and verify durable resume context.
3. UX-R4 and UX-R5: reorder Home and reflow shortcuts together.
4. UX-R2: compact questionnaire navigation.
5. UX-R1: adapt map layout against the current map implementation.
6. Capture fresh evidence and close the acceptance checklist.

These are work packages, not instructions to create separate chats or spawn agents. If work is later divided, coordinate edits to `TalaApp.kt`, which is shared by several packages.

## Verification and evidence

Use an emulator or isolated build and fictional records. Record device/window size in dp, Android version, font scale, keyboard visibility, application ID, and source/APK identity for each run. Restore any changed device settings afterward.

| Scenario | Required checks |
|---|---|
| Narrow portrait, 100% text | Home hierarchy, shortcut layout, all action destinations, Reports unavailable state |
| Phone landscape, 100% text | Home reachability, compact questionnaire navigation, map/details layout |
| Portrait and landscape, 150% and 200% text | Labels, scrolling, action reachability, section/member context |
| Keyboard open in both orientations | Questionnaire field focus, validation recovery, map coordinate save/cancel |
| Wide/tall window | Questionnaire two-pane layout and usable map/details split |
| Rotate during editing | Same household, member, section, draft, and selected map pin |
| Restart app | Continue reference restored; existing saved data unchanged |
| Empty/stale/error states | No household selection, removed resume target/member, save failure/conflict, offline map |

Add focused behavioral tests for action routing and durable resume/fallback logic. Reuse existing device tests for form scope, rotation, save lifecycle, and map behavior; extend them only where the new behavior requires it. Do not add tests that merely duplicate composable structure or use screenshots to prove persistence.

From `android-native/`, run the checks appropriate to the final change:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For device verification, follow the existing isolated-build setup in [the native README](../../../android-native/README.md). Inspect existing harness assumptions before reuse; older UI scripts may rely on superseded labels or require exact source/APK identity. Do not treat an old passing run as verification of new code.

Retain fresh portrait/landscape screenshots and relevant device-test outputs under a new dated directory in `planning/03-evidence/`. Include Home, questionnaire with and without keyboard, member/section selectors, map selected-pin/adjustment states, and Reports. Inspect the rendered images, not just the UI tree.

Validate documentation from the repository root:

```powershell
rtk proxy node tools/check-docs.cjs
```

## Completion record

Complete this section after implementation; leave unchecked items open with a specific reason.

- [ ] UX-R1 accepted with fresh rendered evidence.
- [ ] UX-R2 accepted with fresh rendered evidence.
- [ ] UX-R3 remaining Reports work; Home Browse All and callback routing are fixed, and removed shortcuts are superseded by the approved Home design.
- [x] UX-R4 implemented with fresh Home evidence; slogan removed per the later user decision.
- [x] UX-R5 superseded by removal of Quick Actions; Home card reflow verified at 100%, 150%, and 200% text.
- [x] UX-R6 implemented with durable navigation context, persistence and stale-reference tests.
- [x] Relevant Home JVM, lint, build, and focused device checks passed; this does not close the separate map/questionnaire work.
- [x] Home evidence paths, tested APK identity, and remaining limitations recorded in the linked handoff.

Implementation summary: [Tala Registry Home implementation and agent handoff](HOME-REDESIGN-2026-10-21.md).

Verification results: 202 JVM tests, 9 focused device tests, build and lint passed. Final device evidence: `planning/03-evidence/home-redesign-2026-10-21/orientation-verified/`.

Remaining limitations: Map/questionnaire adaptation and Reports work remain separate. TalkBack spoken output and outdoor readability are not certified by the Home screenshot/semantics checks.
