# Tala UI prototype

Native Android roadmap: [30-day kanban board](planning/ANDROID-KANBAN.html) and [daily Kotlin + Jetpack Compose plan](planning/ANDROID-30-DAY-PLAN.md). See [board usage](planning/README.md) for progress tracking.

Start: `npm start`, then visit http://localhost:5173.

All 18 prototype sections are interactive, including per-member Demographics, Education and Employment.

Read [PROTOTYPE-UPDATE.md](PROTOTYPE-UPDATE.md) for current functionality and verification, and [BUILD-PLAN.md](BUILD-PLAN.md) for initial research and proposed production architecture.

Fictional demo data only. Browser-local storage; no production backend or authentication.

Syntax check: `npm run check`.
Browser verification: `node verify.cjs` (requires Playwright and installed Microsoft Edge).

Household removal and geomapping are available. See [GEOMAPPING.md](GEOMAPPING.md) for research, map providers, usage, and testing. Run `node verify-map.cjs` for the location/removal checks.
