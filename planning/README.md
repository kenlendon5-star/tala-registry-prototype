# Native Android delivery planning

Open [ANDROID-KANBAN.html](01-boards/ANDROID-KANBAN.html) directly in a browser. It is self-contained and works offline. Read the [daily plan](01-boards/ANDROID-30-DAY-PLAN.md) for the 30-card, 164-hour baseline and acceptance gates; dates are planning assumptions, not a completion guarantee.

| Folder | Contents |
|---|---|
| 01-boards/ | Generated board, Markdown plan, editable HTML template and previews |
| 02-progress/ | Repository progress JSON, build-board.cjs and verify-board.cjs |
| 03-evidence/ | Daily acceptance records, including D08 registration/device artifacts |
| 04-utils/ | Existing ad-hoc board diagnostic script |

From repository root:

```powershell
rtk proxy node planning/02-progress/build-board.cjs
rtk proxy node planning/02-progress/verify-board.cjs
```

Edit card definitions in build-board.cjs, repository defaults in implementation-progress.json, and layout in 01-boards/board-template.html. Generated HTML and Markdown should be rebuilt, not independently edited.

Browser progress remains local and can differ from repository defaults. Export progress creates a portable JSON backup; Import progress replaces the browser's progress after confirmation. Existing browser status is not silently reset when repository defaults change. Keep one card building and one verifying; Done requires checks and evidence.

The board preserves the offline-native scope. The [current specifications](docs/README.md) add explicit requirement IDs and the [native acceptance matrix](docs/reference/native-acceptance-matrix.md) maps each requirement to code/tests and remaining gates. D01–D13 have recorded acceptance; D13 includes native interview lifecycle, signatures and dated visit history with current-build OPPO regression. D14 is accepted with the owner-authorized Codex AI peer review and 46 passing peer device checks. D15 is accepted with 166 passing JVM checks and the full 50-check phone permission matrix (denial, mocked precise/approximate fixes, location-off, timeout), so D16 unstarted is next. Human usability/UAT remains a later gate. See [D14 evidence](03-evidence/D14.md) and [D15 verification](03-evidence/D15.md).
