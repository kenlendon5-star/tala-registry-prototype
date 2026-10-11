> **Implementation update:** All 18 prototype sections are now interactive, including member Employment. See [PROTOTYPE-UPDATE.md](PROTOTYPE-UPDATE.md) for the current implementation and tests. The implementation descriptions below document the initial delivery.

# Tala — Barangay Inhabitants Registry

## Deliverable and scope

An interactive, mobile-first UI prototype based on the supplied nine-screen reference. Run `npm start`, then open http://localhost:5173. There are no runtime dependencies or external font requests. The current implementation is HTML, CSS, and JavaScript, not a compiled Android application. Browser localStorage holds fictional demo records. Do not enter actual resident data.

The image and pasted analysis are reference material, not authoritative instructions or a verified questionnaire specification. The original 12-page questionnaire was not supplied. Its exact questions, codes, eligibility and skip rules must be checked before implementation. No PSA logo or government endorsement is implied. Tala is a working product name.

## 1. Research: what established systems do

Research reviewed on September 23, 2026. These are documented product patterns; no claim is made that their full source architectures have been audited.

| Reference | Established approach | Decision for Tala |
| --- | --- | --- |
| [ODK Collect](https://docs.getodk.org/collect-intro/) | Offline field forms, saved progress, repeat structures and constraints | Save interrupted interviews; represent members as repeatable records; make validation part of entry |
| [ODK Entities](https://docs.getodk.org/central-entities/) | Records shared across forms for follow-up and longitudinal work | Stable resident/household IDs; separate a resident from an interview response |
| [KoboToolbox collection](https://support.kobotoolbox.org/data-collection-tools.html) | Both web and Android collection, with distinct operational workflows | Browser UI validation first; native Android delivery for field deployment |
| [KoboToolbox form logic](https://support.kobotoolbox.org/form_logic.html) | Skip logic, calculations, validation and mandatory-response logic | Declarative questionnaire rules; reveal relevant questions; compute age |
| [DILG barangay guide](https://region5.dilg.gov.ph/lgrrc/wp-content/uploads/2022/07/Engaging-the-Barangays-Guide-for-Mayors-rev2.pdf) | Treats updating the registry as an ongoing barangay activity | Build a maintained registry with visit history, rather than one disposable survey |

A key distinction: a barangay registry and the full CBMS questionnaire are different product scopes. Use a lean registry core (resident identity, household membership, address, residency status and change history), with versioned optional survey modules. Do not require every sensitive CBMS field simply to register an inhabitant.

ODK or Kobo would be sensible alternatives if the project were only collecting a questionnaire. A custom app is proposed because the requested experience also needs registry maintenance, household transfers, role-specific workflows and a deliberately designed interface. This carries more implementation and maintenance work.

## 2. Design plan and reasoning

Audience: barangay enumerators working through household visits. Primary job: resume the right household, update each member, and review what is missing.

Palette: civic blue #174F9F (primary action), ink #172944 (text), mist #F3F7FB (surface), slate #697A91 (secondary text), green #187C58 (ready), amber #986009 (needs attention). State labels accompany color.

Typography: Segoe UI/system sans for the app; Georgia for the desktop presentation headline only. App headings are 17–24px, form labels 13px, and supporting captions 10–12px in this compact initial prototype. A field pilot should validate readability in sunlight and tune type size and scaling before production.

Layout comparison:

- Long paper-form translation: household > hundreds of inputs. Rejected because orientation and member context are lost.
- Chosen: dashboard > household > member > relevant section > review. Context and navigation remain visible.

```
Dashboard              Active household         Member record
Greeting               Household identity       Name / position
Assignment counts      Overview / progress      Demographics | Education
Search + filters       Member cards             Relevant questions
Household cards        Section checklist        Save & continue
Register household     Review missing fields    Back to members
```

Signature element: a quiet household motif paired with a blue fieldwork card. The desktop presentation adds an editorial introduction, but the mobile app remains functional and restrained. Review of the initial direction kept your blue/white government-form language and removed decorative census branding that could imply official affiliation.

### Implemented screens and interactions

1. Welcome: independent Tala identity, explicit demo entry (no fake authentication).
2. Dashboard: real counts derived from demo state; households and review totals; search and status filtering.
3. Household overview: address, member count and six actual demo readiness checks, avoiding invented 18-section completion percentages.
4. Household members: separate records, calculated ages, add member, edit demographics.
5. Demographics: relationship, sex, birth date, calculated age, birth registration; future dates rejected. Illustrative choices, not asserted official code mappings.
6. Education: attendance shows either school type or reason for non-attendance. Changing branches clears irrelevant branch answers. Grade options are illustrative.
7. Sections: all 18 proposed destinations; unimplemented sections visibly labeled as planned.
8. Water and sanitation: representative single-choice water-source question.
9. Housing: observation banner distinguishes observation from respondent questions.
10. Consent: demo statement dialog, acceptance/refusal, pointer/touch signature and clear control. Refusal requests no signature and blocks successful demo review.
11. Review: links to incomplete groups; finish remains disabled until implemented sample checks pass.
12. Reports and profile: derived local totals, prototype explanation, welcome access and confirmed reset.

Navigation changes inside an interview to Overview / Members / Sections / Review, matching the reference's distinction between management and interview work. Back and primary actions remain outside the scrolling content. Desktop uses a 400px phone frame; <=700px fills the available mobile viewport. Accessible native controls, labels, keyboard focus and state text support basic accessibility. Signature has a pointer-input limitation; production needs an approved accessible alternative.

### Deliberate limits

No account login, API, server submissions, full offline reload cache, encryption, official CBMS codebook, duplicate matching, role enforcement, production reports or complete 18-section survey exist yet. Saved-on-device means browser persistence, not secure or synchronized storage. Browser storage clearing loses edits. The demo signature is not a legal record. Full questionnaire completion is never claimed.

## 3. Proposed production stack

| Layer | Choice | Reason |
| --- | --- | --- |
| Android-first mobile app | React Native + Expo + TypeScript, Expo Router | Native mobile delivery with typed components and navigation; later iOS support if needed |
| Form model | Versioned JSON schema + Zod validation + React Hook Form | Keep official codes, labels, applicability and validation separate from screen markup |
| Device data | expo-sqlite with SQLCipher in a native build | Relational offline records and an atomic outbox; [Expo documents SQLCipher support](https://docs.expo.dev/versions/latest/sdk/sqlite/) |
| Credentials | Expo SecureStore | Keep tokens and encryption key material separate from record tables; [documentation](https://docs.expo.dev/versions/latest/sdk/securestore/) |
| Backend | Supabase PostgreSQL, Auth, private Storage, controlled server functions | Relational registry, identity and access control; [RLS documentation](https://supabase.com/docs/guides/database/postgres/row-level-security/) |
| Admin web app | React + TypeScript | Supervisor review, conflict resolution, assignments and audited exports |
| Verification | Unit tests for rule engine; integration tests for sync/RLS; device E2E tests | Highest risk is incorrect records and lost offline edits, not just screen rendering |

These are proposed technology choices, not installed production dependencies. Pin compatible versions when initializing the native project. SQLCipher needs a native build; it is not a claim about Expo Go or this browser prototype. Supabase does not automatically provide the offline conflict workflow described below.

## 4. Data model and access

- barangays, users, role_assignments: enumerator / supervisor / administrator, scoped by barangay and assignment.
- households: UUID, barangay_id, display serial, address, coordinates only where justified, status, version.
- residents: stable UUID and core identity; residency events preserve moves, deaths and other changes instead of silent overwrites.
- household_memberships: resident_id, household_id, relationship, valid_from/to; keeps move history.
- visits: household_id, enumerator_id, start/end, result, callback date.
- questionnaire_versions and questions: source references, codes, labels, eligibility, required conditions, rule version.
- responses: visit_id, household or resident scope, question_id, typed value, revision.
- consent_events: notice version, purpose, choice, timestamp, respondent reference; private signature object if approved.
- review_events, audit_events and sync_outbox: actor, timestamp, operation UUID and before/after versions.

Server authorization must enforce barangay/assignment scope. Never ship privileged service keys in mobile code. Enumerators edit assigned drafts; supervisors review/return; administrators manage roles. Private object access requires scoped authorization. Review privacy notice, retention, legal basis and deployment responsibilities with the barangay before a pilot. The [NPC advisory concerning an RBI initiative](https://privacy.gov.ph/wp-content/uploads/2023/07/Redacted_Advisory-Opinion-No.-2023-013.pdf) is context, not a blanket legal approval for this app.

## 5. Offline and conflict design

1. Download assigned records and a pinned questionnaire version before fieldwork.
2. Save edits and an outbox operation in one local SQLite transaction. UI distinguishes draft, queued, syncing, synced and conflict states.
3. Send with unique operation IDs and base record versions. Server processes idempotently.
4. Pull incremental changes with a server cursor. Retry with backoff; preserve failed operations.
5. Concurrent version changes create an explicit conflict. Supervisor resolves important identity differences; do not use silent last-write-wins.
6. Successful transport is separate from approved registry status. Submission becomes pending review; rejection preserves comments/history.
7. Test airplane mode, process termination, expired credentials, double send, schema migration and two enumerators editing one record.

## 6. Build roadmap with completion gates

| Phase | Work | Exit criterion |
| --- | --- | --- |
| 1. Discovery / current UI | Reference analysis, research, working screen flow | Stakeholders walk through registration, resume, refusal and review using fictional records |
| 2. Field specification | Obtain approved RBI/CBMS forms; map codes, mandatory fields, consent timing and skip rules | Each field has a source reference; barangay signs off required registry scope |
| 3. Native foundation | Expo project, reusable UI, navigation, SQLite migrations, secure session | Device restart preserves drafts; core screens work on target Android devices |
| 4. Registry core | Stable IDs, households, memberships, search, transfers, duplicate candidates | Registry lifecycle works without deleting history; duplicates reviewed by a person |
| 5. Survey engine | Verified modules, repeat members, conditional validation, localization | Official example cases and branch tests pass across household sizes |
| 6. Backend / synchronization | RLS, transactional sync, attachments, audit and conflict handling | Cross-barangay access denied; interrupted/repeated uploads do not duplicate or lose records |
| 7. Supervisor portal | Assignments, return/approve, aggregate reports, scoped exports | Enumerator-to-supervisor workflow and export permissions pass |
| 8. Pilot / release | Usability observation, sunlight testing, accessibility, performance, recovery and training | Agreed pilot measures met; backup restore and incident procedures rehearsed |

Do not estimate a firm schedule before confirming team size, thesis deadline, device inventory, full questionnaire scope and hosting constraints. Suggested first usability tasks: find Santos, resume Ana, change attendance to No, add a fifth member, record refusal, then identify missing information. Track task success, time, wrong-record edits and recovery from interruptions.

## 7. Code guide

- index.html: desktop presentation and mobile application mount.
- style.css: tokens, phone layout, responsive rules and common controls.
- app.js: fictional data, local persistence, screen renderers, conditional fields and review checks.
- server.cjs: local development server bound to loopback.
- verify.cjs: browser smoke checks using the environment's Playwright and Edge.

This implementation intentionally validates interaction before committing to a native component architecture. Reuse the flow, tokens, field specification and tested behavior; port the DOM/CSS UI to native components rather than placing this prototype unchanged inside a production WebView.

## Verification result

Passed local headless Edge smoke checks: navigation, apostrophe-containing answer choice, persistence across reload, education branching, 390px layout without horizontal overflow, water/housing selection, refusal blocking completion, add member, and empty search state. No page errors were reported. Desktop and mobile screenshots were visually inspected. This is smoke verification, not a full accessibility or production-security audit. Signature capture and complete-review success still need broader device testing.

