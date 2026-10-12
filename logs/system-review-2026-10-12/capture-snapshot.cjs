const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const destination = path.join(__dirname, 'source-snapshot');
const files = [];
function capture(relative) {
  const absolute = path.join(root, relative);
  if (fs.statSync(absolute).isDirectory()) {
    for (const name of fs.readdirSync(absolute)) capture(path.join(relative, name));
    return;
  }
  const bytes = fs.readFileSync(absolute);
  const target = path.join(destination, relative);
  fs.mkdirSync(path.dirname(target), {recursive:true});
  fs.writeFileSync(target, bytes);
  files.push({path:relative.replaceAll('\\','/'),sha256:crypto.createHash('sha256').update(bytes).digest('hex')});
}
for (const relative of ['android-native/app/src','android-native/app/build.gradle.kts','planning/02-progress/implementation-progress.json','planning/02-progress/verify-board.cjs','planning/01-boards/ANDROID-30-DAY-PLAN.md']) capture(relative);
const manifest={capturedAt:new Date().toISOString(),baseCommit:'01e4591',note:'Working tree changed externally during review. Snapshot is source evidence, not a passing build.',files};
fs.writeFileSync(path.join(__dirname,'source-manifest.json'),JSON.stringify(manifest,null,2));
console.log(JSON.stringify({capturedAt:manifest.capturedAt,files:files.length}));
