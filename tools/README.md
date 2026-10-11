# Documentation tools

Run from the repository root with the RTK prefix.

| Script | Purpose |
|---|---|
| generate-reference.cjs | Reads browser/forms.js and the exported Room v1 schema; generates current field inventory/dictionary and SQLite DDL reference |
| prepare-backup-schema.cjs | Generates the proposed offline backup schema and fictional example; changes to its contract require review/versioning |
| build-wireframes.cjs | Generates the local 12-screen wireframe gallery |
| check-docs.cjs | Checks local Markdown links, JSON files, spec presence, source/generated counts and wireframe anchors |
| verify-artifacts.cjs | Validates the backup example and SQLite DDL and checks wireframe layout/navigation using available development tools |

These tools generate documentation, not application migrations. The authoritative native schema remains android-native/app/schemas/. Browser tests stay in browser/tests/ and the board generator stays in planning/02-progress/.
