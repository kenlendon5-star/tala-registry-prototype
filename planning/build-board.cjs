const fs = require('node:fs');
const path = require('node:path');
const days = [
  [1,'Agree on the release','Lock a realistic Android capstone scope.',[
    'Inventory all 18 sections, mapping, household/member editing, callbacks, signatures, photos and exports against the current prototype.',
    'Record the chosen native Kotlin + Jetpack Compose architecture, demo-only scope, one developer, target phone, installation method and reviewer availability.',
    'Create a requirements-to-test matrix; record unresolved questionnaire meanings and sample evacuation choices without inventing answers.'
  ],'A signed-off scope checklist, target-device list and acceptance matrix exist. Each required feature has a test ID.',[],6,'Scope checklist + requirements matrix'],
  [2,'Set up Android Studio','Make the Android toolchain reproducible.',[
    'Create an Empty Activity Kotlin/Compose project in a new android-native directory using Android Studio; choose an app ID and device-compatible minimum SDK.',
    'Connect a physical phone with USB debugging; create a second emulator/device configuration. Verify Android Studio, SDK, JDK and Gradle compatibility.',
    'Pin dependencies with a version catalog and Compose BOM; build the empty app, record setup instructions and preserve the web prototype/Expo starter as references.'
  ],'The phone and emulator are visible in Android Studio; the documented toolchain works and baseline failures are recorded.',[1],6,'Setup guide + device evidence'],
  [3,'Build the native app shell','Establish navigation and state ownership.',[
    'Create the Tala Material 3 theme, Compose home/household-list shells and navigation routes with typed record IDs.',
    'Set up ViewModels, immutable UI state, coroutines/StateFlow and repository interfaces; use a small single-module package structure.',
    'Install a debug APK and verify offline startup, system insets, keyboard resize, Android Back and state restoration with placeholder data.'
  ],'A native Compose APK cold-starts offline and navigates between home, list and interview shells without a WebView or laptop server.',[2],6,'Native debug APK + navigation recording'],
  [4,'Build reusable Compose form controls','Make the 18-section rewrite feasible.',[
    'Translate the field specification from forms.js into Kotlin models: text, phone, number, date, date/time, choice, select, multi-select and conditional predicates.',
    'Build labeled Compose controls, a reusable section renderer, inline errors and household/member-scoped answer state. Keep specialized photo/signature UI for later cards.',
    'Create focused unit/Compose tests for required/optional fields, limits, conditional clearing, error focus and switching member IDs.'
  ],'One representative section renders and validates each shared input type, including a conditional field and two distinct member states.',[3],6,'Form renderer + rule tests'],
  [5,'Build the Room database','Prove native persistence on the target device.',[
    'Define Room entities/relations and DAO/repository contracts for households, members, visits, scoped answers, coordinates and attachment references with stable UUIDs.',
    'Implement Room transactions, schema export and a migration test; demonstrate reopen, rollback and failed-write reporting on the phone.',
    'Document the ERD, app-private attachment strategy and backup policy. Keep small settings in DataStore; do not use destructive database migration.'
  ],'A small saved record survives force-stop and reboot; a failed transaction rolls back and reports failure.',[3],6,'Schema diagram + storage spike evidence'],
  [6,'Connect native editing and saves','Finish the first durable household/member slice.',[
    'Implement Compose household/member add and edit screens backed by Room create/read/update/archive operations and transactions.',
    'Connect ViewModel save/resume flows and lifecycle-aware state collection; show saving, saved and failed states and protect record identity.',
    'Add focused repository tests and verify two households do not overwrite one another. Keep fictional seed records explicit and optional.'
  ],'Household edits and member edits reload correctly after process death; the UI never labels a failed write as saved.',[4,5],6,'Repository implementation + persistence tests'],
  [7,'Review the foundation','Close week one with a runnable vertical slice.',[
    'Demonstrate launch → create household → add member → save → force-stop → resume on the target phone.',
    'Review setup, schema and navigation evidence; fix the highest-risk foundation issue within this timebox.',
    'Update the board and re-estimate remaining work; keep the rest of the day free for recovery/rest.'
  ],'Week-one gate passes: an installable offline build and durable household/member slice. Otherwise block dependent features and replan.',[6],2,'Week-one demo + issue list'],
  [8,'Complete household registration','Carry the revised household workflow into the APK.',[
    'Implement the full native setup, visit, household and member-list sections, including structured names, optional contact fields, previous city and house-safety reason.',
    'Implement enumerator and household pictures with Android Photo Picker, app-private copies, size limits, replace/remove behavior and failure cleanup.',
    'Build the home summary and enumerator profile; test member editing, household counts and total/male/female/unrecorded-sex counts after restart.'
  ],'A household with multiple members and pictures can be created, edited and resumed with correct live counts.',[7],6,'Registration test evidence'],
  [9,'Complete member identity','Preserve per-person answers and conditional identity fields.',[
    'Implement Demographics, Other demographics and Migration using the Kotlin field definitions and shared Compose renderer; use two members with distinct answers.',
    'Test birth date, voter registration, solo-parent ID, disability → registered PWD → PWD ID, migration and overseas branches.',
    'Extract/test branching and validation logic; confirm hidden dependent answers clear and member switching never leaks values.'
  ],'Each identity branch passes positive/negative tests and stored answers remain attached to the correct member.',[8],6,'Member identity test matrix'],
  [10,'Complete education and employment','Keep school and work questions accurate on mobile.',[
    'Implement Education with attendance branches, highest grade, SNED/SPED choices and educational assistance.',
    'Implement Employment with the seven-day pay/profit question, work details, hours limits, job-seeking and availability branches.',
    'Test no/yes transitions, optional fields, empty required fields, phone formatting and error focus without losing edits.'
  ],'Both sections pass on two different members, including branch reversal and restart recovery.',[9],6,'Education/employment evidence'],
  [11,'Complete household services','Finish six household-level questionnaire sections.',[
    'Implement Health, Food security and Transport/financial accounts with the shared renderer, including conditions and multi-select behavior.',
    'Implement Internet/safety, Water/sanitation and Housing, including numeric limits and exclusive None choices where specified.',
    'Add representative validation cases and compare saved/reloaded answers with the requirements matrix.'
  ],'All six sections save, reload and validate correctly; household fields do not become member-scoped.',[10],6,'Household services evidence'],
  [12,'Complete disaster and protection','Preserve the latest questionnaire revisions.',[
    'Implement Disaster preparedness: disaster type, hazards, evacuation-known branching, sample site labels, Other text and emergency-kit answers.',
    'Implement Social protection: PhilHealth member/dependent, DSWD, 4Ps and other assistance branches without silently resolving ambiguous source notes.',
    'Cross-check all 18 sections against the inventory, document approved labels and keep unresolved real-world choices visibly marked for the demo.'
  ],'Every section has an implementation/test entry; disaster/protection dependent answers clear correctly.',[11],6,'18-section coverage matrix'],
  [13,'Finish interviews and callbacks','Complete the end-to-end interview lifecycle.',[
    'Implement visit-result actions, callback list/date scheduling, refusal/no-respondent paths and resuming a partial interview; notifications are outside scope.',
    'Implement review, jump-to-error, progress calculation and explicit local completion status; do not label local completion as server synchronization.',
    'Build a Compose signature pad with drawn/typed modes, clear/redo and saved attachment handling; implement consent/refusal behavior and test reopening.'
  ],'One complete and one refused/callback interview follow the agreed rules; saved signatures survive reopen.',[12],6,'Interview lifecycle recording'],
  [14,'Review questionnaire coverage','Freeze required form behavior.',[
    'Run a scripted interview covering all 18 sections and at least two members.',
    'Have a peer/adviser review labels and flow; log approved corrections separately from new scope.',
    'Update the acceptance matrix and use remaining time for rest; plan unresolved defects before adding features.'
  ],'Week-two gate passes: every required questionnaire section works offline with correct scope and validation.',[13],2,'Reviewer notes + coverage sign-off'],
  [15,'Capture native location','Make location permissions and GPS outcomes explicit.',[
    'Implement a Kotlin location service and request-on-use permission flow; select a device-compatible provider and expose location state to the ViewModel.',
    'Store coordinates, accuracy and capture time; provide manual correction and ignore stale results after switching households.',
    'Test precise/approximate access, denied/permanently denied permissions, GPS off and timeout on the target phone.'
  ],'Permission denial never blocks the interview; coordinates update only the intended household and retain accuracy/time.',[14],6,'Permission matrix + GPS recording'],
  [16,'Finish household mapping','Keep mapping useful when connectivity disappears.',[
    'Integrate a native Android map SDK selected for licensing, availability and budget; port Leaflet behavior into markers, selection and manual pin adjustment. Configure any map credential restrictions.',
    'Show a clear unavailable-basemap state while retaining coordinates and forms offline; keep the existing boundary labeled approximate.',
    'Keep map attribution visible and verify provider terms; do not add offline tile downloads without a permitted source.'
  ],'Airplane mode leaves saved coordinates accessible; no missing tiles or external-map return loses household edits.',[15],6,'Online/offline map evidence'],
  [17,'Export records on Android','Produce a usable, deliberate record export.',[
    'Implement Android Storage Access Framework create-document export and optional Sharesheet sharing for selected records, including URI permission handling.',
    'Use Kotlin serialization for a versioned JSON backup format containing IDs, answers, coordinates and recoverable photos/signatures; set documented size limits.',
    'Test user cancellation, large attachments and write errors; confirm the exported payload contains only selected records.'
  ],'A phone export opens correctly on another machine, is versioned and includes all data needed for a round-trip restore.',[14],6,'Sample fictional export + export tests'],
  [18,'Restore and migrate safely','Prove records can survive backup restoration and app updates.',[
    'Validate JSON format, size and schema version before import; reject malformed or unsupported data without modifying the database.',
    'Implement explicit import preview and duplicate-ID handling with transactional rollback; document browser-to-app transfer as an explicit operation.',
    'Test export → clean app → restore, and an app upgrade with a schema migration; compare record/attachment counts and representative values.'
  ],'Round-trip backup and upgrade retain complete records; failed imports leave existing data intact.',[6,17],6,'Restore comparison + migration evidence'],
  [19,'Add reversible household removal','Make record maintenance predictable.',[
    'Replace immediate permanent household removal with confirmed archive and a restore path for the demo.',
    'Verify archived records leave active counts/maps while their members and attachments remain recoverable.',
    'Test archive, undo/restore, search/filter behavior and export selection after restart; specify any permanent-delete policy as future work.'
  ],'Archiving the wrong household can be reversed; active totals and maps stay consistent after every transition.',[16,18],6,'Archive/restore test evidence'],
  [20,'Harden lifecycle and accessibility','Remove failures caused by everyday phone use.',[
    'Test background/resume, rotation, external-app return, low storage and process death during editing; protect the last confirmed save.',
    'Check TalkBack labels, focus order, text scaling, contrast and approximately 48dp interactive touch targets.',
    'Generate a fictional performance dataset, provisionally 200 households/1,000 members; record startup/list/map timings on the target phone and fix blockers.'
  ],'No confirmed saved record is lost; all critical actions are usable with large text and TalkBack. Record measured performance against Day-one targets.',[19],6,'Lifecycle/accessibility/performance report'],
  [21,'Review feature completeness','Freeze the release candidate feature set.',[
    'Demonstrate an offline interview, GPS denial/manual location, export/restore and archive/restore.',
    'Review open issues and classify release blockers versus cosmetic changes; freeze features.',
    'Confirm UAT participants and book the final demo; protect the remaining day for rest.'
  ],'Week-three gate passes: the agreed offline feature set is complete and release blockers have owners and reproduction steps.',[20],2,'Feature-freeze checklist'],
  [22,'Run full regression','Verify the complete app with repeatable evidence.',[
    'Run Gradle unit tests, Android lint and connected Compose/instrumentation tests; use existing browser scenarios as behavior references, not as native-app verification.',
    'Run focused Kotlin branching, Room persistence/failed-write/migration and restore tests plus the 18-section coverage matrix.',
    'Execute device tests on the target phone and second configuration; record build version, device, scenario, result and evidence.'
  ],'Every required test has a pass/fail outcome; no untested feature is marked complete.',[21],6,'Versioned regression report'],
  [23,'Conduct user acceptance','Observe a user finishing real demo scenarios.',[
    'Give a peer/adviser fictional scenarios for registration, multiple members, callback, completion, map correction and export.',
    'Observe task completion without coaching; collect usability issues and requirement mismatches.',
    'Triage defects into release blockers, important fixes and later enhancements; agree on acceptance evidence and owners.'
  ],'At least one reviewer completes the scenario set; the UAT log contains results and prioritized actionable feedback.',[22],6,'UAT report + reviewer feedback'],
  [24,'Fix release blockers','Resolve failures before packaging the final build.',[
    'Fix crashes, data loss, broken required flows and severe validation/accessibility defects first.',
    'Retest every changed path and adjacent affected scenarios; link results to defect IDs.',
    'Review manifests, permissions, logs, bundled demo data and backup behavior; remove debugging secrets/configuration and verify no production credentials are packaged.'
  ],'There are zero open crash, data-loss or required-workflow blockers; resolved defects have retest evidence.',[23],6,'Closed blocker log + retest results'],
  [25,'Prepare the signed release','Create a reproducible Android Studio release candidate.',[
    'Set app name, icon, versionCode/versionName and release settings; verify the chosen distribution requirements at release time.',
    'Create/manage the signing key outside source control, record secure custody and build a signed release APK in Android Studio.',
    'Install the signed APK fresh and over the previous compatible signed build; run offline startup, save/resume and export smoke tests.'
  ],'A signed, non-debuggable release candidate installs and upgrades without losing records; the build steps and signing-key custodian are documented.',[24],6,'Signed RC APK + release build instructions'],
  [26,'Reserve contingency time','Absorb device, database or signing surprises.',[
    'Use this protected day only for defects blocking release, installation, migration or agreed acceptance.',
    'If no blockers remain, repeat the end-to-end smoke test on the second configuration and rehearse recovery.',
    'Update actual effort and release risk; defer cosmetic additions rather than consuming release gates.'
  ],'Remaining blockers are closed and retested, or the release is explicitly held with a revised date.',[25],6,'Contingency log + release decision'],
  [27,'Finish capstone documentation','Make the project understandable and maintainable.',[
    'Complete problem/objectives, scope, architecture, ERD/data dictionary, use cases and implementation chapters with screenshots from the actual build.',
    'Write installation, enumerator workflow, backup/restore, troubleshooting and developer setup guides; include test/UAT results and limitations.',
    'Prepare defense slides/demo script and a requirements-to-feature-to-test traceability appendix. Use only fictional records in artifacts.'
  ],'A new reader can build/install the app and execute the main workflow using the handover pack; claims match measured evidence.',[22,23,25],6,'Capstone report + user/developer guides + demo script'],
  [28,'Run the release rehearsal','Practice the exact handover before the final build.',[
    'Install the release candidate on a clean test device and follow the user guide without developer assistance.',
    'Rehearse the defense/demo with airplane mode, a callback, multi-member validation, mapping and export/restore.',
    'Record final corrections and reviewer feedback; leave the remainder of the day as recovery time.'
  ],'Week-four gate passes: the APK, documentation and demonstration agree; only bounded final corrections remain.',[26,27],2,'Rehearsal checklist + reviewer notes'],
  [29,'Finalize and verify the release','Produce the final tested deliverable.',[
    'Apply bounded final corrections; rerun affected tests and the signed-build smoke checklist.',
    'Build the final signed APK, record its version/hash, tag the matching source revision and archive the test report and release notes.',
    'Verify fresh install, upgrade, offline interview and backup/restore on that exact APK; hold release if any required gate fails.'
  ],'The exact final APK passes all release gates and is traceable to source, build instructions and evidence.',[28],6,'Final signed APK + source tag + release checklist'],
  [30,'Deliver and hand over','Close the agreed project scope.',[
    'Deliver the APK, source/setup instructions, fictional backup, documentation, test evidence, known limitations and demo materials.',
    'Walk the reviewer through install, interview, location, export/restore and recovery; record acceptance or concrete remaining issues.',
    'Confirm source and signing-key custody, maintenance ownership and a separate backlog for production accounts, secure storage, sync and supervisor review.'
  ],'The reviewer accepts the agreed offline capstone scope; deliverables and maintenance ownership are recorded. Do not claim field-pilot readiness.',[29],6,'Accepted handover manifest + maintenance backlog']
];
const cards=days.map(([day,title,goal,tasks,done,deps,hours,evidence])=>({id:`D${String(day).padStart(2,'0')}`,day,title,goal,tasks,done,deps:deps.map(d=>`D${String(d).padStart(2,'0')}`),hours,evidence,sprint:Math.min(4,Math.ceil(day/7)),owner:'Developer',status:day===1?'Ready':'Backlog'}));
const repositoryProgress=JSON.parse(fs.readFileSync(path.join(__dirname,'implementation-progress.json'),'utf8'));
for(const card of cards)if(repositoryProgress[card.id])Object.assign(card,repositoryProgress[card.id]);
const scope=`Planning assumption: one developer already comfortable with Kotlin, Compose and Room; 30 consecutive calendar days starting September 29, 2026 (editable in the board). Normal days allow 6 focused hours; Days 7, 14, 21 and 28 allow 2 hours for review, leaving time for rest. Total budget: 164 hours. A peer/adviser is needed for the Day 14 review, Day 23 UAT and final acceptance. Dates are a proposed schedule, not a delivery promise. This is an aggressive 164-hour native rewrite target, not a guaranteed estimate. If learning Kotlin/Compose, working part-time or missing either early gate, extend the schedule before promising the same scope.\n\nThe deliverable is a complete offline Tala capstone/demo app rewritten in Kotlin + Jetpack Compose in Android Studio, preserving the current prototype workflows. The user selected a native rewrite. Use a new android-native/ project; the existing browser app and mobile/ Expo starter are behavior references, not reusable native screens. Port the questionnaire definitions and rules into Kotlin; do not package the HTML/JavaScript in a WebView. Day 1 confirms scope and device constraints. The month covers implementation from scope through handover; this board itself does not mean implementation has started.\n\nIncluded: all 18 sections; household/member editing; photos and counts; branching/validation; save/resume; callbacks; signatures and review; native GPS/manual coordinates; online map with offline fallback; Room/SQLite storage; versioned JSON export/restore; reversible archiving; device testing; signed APK; documentation and defense materials.\n\nSeparate later phase: production login and roles, server-enforced access, encrypted storage/key management, cloud synchronization/conflict resolution, supervisor portal, authoritative locality boundaries, approved field questionnaire, offline basemap licensing and public Play Store launch. These are required planning items for a real field deployment where applicable; the 30-day demo does not establish production readiness.`;
const cadence=`Daily cadence (6 hours): 15 minutes review blockers and choose today's card; 15 minutes define examples/acceptance; 3 hours build the listed tasks; 1.5 hours test on the target phone and fix; 30 minutes save evidence/update documentation; 30 minutes commit/review/replan. On the four 2-hour review days: 15 minutes prepare, 45 minutes demonstrate/test, 30 minutes triage, 30 minutes update the next sprint. Daily cards are work packages inside four weekly sprints, not 30 separate Scrum sprints.\n\nWorkflow: Backlog → Ready → In progress → Verify → Done; move unresolved work to Blocked. Keep at most one card In progress and one in Verify for the assumed solo developer. Ready requires dependencies Done, available tools and clear acceptance. Done requires all checklist items checked, acceptance criteria met, and evidence recorded. A reviewer can be a peer; the developer remains responsible for each card. Keep blocked-card reason, owner and next action in notes. Escalate a blocker after 24 hours; do not silently shift every subsequent date.\n\nThe initial board has D01 Ready and D02–D30 Backlog. No app task is marked Done based on this planning exercise. Date filters are planning views, not evidence of actual completion.`;
const milestones=[['Sprint 1 · Days 1–7','Installable foundation','Android Studio builds an APK; offline launch, navigation and durable household/member saves pass.'],['Sprint 2 · Days 8–14','Complete interviews','All 18 sections, conditional fields, callbacks, review and signatures pass on-device.'],['Sprint 3 · Days 15–21','Complete phone workflows','GPS, maps, export/restore, migration, archive/restore and lifecycle checks pass.'],['Sprint 4 · Days 22–30','Validate and hand over','Regression, UAT, blocker fixes, signed release, capstone documentation and final acceptance are complete.']];
const sources=[['Android: Jetpack Compose setup','https://developer.android.com/develop/ui/compose/setup'],['Android: Room persistence','https://developer.android.com/training/data-storage/room'],['Android: architecture recommendations','https://developer.android.com/topic/architecture/recommendations'],['Android: prepare a signed release','https://developer.android.com/studio/publish/preparing']];
const md=[`# Tala Android · 30-day kanban plan\n\nPrepared September 29, 2026.\n\n## Release scope and capacity\n\n${scope}\n\n## How to run the board\n\n${cadence}\n\nOpen ANDROID-KANBAN.html in a browser. Expand a card for its checklist, acceptance criteria and evidence notes. Status changes and checklists save in that browser when storage is available; Export progress creates a portable JSON backup and Import progress restores it. This Markdown file is the static planning baseline.\n\n## Initial kanban\n\n| Backlog | Ready | In progress | Verify | Blocked | Done |\n|---|---|---|---|---|---|\n| D02–D30, in dependency order | D01 — Agree on the release | Empty | Empty | Empty | Empty |\n\n## Sprint goals\n\n| Sprint | Goal | Exit gate |\n|---|---|---|\n${milestones.map(x=>'| '+x.join(' | ')+' |').join('\n')}\n\n## Daily work packages\n`];
for(const c of cards){const date=new Date(Date.UTC(2026,8,29+c.day-1)).toISOString().slice(0,10);md.push(`### ${c.id} · ${date} · ${c.title}\n\n**Goal:** ${c.goal}\n\n**Sprint:** ${c.sprint} · **Budget:** ${c.hours} hours · **Owner:** ${c.owner} · **Initial status:** ${c.status}\n\n**Depends on:** ${c.deps.join(', ')||'None'}\n\n${c.tasks.map(t=>'- [ ] '+t).join('\n')}\n\n**Done when:** ${c.done}\n\n**Evidence/deliverable:** ${c.evidence}\n`);}
md.push(`## Release acceptance checklist\n\n- [ ] Final signed APK installs fresh and upgrades the previous compatible build.\n- [ ] No laptop/server/network is needed to launch, complete and save an interview.\n- [ ] All 18 sections and current revisions pass on the target phone and second configuration.\n- [ ] Saved records survive force-stop/reboot; failed writes report failure.\n- [ ] Location denial, map unavailability and cancelled exports have usable outcomes.\n- [ ] Backup/restore, migrations and archive/restore preserve records and attachments.\n- [ ] No crash, data-loss, or broken-required-workflow defects remain.\n- [ ] Exact APK version/hash, matching source, test evidence, UAT and known limits are recorded.\n- [ ] User/developer guides, capstone report and demo material are delivered.\n- [ ] Reviewer acceptance, maintenance owner and signing-key custody are documented.\n\n## Risk and scope rules\n\n| Trigger | Response |\n|---|---|\n| Native shell, form renderer or Room slice fails | Use the Day 7 gate to resolve/re-estimate; do not carry an unproven foundation into forms work. |\n| Backend, accounts or cloud sync become mandatory | Rebaseline capacity and dates; the offline native rewrite plan cannot absorb production backend work unchanged. |\n| Questionnaire wording or approved local data unavailable | Keep documented illustrative/demo labels; do not invent official definitions. |\n| Behind by more than one normal day | Use Day 26 contingency, then defer cosmetic polish; protect data integrity, required flows and release verification. |\n| No reviewer available | Record acceptance as pending; reschedule UAT/handover rather than claiming approval. |\n| Release blocker remains on Day 29 | Hold release and publish a revised completion date. |\n\n## Planning sources\n\nBaseline: README.md, ANDROID-PLAN.md, REVISION-NOTES.md, forms.js, package.json and mobile/package.json in this repository.\n\n${sources.map(([t,u])=>`- [${t}](${u})`).join('\n')}\n\nVerify toolchain compatibility and distribution requirements again on the scheduled implementation/release day.\n`);
// Preserve the dated planning baseline while showing measured implementation evidence.
for(const card of cards){
  if(!repositoryProgress[card.id])continue;
  const index=md.findIndex(block=>block.startsWith(`### ${card.id} `));
  let taskIndex=0;
  md[index]=md[index].replace('**Initial status:**','**Repository status:**')
    .replace(/- \[ \]/g,()=>card.checks[taskIndex++]?'- [x]':'- [ ]')
    +`\n**Implementation evidence:** ${card.notes}\n`;
}
md[0]=md[0].replace('## Initial kanban','## Original planning baseline')
  .replace('## Daily work packages','## Repository implementation update\n\nD03 is in Verify; see [Day 3 evidence](evidence/D03.md). Existing browser progress remains independent. D01/D02 acceptance is not inferred from a successful shell build.\n\n## Daily work packages');
fs.writeFileSync(path.join(__dirname,'ANDROID-30-DAY-PLAN.md'),md.join('\n'));
const template=fs.readFileSync(path.join(__dirname,'board-template.html'),'utf8');
fs.writeFileSync(path.join(__dirname,'ANDROID-KANBAN.html'),template.replace('__PLAN_DATA__',JSON.stringify({cards,scope,cadence,milestones,sources}).replace(/</g,'\\u003c')));
console.log(`Generated board and daily plan: ${cards.length} cards, ${cards.reduce((n,c)=>n+c.hours,0)} hours.`);
