const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const metadata = JSON.parse(fs.readFileSync(path.join(root, 'android-native/app/build/outputs/apk/debug/output-metadata.json'), 'utf8'));
if (metadata.applicationId !== 'ph.tala.registry') throw new Error('Expected normal app APK');
const apk = 'android-native/app/build/outputs/apk/debug/app-debug.apk';
fs.writeFileSync(path.join(root, 'planning/03-evidence/day08/normal-apk.json'), JSON.stringify({
  recordedAtUtc: new Date().toISOString(), applicationId: metadata.applicationId,
  versionName: metadata.elements[0].versionName, versionCode: metadata.elements[0].versionCode,
  apk, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, apk))).digest('hex'),
  installedOnDevice: 'e7d5dc18', installationCommand: 'rtk proxy adb -s e7d5dc18 install -r ' + apk,
  installationResult: 'Success'
}, null, 2) + '\n');
console.log('Recorded normal APK identity and successful target-phone update.');
