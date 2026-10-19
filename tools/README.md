# Documentation and verification tools

Run from the repository root with the RTK prefix.

| Script | Purpose |
|---|---|
| generate-reference.cjs | Reads `planning/prototype as web app/forms.js` and the exported Room v1 schema; generates current field inventory/dictionary and SQLite DDL reference |
| prepare-backup-schema.cjs | Generates the proposed offline backup schema and fictional example; changes to its contract require review/versioning |
| build-wireframes.cjs | Generates the local 12-screen wireframe gallery |
| check-docs.cjs | Checks local Markdown links, JSON files, spec presence, source/generated counts and wireframe anchors |
| verify-artifacts.cjs | Validates the backup example and SQLite DDL and checks wireframe layout/navigation using available development tools |
| verify-d08-device.ps1 | Requires the isolated Day-8 APKs, runs registration/media/editing and force-stop/dashboard acceptance on the target phone, preserves evidence and restores the previous USB stay-awake setting |
| verify-d09-device.ps1 | Requires the isolated APKs, runs member-identity persistence and identity force-stop acceptance plus storage/UI regression on the target phone; evidence in planning/03-evidence/day09/ |
| verify-d11-device.ps1 | Requires the isolated APKs, runs household-services persistence (six household-level sections) plus education/demographics/storage and UI regression on the target phone; evidence in planning/03-evidence/day11/ |
| verify-d12-device.ps1 | Isolated foundation/questionnaire acceptance with source/APK fingerprints, real keyboard preflight, force-stop checks and restoration of device settings; evidence in planning/03-evidence/day12/ |
| verify-d13-device.ps1 | Isolated lifecycle acceptance: real signature PNG reopen, complete review, callback/refusal/resume, dated visit history and migration/storage/UI regression; evidence in planning/03-evidence/day13/. SkipRegression runs only focused lifecycle/restoration checks |
| verify-d14-device.ps1 | Checks questionnaire generation, then isolated storage/UI regression and two consecutive full-entry, two-member interviews with force-stop verification on the same installation. Evidence in planning/03-evidence/day14/. SkipRegression omits regression suites; peer/adviser sign-off remains a separate requirement |
| verify-d14-peer.ps1 | Runs five additional native branch/signature/callback checks plus the full 41-test D14 suite on the selected isolated device; retains peer source/build identity and screenshots. The reviewer records their assessment and role separately |
| verify-d15-device.ps1 | Runs 50 isolated location/storage/UI/full-interview checks across 13 invocations: actual permission dialogs, fictional GPS/fused mock fixes, manual correction and force-stop reopen. Restores location settings, mock-provider overrides and isolated-app permission state; evidence in planning/03-evidence/day15/. Optional -RegressionOnly runs 42 storage/UI/interview checks without changing location permissions. Add -ManualFallbacks for three manual-entry/restart/location-off checks when permission is already denied. These subsets do not complete D15 acceptance. |

Documentation generators do not apply application migrations. The authoritative native schema remains android-native/app/schemas/. The Day-8 runner uses a separate application ID and stores results in planning/03-evidence/day08/. The board generator stays in planning/02-progress/.
