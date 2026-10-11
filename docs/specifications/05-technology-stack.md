# Technology Stack Specifications — Tala

Version 1.0 · 11 October 2026. Versions below are project declarations, not claims that each is the latest available. Do not upgrade dependencies just to match a newer documentation example.

## 1. Selected delivery architecture

| Layer | Selected / current technology | Role and status |
|---|---|---|
| Native frontend | Kotlin + Jetpack Compose + Material 3 | Primary release application in android-native/ |
| State/navigation | AndroidX ViewModel, SavedStateHandle, StateFlow, Navigation Compose | Current native shell and practice workflow |
| Domain | Pure Kotlin field specifications, conditions and FormRules | Current validation/branching; full questionnaire port pending |
| Native persistence | Room 2.8.5 over SQLite | Current transactional storage; editing adapter pending |
| Media | Android app-private files + Room references | Proposed lifecycle; references exist |
| Backend for offline release | None | Core workflow must work without network or hosted API |
| Future backend | Supabase Auth + PostgreSQL + private Storage | Proposed only; no client/schema integration currently exists |
| Browser reference | HTML/CSS/vanilla JavaScript; Node built-in HTTP preview | Working behavior reference in browser/ |
| Browser maps | Vendored Leaflet 1.9.4, OpenStreetMap online raster tiles | Current; manual coordinates remain available without tiles |
| External directions | Google Maps URLs using api=1 | Current browser link integration; native launcher proposed |
| Native embedded map | DEC-07 pending | No map SDK/dependency has been selected or installed; pin version/provider before implementation |
| Release distribution | Signed APK for direct installation | Target; signing custody and exact release artifact verification pending |
| Website hosting | None configured | Browser uses localhost:5173. Public hosting is outside this document's implementation scope |

## 2. Pinned native versions

Source: [version catalog](../../android-native/gradle/libs.versions.toml), [wrapper](../../android-native/gradle/wrapper/gradle-wrapper.properties), [app build](../../android-native/app/build.gradle.kts).

| Tool/library | Declared version |
|---|---|
| Java toolchain | 17 |
| Gradle wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin Compose plugin | 2.2.10 |
| Compose BOM | 2026.02.01; resolves Compose/Material versions |
| Core KTX | 1.19.1 |
| Lifecycle | 2.11.0 |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.10.2 |
| Coroutines | 1.9.0 |
| Room | 2.8.5 |
| KSP | 2.3.6 |
| kotlinx.serialization core/json | 1.8.1 |
| JUnit | 4.13.2 |
| AndroidX test JUnit | 1.1.5 |
| Espresso | 3.5.1 |
| minSdk / compileSdk / targetSdk | 26 / 37 / 37 |
| Current app version | 0.3.0, versionCode 3 |

AGP uses built-in Kotlin support; do not apply a second org.jetbrains.kotlin.android plugin without a deliberate toolchain change. The catalog's Kotlin value pins the Compose plugin; resolve the actual compiler dependency when diagnosing compatibility. The checked-in settings limit Gradle to one worker and a 1536 MiB daemon heap for the development machine.

## 3. Other workspaces

The browser has no runtime npm dependencies; Leaflet is vendored with its license. Repository development tooling pins Node 24.18.0 (.nvmrc), Playwright 1.62.1 and Ajv 8.20.0 in the root package/lockfile. Install with `rtk npm ci --ignore-scripts`; browser checks use installed Microsoft Edge. Node's built-in SQLite support verifies the generated DDL in memory. These dependencies serve verification, not a hosted backend. A clean second-machine/CI run remains a release task.

The retained Expo starter declares Expo `~57.0.24`, React `19.2.3`, React Native `0.86.3`, Expo Router `~57.0.22`, TypeScript `~6.0.3`. Those are dependency ranges/pins as written, not all exact installed versions. It has no Tala workflows and is not part of the native release build. Its current CSS declaration type errors are documented in the review. Read its AGENTS.md before editing Expo code.

Supabase config declares PostgreSQL major 17, local API 54321, database 54322, Studio 54323, project ID Capstone. CLI 2.120.0 and Android Studio 2026.2.1 were recorded during earlier setup; these are historical environment observations, not dependencies the app ships. The Android client library for Supabase is not selected because cloud integration is outside this release.

## 4. Environment and commands

From repository root:

```powershell
rtk npm --prefix browser start
rtk npm --prefix browser run check
rtk proxy node browser/tests/verify.cjs
rtk proxy node browser/tests/verify-map.cjs
rtk proxy node browser/tests/verify-revisions.cjs
rtk proxy node planning/02-progress/build-board.cjs
rtk proxy node planning/02-progress/verify-board.cjs
rtk proxy node tools/check-docs.cjs
```

Browser tests expect the preview server to be running. Screenshots write under browser/screenshots using paths relative to each test file. Run native commands from android-native/:

```powershell
rtk proxy ./gradlew.bat :app:assembleDebug
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug
rtk proxy ./gradlew.bat :app:connectedDebugAndroidTest
```

Device tests may install/uninstall test applications; use a dedicated fictional-data test device. Keep local.properties, credentials, signing keys, generated builds and logs outside tracked source. Use logs/android/ for local logs, which remain ignored.

## 5. Dependency selection gates

Before native mapping, choose an SDK with a tested Compose bridge/lifecycle, explicit tile service terms, attribution, credential/billing model, offline failure mode and a pinned compatible version. No offline prefetch of the public OSM tile service is included. [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/).

Before cloud integration, define roles/ownership, RLS tests, conflict semantics, retries, migration compatibility and client SDK versions. Before deployment, specify hosting region, environments, retention, backup/restore and operational ownership. These are deliberate open selections, not silently installed services.

## 6. Sources and update policy

Repository catalogs and exported schema are authoritative for the current build. Official references: [Android architecture](https://developer.android.com/topic/architecture/recommendations), [Room](https://developer.android.com/training/data-storage/room), [Supabase database](https://supabase.com/docs/guides/database/overview), [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started). On dependency changes, update the catalog/lockfile, this specification, affected tests and evidence together.
