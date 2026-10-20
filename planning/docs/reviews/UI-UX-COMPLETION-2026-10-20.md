# UI/UX corrections and verification — 20 October 2026

Owner: Codex. Follow-up to [peer verification](UI-UX-PEER-VERIFICATION-2026-10-20.md). **Verification in progress; final results will be recorded here.**

## Changes

- UX-03: short-height or large-text workspaces now scroll as one page, including navigation and save controls. The questionnaire no longer reserves a duplicate fixed app bar. Changing section/member returns the page to its start. Ordinary tall phone and wide layouts retain their bounded form/navigation arrangement.
- UX-04: footer, directory and wide index use the canonical 18-section numbering. The JVM test calls the production helper; a device test compares Demographics 05 in the directory, footer and overview.
- UX-01: device fixtures now explicitly cover valid setup, partially answered visit and untouched water sections, with exact state descriptions in chips and the directory. Selection must preserve readiness and stored answers. Fixtures are located through the full household list.
- UX-05: a new device regression follows the empty-member guidance, adds a fictional member, returns to Demographics and checks that household answers remain saved.
- UX-07: signature lifecycle tests now check the typed-name alternative label and the drawn pad's saved/empty semantic state, alongside existing draw/undo/redo/clear and restart assertions.
- UX-02/UX-10: the pinned member list has a bounded scroll area, allowing later members to remain reachable without consuming the section index.
- Verification tools: visual runs require a new evidence directory, use RTK, reject missing fields/IME/orientation, scroll within visible content bounds, and restore rotation including accelerometer mode, font, display overrides and keyboard settings. Both emulator runners temporarily enable virtual AC power so stay-awake works on an emulator without virtual USB power, then restore it. The regression runner now covers 20 checks.

## Evidence and acceptance

[Completion evidence](../../03-evidence/ui-ux-completion-2026-10-20/) preserves build logs, interrupted/preliminary attempts, and subsequent verification. The earlier [baseline](../../03-evidence/ui-ux-2026-10-20/), [implementation captures](../../03-evidence/ui-ux-impl-2026-10-20/) and [peer failures](../../03-evidence/ui-ux-peer-2026-10-20/) remain historical evidence.

The application build and lint succeeded. The fresh JVM report contains **175 tests across 31 suites with zero failures, errors or skips**. Device and visual outcomes are pending below; earlier passing baseline runs do not count as acceptance of this build.

Spoken TalkBack output, physical target-phone ergonomics, sunlight readability and uncoached human UAT are separate checks. Semantics assertions and screenshots are not substitutes for those checks. No Internet permission, backend, persistence schema or migration was added.

## Reproduction

From `android-native/`:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug -PisolatedTestApp=true --no-daemon --console=plain
```

From the root, run the following separately, using fresh evidence directories:

```powershell
rtk proxy powershell -NoProfile -File tools/verify-ui-ux-emulator.ps1 -DeviceSerial emulator-5554 -EvidenceDirectory planning/03-evidence/NEW-AUTOMATED-RUN
rtk proxy python tools/verify-ui-ux-visual.py --evidence-directory planning/03-evidence/NEW-VISUAL-RUN
rtk proxy node tools/check-docs.cjs
```

Update the [shared tracker](UI-UX-EMULATOR-2026-10-20.md) when evidence is accepted. Keep UX IDs, original priorities and failed attempts intact.
