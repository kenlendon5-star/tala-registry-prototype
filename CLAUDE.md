# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Tala — an offline-first barangay household registry capstone. Three codebases with different roles:

- `android-native/` — **the release target**: Kotlin + Jetpack Compose app (`ph.tala.registry`). All active development happens here.
- `planning/prototype as web app/` — the 18-section browser prototype (plain JS + localStorage). It is the **behavior reference**; do not package it in a WebView.
- `mobile/` — earlier Expo starter, retained but outside the active build (has its own CLAUDE.md/AGENTS.md).

Everything uses fictional data only. The app has no Internet permission and no backend; `supabase/` is placeholder tooling, not a connected backend.

## RTK command convention

All shell commands run through the RTK proxy (token-optimizing wrapper). Use `rtk proxy <cmd>` for arbitrary commands (node, gradlew, adb, powershell) and `rtk npm ...` for npm. Examples throughout this file follow this. `rtk proxy` executes the raw command unfiltered.

## Commands

Repository tooling requires Node ≥24.18.0 (see `.nvmrc`). Install pinned dev tools once from the root: `rtk npm ci --ignore-scripts`.

### Native Android (run from `android-native/`)

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug                  # build APK
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug   # JVM tests + lint
rtk proxy ./gradlew.bat :app:connectedDebugAndroidTest      # device tests (needs authorized device)
rtk proxy adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Requires SDK platform 37, Java 17; set `sdk.dir` in `android-native/local.properties` (Windows: escape the colon, `sdk.dir=C\:/...`) or `ANDROID_HOME`.
- Gradle is deliberately constrained for an 8 GB machine: one worker, parallel off, in-process Kotlin compilation. Do not undo these settings.
- Isolated device-evidence build (separate application ID `.d08test`, preserves the normal app's data): add `-PisolatedTestApp=true`, then run `rtk proxy powershell -NoProfile -File tools/verify-d08-device.ps1` (or `verify-d09-device.ps1`) from the repo root. Evidence lands in `planning/03-evidence/`.
- Device tests write screenshots to the app's external files dir (`day03/`, `day04/`); pull them before the Gradle runner uninstalls the test apps.

### Browser prototype (from repo root)

```powershell
rtk npm --prefix browser start          # localhost:5173 (required for checks)
rtk npm run check                       # browser check + docs check
rtk proxy node browser/tests/verify.cjs # also verify-map.cjs, verify-revisions.cjs
```

Browser checks need Playwright (pinned in root package.json) and Microsoft Edge.

### Docs, board, and generation tools (from repo root)

```powershell
rtk proxy node planning/02-progress/build-board.cjs   # rebuild KANBAN HTML/MD from build-board.cjs + progress JSON
rtk proxy node planning/02-progress/verify-board.cjs
rtk proxy node tools/generate-reference.cjs           # field inventory + Room DDL from browser/forms.js
rtk proxy node tools/build-wireframes.cjs
rtk proxy node tools/check-docs.cjs                   # markdown links, JSON, generated-file consistency
```

**Generated files — edit the generator's inputs, not the output:** `QuestionnaireSections.kt`, the kanban HTML/Markdown, wireframes, field dictionary, and Room DDL reference are all generated. Regenerate and never hand-edit.

## Architecture (android-native)

Package root `ph.tala.registry`, single `:app` module, three layers:

- **`domain/`** — pure Kotlin, no Android dependencies.
  - `model/`: typed household/member identities and immutable record/questionnaire models.
  - `form/FormRules`: pure validation, strict calendar parsing, exclusive multi-select, fixed-point clearing of hidden dependent answers.
  - `form/QuestionnaireSections`: all 18 section specs — **generated** by `tools/port-native-questionnaire.cjs` from `planning/docs/schemas/questionnaire-current.json` (verify with `--check`). Member-scoped sections are flagged so answers key to one member.
  - `form/InterviewRules`: reads one section's answers from a stored record, cleans hidden dependents to a fixed point, computes review issues across household and member scopes.
- **`data/`** — repositories and persistence.
  - `HouseholdRepository` (interface) + `RoomHouseholdRepository`: one mutex-serialised write stream; every save is a full-snapshot `RecordStore` write in a single Room transaction reporting `Saved` or `Failed` explicitly (a failed write is never reported as saved).
  - `local/`: Room entities (UUID identities, foreign keys), `RegistryDao`, `RegistryDatabase` (schema exported to `app/schemas/`, **no destructive migration**), `RoomRecordStore`.
  - `PhotoStore`: bounded decode/normalize on IO dispatcher, staged-file cleanup, orphan recovery.
  - `DataStoreEnumeratorProfileStore`: profile via DataStore.
  - Fictional fixtures `HH-00452`–`HH-00454` seed once per database install, idempotently.
- **`ui/`** — Compose. `TalaApp` owns navigation; `Routes` stores only the household ID string in the nav bundle (screens pass IDs, never record objects). ViewModels use `SavedStateHandle` so drafts/UUID identities survive process death. `InterviewWorkspaceViewModel` saves one section/member scope at a time with debounced auto-save, save-before-switch, and conflict refusal when the stored section changed elsewhere. `form/SectionForm` is a reusable stateless renderer.

Core invariants: explicit `Idle/Saving/Saved/Failed` states; Reviewed status only granted by completing interview review; archived households excluded from active counts and recoverable via the Archived filter; no fake save actions or completion percentages shown anywhere.

## Planning workflow

Daily work packages (D01–D30) tracked in `planning/01-boards/`. Cadence rules: at most one card In progress and one in Verify; **Done requires checked checklist items, met acceptance criteria, and recorded evidence** in `planning/03-evidence/DXX.md`. Board status is edited via `build-board.cjs` inputs (`build-board.cjs` card definitions, `implementation-progress.json`), then rebuilt. Specs in `planning/docs/` distinguish current implementation vs. target release vs. future cloud work — don't treat proposed schemas (e.g. `backup-v1.schema.json`) as applied migrations.
