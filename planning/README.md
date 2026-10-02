# Tala native Android delivery plan

Open **ANDROID-KANBAN.html** directly in a browser. It is self-contained and works offline.

- The plan is a **Kotlin + Jetpack Compose rewrite in Android Studio**, with Room for local data.
- Thirty calendar days, initially September 29–October 28, 2026; change the start date in the board.
- Four sprint goals, thirty daily cards, ninety checklist tasks, dependencies and acceptance evidence.
- One experienced developer, 164 focused hours. This is an aggressive offline capstone scope; learning time or production backend work needs a revised schedule.
- Expand a card, complete its checklist, record evidence, then change its status. WIP is limited to one card building and one verifying.
- Browser progress is local. Use **Export progress** for a backup and **Import progress** to restore it. Imports replace the current browser's progress after confirmation.
- The Markdown plan is the complete static baseline; browser changes do not modify it.

Files:

- `ANDROID-KANBAN.html`: interactive board.
- `ANDROID-30-DAY-PLAN.md`: daily plan, scope, sprint gates, cadence, risks and release checklist.
- `build-board.cjs`: editable plan data and Markdown generator.
- `board-template.html`: board UI template.
- `verify-board.cjs`: focused board verification using the existing Playwright/Edge setup.

Rebuild after editing the plan or template: `rtk proxy node planning/build-board.cjs` from the repository root. Verify with `rtk proxy node planning/verify-board.cjs`.

The board does not create or install an Android app. Native implementation begins with D01/D02. Keep the existing web prototype and Expo starter as references; the proposed new project directory is `android-native/`.
