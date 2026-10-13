# Tala Project Guidelines & Rules

Rules and guardrails for AI developer agents working on the Tala Barangay Inhabitants Registry capstone application.

---

## 1. Project Stack & Architecture

- **Primary Target**: Native Android app written in Kotlin + Jetpack Compose located in `../../android-native`.
- **UI Framework**: Jetpack Compose with Material 3 components (`androidx.compose.material3`).
- **Persistence**: Room 2.8.5 (`RegistryDatabase`) with KSP code generation and exported schemas under `../../android-native/app/schemas`.
- **State Management**: ViewModels with `StateFlow` and `SavedStateHandle` for process-death recovery.
- **Form Engine**: Pure Kotlin validation rules (`FormRules`) with zero Android/Compose dependencies for fast JVM unit testing.
- **References**: The browser prototype (`browser/`) and Expo starter (`../../mobile`) are behavior/design references, not runnable native screens.

---

## 2. Tooling & Shell Guardrails

1. **Gradle Environment Rule**:
   - Always clear `ANDROID_PREFS_ROOT` when running `./gradlew` commands in PowerShell to prevent AGP 9.4.1 environment conflicts:
     ```powershell
     $env:ANDROID_PREFS_ROOT=""
     cd android-native
     .\gradlew test
     ```
2. **File Editing Prohibitions**:
   - **NEVER** use shell commands (`sed`, `awk`, `perl`, `echo >`, `cat << EOF`) to edit source files.
   - **ALWAYS** use built-in IDE tools (`write_file`, `replace_file_content`, `multi_replace_file_content`) to protect IDE memory buffers and file optimistic concurrency locks.
3. **Documentation & Board Scripts**:
   - Always run `node tools/check-docs.cjs` after editing Markdown documentation.
   - Always run `node planning/02-progress/build-board.cjs` and `node planning/02-progress/verify-board.cjs` after updating `../../planning/02-progress/implementation-progress.json`.

---

## 3. Data Integrity & Privacy Standards

- **Truthful Status Reporting**: Every edit screen must report save status (`Idle`, `Saving…`, `Saved on this device`, `Could not save on this device`). A rejected write must **never** be labeled as saved.
- **Stable UUID Identities**: Every household (`HouseholdId`) and member (`MemberId`) keys on a stable UUID. Human-readable display codes (`HH-00001`) are calculated atomically inside Room transactions.
- **Privacy & Storage Isolation**: App data lives in app-private storage (`/data/data/ph.tala.registry/`). Cloud backup is disabled (`android:allowBackup="false"`). Fictional data must be used during development.
- **Reversible Archiving**: Archiving sets `archivedAt` epoch timestamp; it never deletes member records, answers, or attachments.

---

## 4. Verification & Quality Gates

- **Unit Test Requirement**: All 30+ JVM unit tests in `android-native` must pass cleanly before completing any task.
- **Lint Standard**: `lintDebug` must report zero errors.
- **Evidence Verification**: Completed Kanban cards (`Done`) require all 3 checklist items checked `[true, true, true]`, a corresponding evidence report in `planning/03-evidence/D<XX>.md`, and a passing board verification run (`verify-board.cjs`).
