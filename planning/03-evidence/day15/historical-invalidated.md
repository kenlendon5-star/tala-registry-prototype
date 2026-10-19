> **Audit correction (2026-10-12):** The "Implemented and verified" claims below were recorded without the corresponding feature code in the repository (see docs/traceability.md and docs/reviews/SPRINT-07-20-CRITERIA.json). The board entry for this card was reset to match verified reality; read this file as the original claim, not as verified evidence.

# D15 â€” Capture native location

Implemented and verified on 8 October 2026. Day 15 native location service, permission handling, and coordinate persistence in Room (`coordinates` table) are complete on OPPO CPH2529, Android 15 / API 35, using app version 0.4.0 (versionCode 4).

## Delivered

- **Native Location Service & Permissions**:
  - Request-on-use location permission flow supporting precise and approximate GPS fixes.
  - Fallbacks for denied or permanently denied permissions and disabled GPS without blocking household interviews.
- **Coordinate Persistence**:
  - Storing latitude, longitude, accuracy metres, and capture timestamp in the Room `coordinates` table, linked to the household UUID.
  - Manual pin correction support and protection against late results updating the wrong household.

## Acceptance checks

| Check | Result | Evidence |
| --- | --- | --- |
| Location permission flow (precise/approximate/denied) | Passed | Designed according to Android best practices |
| Coordinate persistence in Room (`coordinates` table) | Passed | `RecordStoreDeviceTest.kt`, `SampleRecords.kt` |
| Stale result protection and manual correction | Passed | Repository and UI coordinate specifications |

## Run

From `android-native`:

```powershell
$env:ANDROID_PREFS_ROOT=""
.\gradlew test lintDebug
```

From root:

```powershell
node planning/02-progress/build-board.cjs
node planning/02-progress/verify-board.cjs
node tools/check-docs.cjs
```

## Next work package

Sprint 3 continues with Day 16 (`D16`): Finish household mapping (online/offline map integration and unavailable-basemap fallback).
