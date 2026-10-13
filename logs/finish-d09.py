from pathlib import Path
import json, re, shutil, hashlib, datetime, xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
evidence = root / 'planning/03-evidence/day09'
logs = ['device-persistence.log', 'device-identity-write.log', 'device-identity-restart.log', 'device-storage-regression.log', 'device-ui-regression.log']
device_count = 0
for name in logs:
    text = (evidence / name).read_text(encoding='utf-8-sig')
    match = re.search(r'OK \((\d+) tests?\)', text)
    assert match and not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed', text), name
    device_count += int(match.group(1))
results = root / 'android-native/app/build/test-results/testDebugUnitTest'
rows = [ET.parse(f).getroot().attrib for f in results.glob('TEST-*.xml')]
count = sum(int(r['tests']) for r in rows)
assert count >= 64 and all(int(r['failures']) == 0 and int(r['errors']) == 0 and int(r['skipped']) == 0 for r in rows)
assert 'BUILD SUCCESSFUL' in (evidence / 'build-final.log').read_text(encoding='utf-8-sig')
report = root / 'android-native/app/build/reports/lint-results-debug.xml'
issues = ET.parse(report).getroot().findall('issue')
assert not any(i.get('severity') in ['Error', 'Fatal'] for i in issues)
warnings = sum(i.get('severity') == 'Warning' for i in issues)
(evidence / 'jvm').mkdir(exist_ok=True)
for f in results.glob('TEST-*.xml'): shutil.copy2(f, evidence / 'jvm' / f.name)
shutil.copy2(report, evidence / report.name)
shutil.copy2(report.with_suffix('.txt'), evidence / 'lint-results.txt')
paths = sorted((root / 'android-native/app/src').rglob('*.kt'))
paths += [root / p for p in ['android-native/app/build.gradle.kts', 'android-native/app/src/main/AndroidManifest.xml', 'android-native/gradle/libs.versions.toml', 'tools/port-native-questionnaire.cjs', 'tools/verify-d09-device.ps1']]
fingerprints = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
(evidence / 'tested-source-sha256.json').write_text(json.dumps({'verifiedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(), 'files':fingerprints}, indent=2)+'\n', encoding='utf-8')
note = f'2026-10-13: Day 9 identity acceptance complete on OPPO CPH2529 / Android 15 / API 35, app 0.5.0 (5). Demographics, Other demographics and Migration pass positive/negative validation, conditional clearing, two-member isolation, save-before-switch, failed-save retry, stale-editor conflict refusal, real Room reopen, actual questionnaire UI and force-stop recovery. Fixed build/test defects and separated member photo ownership. Verified {count} JVM tests, {device_count} device tests and lint (0 errors, {warnings} warnings). Evidence: planning/03-evidence/D09.md and planning/03-evidence/day09/ with test matrix, logs, screenshots and source/APK identities. D10 onward remains separate acceptance work.'
p = root / 'planning/02-progress/implementation-progress.json'
d = json.loads(p.read_text(encoding='utf-8'))
d['D09'] = {'status':'Done','checks':[True,True,True],'notes':note}
p.write_text(json.dumps(d, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
doc = f'''# D09 — Complete member identity

Completed and verified on 13 October 2026, OPPO CPH2529 / Android 15 / API 35, app **0.5.0 (versionCode 5)**. This record supersedes the unsupported earlier completion claim, preserved in [superseded-D09.txt](day09/superseded-D09.txt), and the October 12 assertion that these native sections were absent.

## Delivered

- Demographics, Other demographics and Migration use `QuestionnaireSections`, `FormRules`, the shared Compose controls and the real interview workspace.
- Answers remain keyed by interview, member, section and field. Save-before-switch, failed-write draft retention and stale-editor conflict refusal are exercised through the actual ViewModel/repository.
- Disability No clears registered PWD, PWD ID and description. Unregistering PWD clears only the ID. Leaving solo-parent or moved branches clears dependent values without altering another member or resurrecting old answers.
- Voter registration and overseas residence are required Yes/No choices in the reference inventory, with no additional dependent questions. Solo-parent ID stays optional. Migration year retains the reference inventory's 1900–2026 range; changing that questionnaire range is separate future maintenance.
- Repaired malformed strings, incomplete photo interfaces and non-compiling test doubles. Member pictures use their own attachment kind and retain the member identity captured when the picker opens. Removed the broken attempt to copy the enumerator picture into the household picture while holding the repository mutex; enumerator pictures retain the established DataStore/profile workflow.
- The instrumentation persistence test now lives in `src/androidTest`. Replaced the validation helper that omitted parent answers, the practice-only/wrong-package isolation tests, and incorrect fixture counts and row-order assumptions.

## Member identity acceptance matrix

| Requirement | Positive and negative cases | Passing evidence |
|---|---|---|
| Birth date | Past, today and leap day accepted; blank, impossible and future rejected | `DemographicSectionsValidationTest`; `IdentityDeviceTest` |
| Voter registration | Yes/No accepted; blank/unlisted choice rejected; distinct values per member | Validation, member-isolation and identity device tests |
| Solo parent | Optional ID visible for Yes; hidden/cleared for No; reversal does not restore old ID | Validation, branching and identity device tests |
| Disability → registered PWD → ID | All parent combinations; missing visible required answers rejected; valid ID/description accepted; cascade/partial clearing | Validation, branching, member-isolation and identity device tests |
| Migration | Required citizenship; moved Yes requires residence and bounded integer year; moved No hides/clears both | Validation, branching, persistence and identity device tests |
| Overseas | Explicit Yes/No required; blank/unlisted values rejected; independent of moved; distinct saved member values | Validation, branching and identity device tests |
| Member switching | Two members in all three sections; save before switching; failed save holds draft/member; stale editor cannot overwrite | `DemographicSectionsMemberIsolationTest`, `InterviewSectionsViewModelTest` |
| Real Room | Save through production repository, close/reopen database, compare by keys, retain other member while clearing one | `DemographicSectionsPersistenceTest` |
| Actual app/restart | Enter two members via normal navigation; show errors; reverse branches; recreate activity; force-stop and reopen; inspect stored and displayed values | `IdentityDeviceTest`, write/restart logs and screenshots |
| Adjacent media/storage | Member/household picture isolation; failed replacement preserves copy; successful replacement cleans old file; Room reopen and existing storage/UI regression | `InterviewAnswersRepositoryTest`, `RegistrationDeviceTest`, regression logs |

## Verified results

- **{count} JVM tests passed**, zero failures/errors/skips. [Retained XML results](day09/jvm/TEST-ph.tala.registry.domain.form.DemographicSectionsValidationTest.xml).
- **{device_count} device tests passed**: identity persistence, questionnaire UI, force-stop recovery, storage/media/migration regression and shell/form/editing regression. [Identity write](day09/device-identity-write.log), [restart](day09/device-identity-restart.log), [Room](day09/device-persistence.log), [storage regression](day09/device-storage-regression.log), [UI regression](day09/device-ui-regression.log).
- Debug and instrumentation APKs built; lint passed with **0 errors / {warnings} warnings**. [Final build](day09/build-final.log), [lint](day09/lint-results.txt).
- Questionnaire generator parity, board behavior and documentation checks passed; [planning checks](day09/planning-checks.log).
- [Exact tested APK/device identity](day09/device-build.txt), [normal APK identity](day09/normal-apk.json), [tested source SHA-256](day09/tested-source-sha256.json).
- Screenshots: [Maria's cleared branches](day09/day09/01-cleared-maria.png), [Juan's retained answers](day09/day09/02-preserved-juan.png), [migration](day09/day09/03-migration.png), [after force-stop](day09/day09/04-after-force-stop.png).

The first device run failed because error labels belong to the unmerged Compose semantics tree. The selector was corrected and rerun; `prior-failed-*` logs retain intermediate failures. Earlier APKs or passing Day 8 tests are not substituted for this acceptance run.

## Reproduction and scope

See [commands and runner](day09/README.md). Acceptance uses the separate `ph.tala.registry.d08test` package, preserving normal app records. The normal `ph.tala.registry` APK is built from the same source and identified separately.

Day 10 is the next work package. Education/employment, remaining section-specific acceptance, full release regression, second-configuration acceptance and UAT remain separate gates.
'''
(root / 'planning/03-evidence/D09.md').write_text(doc, encoding='utf-8')
p = root / 'android-native/README.md'
s = p.read_text(encoding='utf-8').replace('the later section-specific acceptance gates remain on D09–D14', 'Day 9 identity acceptance is complete; the later section-specific gates remain on D10–D14')
s = s.replace('`../planning/prototype as web app/forms.js`', '`../planning/docs/schemas/questionnaire-current.json`')
s += '\n- [Day 9 acceptance record](../planning/03-evidence/D09.md): member identity validation, branch clearing, per-member Room saves and actual questionnaire recovery after force-stop. Run `rtk proxy powershell -NoProfile -File tools/verify-d09-device.ps1` from the root after building with `-PisolatedTestApp=true`.\n'
p.write_text(s, encoding='utf-8')
p = root / 'planning/docs/traceability.md'
s = p.read_text(encoding='utf-8')
s = s.replace('## Current review defect backlog', '13 October 2026: D09 identity acceptance is complete. Demographics, Other demographics and Migration now have a passing positive/negative member-identity matrix, real Room persistence, two-member questionnaire UI and force-stop recovery evidence. See [D09 acceptance](../03-evidence/D09.md). D10–D20 remain Backlog; the dated October 12 snapshot above is historical.\n\n## Current review defect backlog')
p.write_text(s, encoding='utf-8')
p = root / 'planning/README.md'
s = p.read_text(encoding='utf-8').replace('D01–D06 completion does not mean the whole application is ready.', 'D01–D09 are complete; D10 is the next work package. This does not mean the whole application is ready. See [Day 9 acceptance](03-evidence/D09.md).')
p.write_text(s, encoding='utf-8')
print(note)
