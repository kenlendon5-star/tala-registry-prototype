# UI/UX implementation peer verification — 20 October 2026

**Verdict: partially implemented; not ready for UI/UX sign-off.** The implementation contains useful changes, but UX-03 still fails visibly and UX-04 remains inconsistent. This review supersedes the implementation document's blanket “Done” claims. Product source was not changed during verification.

## Findings and instructions for the implementing agent

1. **P1 — UX-03: the form disappears in 200% landscape, even before opening the keyboard.** On API 35, 2400×1080 px / 420 dpi, open HH-00452 → Continue questionnaire → directory → Demographics, set font scale 2.0 and rotate landscape. The section/member chips and three-row footer consume the body; no editable field remains visible and Check answers/Save draft are clipped. [Rendered failure](../../03-evidence/ui-ux-peer-2026-10-20/peer-landscape-200-no-keyboard.png), [hierarchy](../../03-evidence/ui-ux-peer-2026-10-20/peer-landscape-200-no-keyboard.xml). `InterviewWorkspaceScreen.kt:67` changes the pane threshold, but the single-pane fixed chrome and footer (`:346`) still exhaust the height. Make the short-height layout scroll/reflow or collapse chrome while preserving a focused field and save/navigation path. Verify portrait/landscape, 100%/200%, keyboard open/closed, and draft/member preservation. Do not shrink the user's text scale.

2. **P2 — UX-04: canonical numbers do not reach the footer.** Directory Demographics is **05**, but the same form says **Section 4 of 17**. [Directory](../../03-evidence/ui-ux-peer-2026-10-20/peer-directory.png), [form/footer](../../03-evidence/ui-ux-peer-2026-10-20/peer-demographics-100.png). Both footer branches (`InterviewWorkspaceScreen.kt:353`, `:366`) still use the filtered index/count. Use consistent canonical section numbering, or explicitly distinguish a form-progress ordinal from a section number. Add a UI assertion covering the directory, overview and footer together. `SectionNumberingTest.kt:16` duplicates the numbering calculation inside the test; it would pass with the old incorrect rendering.

3. **P2 — UX-01 coverage does not prove correct readiness.** The two new device tests pass on the awakened emulator, but their matcher (`WorkspaceSemanticsDeviceTest.kt:22–27`) accepts any of the three labels. A constant “Ready” would satisfy it. Prepare known untouched, answered-invalid and valid scopes, assert each exact expected state, then assert selection changes neither the state nor answers. Include directory/wide semantics and retain spoken TalkBack verification as pending. Avoid assuming a fixture is always among Home's two recent households.

4. **P2 — the visual helper can produce misleading evidence and does not restore auto-rotation.** `tools/verify-ui-ux-visual.py:112–114` disables accelerometer rotation; `:142–147` snapshots only font/user rotation, so `finally` leaves auto-rotation disabled. It also uses raw `adb` rather than RTK and captures the keyboard case even if no edit field was found. Snapshot/restore exact original settings, use RTK, assert actual viewport orientation and IME visibility, and fail when the target field is absent. The implementation's `ux03-landscape-200-keyboard.png` is portrait, so it cannot close the landscape issue. This peer run's rotation-after-focus captures also had **no shown IME** (`mInputShown=false`); their filenames are not proof of an IME pass.

5. **P2 — completion documentation overstates execution.** The shared tracker remained Open while the separate implementation document said Done. UX-02/UX-10 cite baseline screenshot `12-wide-member-scope-100.png`, which shows the original defect, and current signature/isolation regression runs were not supplied. Preserve original P1 priorities for UX-02/UX-03. Update the shared tracker before sign-off. The instruction not to run `verify-ui-ux-emulator.ps1` is incorrect: it accepts freshly built source/APKs and requires their identity to remain unchanged **during its run**. Run it against a new evidence directory after the remaining fixes, with `-PisolatedTestApp=true` used for the build.

The pinned member switcher is an improvement in source. Its unbounded member Column (`InterviewWorkspaceScreen.kt:175`) also needs a many-member/large-text check so it cannot consume the whole sidebar and hide other members or the section index. This is a follow-up risk, not a reproduced defect in this review.

## What passed and what remains pending

| Check | Peer result |
| --- | --- |
| Isolated debug app/test APK assembly, JVM task, lint | `BUILD SUCCESSFUL`; Gradle resolved most outputs as up-to-date. Retained JVM XML: **175 tests / 31 suites, zero failures/errors/skips**. This was an incremental verification, not a forced rerun of every JVM test. |
| New `WorkspaceSemanticsDeviceTest` | **2 passed** on unchanged-APK retry after waking the display. [Passing log](../../03-evidence/ui-ux-peer-2026-10-20/workspace-semantics-awake-retry.log). |
| Initial semantics attempt | Both tests timed out in setup. The display was subsequently found asleep; a screenshot was black and the hierarchy stale. [Initial failure retained](../../03-evidence/ui-ux-peer-2026-10-20/workspace-semantics.log). Do not call this a readiness-product failure or erase it. |
| UX-09 home metrics at 200% portrait | **Verified rendered correction:** Households, Members and Callbacks remain whole labels in two columns. [Screenshot](../../03-evidence/ui-ux-peer-2026-10-20/peer-home-200-metrics.png). Existing counts/actions are wired as before in source. |
| UX-02, UX-05, UX-07, UX-08, UX-10 | Relevant source changes present. Full acceptance remains pending: wide member selection/switching, zero-member add-and-return, signature/restart regression, spoken output. |
| Full 16-check regression | **Not rerun in this peer pass.** The baseline's 16 passing checks predate the implementation and cannot certify this changed build. |
| True-landscape keyboard acceptance | **Not passed.** Rotation dismissed the shown keyboard in the peer attempt; the no-keyboard landscape failure already blocks acceptance. |

Build command, from `android-native/`:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true --no-daemon --console=plain
```

Device test command, from the root after installing the isolated APKs:

```powershell
rtk proxy adb -s emulator-5554 shell am instrument -w -r -e class ph.tala.registry.ui.WorkspaceSemanticsDeviceTest ph.tala.registry.d08test.test/androidx.test.runner.AndroidJUnitRunner
```

[Peer evidence and identity](../../03-evidence/ui-ux-peer-2026-10-20/) are separate from the preserved baseline and implementation captures. Manual font, rotation, IME setting, night mode, size and density match the recorded original values after restoration. Only the isolated package was installed; normal app data was not cleared. The emulator is not evidence of a new OPPO phone pass.

Next agent: fix UX-03 first, then UX-04 and the meaningful readiness assertions; repair the visual helper; run current-build regressions and the acceptance matrix above; update [the shared tracker](UI-UX-EMULATOR-2026-10-20.md) with fresh evidence before requesting peer sign-off.
