const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const evidence = path.join(root, 'planning/03-evidence/day08');
fs.mkdirSync(evidence, { recursive: true });
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const files = [];
function walk(relative) {
  for (const entry of fs.readdirSync(path.join(root, relative), { withFileTypes: true })) {
    const item = `${relative}/${entry.name}`;
    if (entry.isDirectory()) walk(item);
    else files.push(item);
  }
}
walk('android-native/app/src');
walk('android-native/app/schemas');
files.push('android-native/app/build.gradle.kts', 'android-native/gradle/libs.versions.toml', 'tools/verify-d08-device.ps1');
fs.writeFileSync(path.join(evidence, 'tested-source-sha256.json'), JSON.stringify({
  capturedAtUtc: new Date().toISOString(),
  scope: 'Native app source, instrumentation/JVM tests, exported schemas and pinned build configuration used for Day 8 acceptance.',
  files: Object.fromEntries(files.sort().map(file => [file, digest(path.join(root, file))]))
}, null, 2) + '\n');
const source = path.join(root, 'android-native/app/build/test-results/testDebugUnitTest');
const destination = path.join(evidence, 'jvm');
fs.mkdirSync(destination, { recursive: true });
let tests = 0, failures = 0, errors = 0;
for (const file of fs.readdirSync(source).filter(file => file.endsWith('.xml'))) {
  const xml = fs.readFileSync(path.join(source, file), 'utf8');
  const suite = xml.match(/<testsuite\b[^>]*>/)[0];
  tests += Number(suite.match(/\btests="(\d+)"/)[1]);
  failures += Number(suite.match(/\bfailures="(\d+)"/)[1]);
  errors += Number(suite.match(/\berrors="(\d+)"/)[1]);
  fs.copyFileSync(path.join(source, file), path.join(destination, file));
}
fs.copyFileSync(path.join(root, 'android-native/app/build/reports/lint-results-debug.txt'), path.join(evidence, 'lint-results.txt'));
console.log(`Preserved Day 8 source hashes, lint and ${tests} JVM tests (${failures} failures, ${errors} errors).`);
if (failures || errors) process.exitCode = 1;
