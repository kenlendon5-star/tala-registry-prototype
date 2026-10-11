# Project structure and organization record

Organized 11 October 2026. Existing project roots were retained so Gradle, Android Studio, Expo dependencies and Supabase tooling paths remain stable.

```text
Capstone/
├── README.md                     # Entry point, status and commands
├── package.json, package-lock.json # Pinned repository verification tools/commands
├── .nvmrc                        # Node version for documentation/browser tooling
├── android-native/               # Primary Kotlin/Compose Android application
│   ├── app/src/main/             # UI, domain and local persistence
│   ├── app/src/test/             # JVM tests
│   ├── app/src/androidTest/      # Device/storage tests
│   ├── app/schemas/              # Authoritative exported Room schemas
│   └── gradle/                   # Wrapper and pinned version catalog
├── browser/                      # Working browser behavior reference
│   ├── index.html, *.js, style.css, server.cjs
│   ├── tests/                    # Playwright verification scripts
│   ├── screenshots/              # Browser previews and test captures
│   └── vendor/                   # Leaflet plus license
├── mobile/                       # Retained Expo starter; not native release source
├── supabase/                     # CLI configuration, placeholder migrations, setup
├── docs/
│   ├── specifications/           # The six requested documents
│   ├── schemas/                  # Generated inventory/DDL and proposed backup schema
│   ├── design/                   # Linked HTML wireframes
│   ├── reviews/                  # Dated system analysis and evidence
│   ├── reference/                # Historical plans/revision notes
│   ├── traceability.md
│   └── project-structure.md
├── planning/
│   ├── 01-boards/                # Generated kanban/plan, template and previews
│   ├── 02-progress/              # Progress JSON, generator and verification
│   ├── 03-evidence/              # D01–D06 acceptance and device evidence
│   └── 04-utils/                 # Existing board diagnostic utility
├── tools/                        # Documentation generation/verification
└── logs/android/                 # Local build logs; *.log stays ignored
```

## Moves and compatibility changes

| Old location | New location |
|---|---|
| docs/ANDROID-PLAN.md, BUILD-PLAN.md, GEOMAPPING.md, PROTOTYPE-UPDATE.md, REVISION-NOTES.md | docs/reference/ |
| docs/SYSTEM-ANALYSIS.md and evidence JSON | docs/reviews/ |
| browser/verify*.cjs | browser/tests/ |
| browser/preview-*.png | browser/screenshots/ |
| Root build-day05*.log | logs/android/ |

All moves preserve file contents before the documented path updates. No application database, local browser storage, native package identity, Supabase project, or installed application was moved. Existing staged changes were retained; the completed organization, including its one-time helper scripts being removed after use, was committed together and not pushed.

Root tooling now pins Ajv and Playwright with a lockfile instead of depending on ancestor-installed packages. The root npm scripts provide familiar start/check/documentation/board commands without combining the Android and Expo dependency trees.

Browser tests now resolve screenshot destinations from their own file location, so they work from the root or browser directory. Readmes and evidence links use the existing numbered planning folders. The malformed D01 progress string was repaired without changing card statuses or acceptance meaning, then the board was regenerated.

## Placement rules

Put product/engineering decisions in specifications; dated observations in reviews; test evidence in planning/03-evidence; generated outputs beside their owning project. Keep transient logs in logs/, generated native builds in ignored app/build/, and secrets/signing material outside tracked source. Avoid adding another app framework or root folder for a feature that belongs in the active Android project.
