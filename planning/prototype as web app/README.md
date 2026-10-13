# Browser behavior reference

This is the working Tala prototype: 18 sections, household/member editing, local persistence, pictures/signatures, callbacks, review, JSON export and geomapping. It is separate from the Kotlin Android app.

From repository root: `rtk npm --prefix browser start`, then open [localhost:5173](http://localhost:5173). From this folder: `rtk npm start`.

| Location | Contents |
|---|---|
| forms.js | Declarative questionnaire fields/options/conditions |
| app.js | Seed records, helpers and navigation definitions |
| workflow.js | Rendering, saving, review, media and record actions |
| geomapping.js | Leaflet, coordinates, location capture and removal |
| tests/ | Playwright verification scripts |
| screenshots/ | Preview and verification images |
| vendor/ | Leaflet 1.9.4 and its license |

Run `rtk npm --prefix browser run check` from root. Install root development dependencies with `rtk npm ci --ignore-scripts`. With the server running, use `rtk proxy node browser/tests/verify.cjs`, `rtk proxy node browser/tests/verify-map.cjs` and `rtk proxy node browser/tests/verify-revisions.cjs`. Playwright is pinned in the root package/lockfile; checks use installed Microsoft Edge.

The [dated review](../docs/reviews/SYSTEM-ANALYSIS.md) records open data-integrity issues. The [target specifications](../docs/README.md) do not claim those fixes are implemented. JSON downloads are legacy exports; the proposed versioned backup contract is a future native feature.
