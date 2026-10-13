const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const file = path.join(root, 'planning/02-progress/implementation-progress.json');
const progress = JSON.parse(fs.readFileSync(file, 'utf8'));
progress.D08 = {
  status: 'Done', checks: [true, true, true],
  notes: '2026-10-12: Day 8 registration acceptance complete on OPPO CPH2529 / Android 15 / API 35. Native setup/visit/household/member workflow, structured identity consistency, optional contact/previous city/house-safety fields, Android Photo Picker household/enumerator pictures, bounded app-private normalized copies with replace/remove/failure cleanup, durable DataStore profile and creation-time attribution, and live household/member/Male/Female/unrecorded counts are implemented. Fixed database reads on every keystroke, photo retry gating and draft preservation during writes. Final verification: 48 JVM tests and 12 isolated-device tests pass, including member add/edit/remove, picture selection/replace/remove/cancel, profile/picture persistence, force-stop recovery and the displayed dashboard counts after restart; lint 0 errors / 14 version notices. Evidence: planning/03-evidence/D08.md and planning/03-evidence/day08/ (logs, APK/device identity, source fingerprints, JVM XML and lint). All 18 definitions exist, but D09–D14 section-specific acceptance is not inferred from this registration gate.'
};
fs.writeFileSync(file, JSON.stringify(progress, null, 2) + '\n');
console.log('D08 set to Done with all three checks and verified acceptance evidence.');
