# Tala — Barangay Household Registry

Tala is an offline-first household enumeration capstone. The primary release target is the native Kotlin/Jetpack Compose app. The browser prototype is the working behavior reference; the Expo project is a retained starter. Use fictional data during development.

**Start here:** [Documentation index](planning/docs/README.md) · [Product requirements](planning/docs/specifications/01-product-requirements.md) · [Wireframes](planning/docs/design/wireframes.html) · [Project folder map](planning/docs/project-structure.md).

## Project areas

| Folder | Purpose / current status |
|---|---|
| [android-native](android-native/README.md) | Native registration, 18 questionnaire definitions, scoped drafts, photos, profile and Room storage; release acceptance remains in progress |
| [browser](planning/prototype as web app/README.md) | Working 18-section browser prototype; localStorage only |
| mobile/ | Earlier Expo starter; no Tala workflows; outside the active native build |
| [supabase](supabase/README.md) | Development tooling and placeholder migrations; no app backend connected |
| [docs](planning/docs/README.md) | Six specifications, schemas, wireframes, traceability and reviews |
| [planning](planning/README.md) | Delivery board, progress source, acceptance and device evidence |
| [tools](tools/README.md) | Documentation/reference generation and consistency checks |
| [logs](logs/README.md) | Local ignored build logs |

## Run the browser reference

Repository tooling uses Node 24.18.0 (see .nvmrc). Install the pinned development tools once with `rtk npm ci --ignore-scripts` from the root. Microsoft Edge must also be installed for browser checks. These tools do not install or modify the Android app.

From this repository root:

```powershell
rtk npm start
```

Open [http://localhost:5173](http://localhost:5173). The preview server binds to loopback. Records, images and signatures save in that browser, with known limitations recorded in the [system review](planning/docs/reviews/SYSTEM-ANALYSIS.md).

```powershell
rtk npm run check
rtk npm run test:browser
```

Browser scenario checks require the preview server, Playwright, and installed Microsoft Edge. They use isolated fictional records and write screenshots under `planning/prototype as web app/screenshots/`.

Root shortcuts are also available: `rtk npm start`, `rtk npm run check`, `rtk npm run docs:verify`, `rtk npm run board:build` and `rtk npm run test:browser`.

## Build native Android

Open android-native/ in Android Studio or follow its [setup and build guide](android-native/README.md). Run the Gradle wrapper from that directory. Household/member editors and the questionnaire workspace save through Room; the separate practice form remains session-only. See the [native acceptance matrix](planning/docs/reference/native-acceptance-matrix.md) for implemented slices and outstanding release gates.

## Planning and documentation

Open the [30-day board](planning/01-boards/ANDROID-KANBAN.html) directly in a browser. From the repository root:

```powershell
rtk proxy node planning/02-progress/build-board.cjs
rtk proxy node planning/02-progress/verify-board.cjs
rtk proxy node tools/generate-reference.cjs
rtk proxy node tools/build-wireframes.cjs
rtk proxy node tools/check-docs.cjs
```

See [traceability](planning/docs/traceability.md) for requirement-to-code/test coverage. Specifications distinguish current implementation, target release behavior, and future cloud work. No hosted service, schema migration, app installation, or production deployment is implied by the documents.
