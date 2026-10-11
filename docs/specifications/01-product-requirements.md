# Product Requirements Document — Tala

Version 1.0 · 11 October 2026 · Implementation baseline for the offline capstone.

**Status:** Draft requirements prepared from the prototype, native implementation, revision notes, and delivery plan. A requirement describes intended behavior; it does not mean that behavior already exists. Unresolved product decisions are listed below.

## 1. Purpose and users

Tala helps a barangay enumerator register households, interview their members, document household conditions, tag locations, and review complete records on an Android phone. The primary outcome is a recoverable, internally consistent local household record that can be reopened and exported without a laptop or internet connection.

| User | Need | Release access |
|---|---|---|
| Enumerator | Register, interview, revisit, correct, locate, review, and back up records | One local workspace on the device; no login in the offline demo |
| Respondent | Understand the demo notice, answer questions, decline, and provide a signature when agreeing | Assisted by enumerator; no respondent account |
| Adviser/tester | Reproduce scenarios and assess completeness against evidence | Fictional fixtures, exported examples, and test reports |
| Supervisor/administrator | Assign work, authorize access, and review synchronized records | Future cloud phase; not a current role implementation |

## 2. Release scope

The release target is the native Kotlin/Compose application. The browser app is the working behavior reference. The Expo project is retained as an earlier starter, not a second implementation target. Current native screens use demo records and session-only practice forms; Room exists independently and must be connected.

Included: all 18 sections, household/member editing, local save/resume, branching, callbacks, profile/household images, signatures, review, GPS/manual location, online map with coordinate fallback, versioned backup/restore, reversible archiving, tests, signed APK, and user/developer documentation.

Excluded from this release: payments, messaging, production identity/roles, cloud synchronization, supervisor portal, official questionnaire certification, public app-store launch, and offline basemap distribution. A real resident-data deployment is a separate scope and review.

## 3. Features and measurable acceptance

Priority: **Must** = offline release gate; **Later** = future phase. Current status: **B** = browser implemented, **N** = native implemented, **P** = partial foundation, **T** = target only. B does not imply the audit defects are fixed.

| ID | Feature / priority | Exact behavior and user expectation | Acceptance example | Current |
|---|---|---|---|---|
| PR-01 | Workspace / Must | Enumerator name, barangay, and optional picture; new records capture the current enumerator attribution | Changing the profile affects new-record attribution without rewriting prior attribution | B; native fixed demo profile |
| PR-02 | Dashboard / Must | Show active registered households, total active members, Male, Female, unrecorded sex, callbacks, reviewed households, and enumerator data. Counts derive from records, never the handwritten 15/30 | Register/archive/restore a household; each count updates without duplicates | B; N basic counts |
| PR-03 | Household register / Must | Search name, readable code, or address; filter All, In Progress, Callback, Reviewed, Archived; empty result offers reset filters | Search does not edit or save a household | B; N read-only list/search |
| PR-04 | Register household / Must | Generate immutable UUID and unique readable code; capture address, structured head name, respondent, optional phone, previous city, alias, and picture | Reopening after process death shows the same identity and saved answers | B; native storage P |
| PR-05 | Member roster / Must | Add/edit members with stable IDs, structured names and relationship; select by ID, preserve explicit roster order; warn before removal of answers | Edit member A, select B, return to A; answers never cross members | B; N read-only roster/practice scope |
| PR-06 | Questionnaire / Must | All sections in §4; save incomplete drafts; completion counts only currently visible required answers | Changing a parent answer hides, clears, and stops requiring its dependents | B; N representative controls/rules |
| PR-07 | Structured identity / Must | First and Last Name required; Middle Name, Extension and Alias optional. Derive display name from components; preserve legacy unsplit name until migrated | Display name and name components cannot disagree after a successful edit | B has duplicate-name defect |
| PR-08 | Contact number / Must | Optional 11-digit local-format number; accept 11 digits or exact 4-3-4 formatting, display `0917-123-4567`; do not strip arbitrary letters into valid input | Blank passes; malformed/10/12-digit input fails without destroying the typed value | B; N stronger practice rule |
| PR-09 | Household safety / Must | Ask “Is Your House Safe?” Yes/No; when answered offer optional reason; CCTV is an example, not an automatic safety determination | Either answer allows an explanation and saves independently of neighborhood safety | B |
| PR-10 | Disaster/evacuation / Must | Disaster Type: Man-made, Natural, Biological Disaster; evacuation area from a maintained list with Other and text; only require area if known | Choosing Other requires its detail; switching Known to No clears both | B with sample areas |
| PR-11 | Education / Must | School attendance, school classification, grade, optional track, and educational assistance Yes/No; preserve the current SNED/SPED values pending DEC-03 | Non-attending member has no stale school/track answer; assistance is asked independently | B |
| PR-12 | Employment / Must | Earned for pay/profit in previous 7 days; if Yes require occupation, industry, employment type, integer hours 0–168; if No require looking-for-work and reason; availability only when looking | Yes→No clears work details; 169 hours fails | B; N representative work fields |
| PR-13 | Voter information / Must | Per-member Registered Voter? Yes/No; record respondent answer without inferring from age | Switching members shows each member's answer | B |
| PR-14 | Disability / Must | Has Disability?→Yes→Registered PWD?; Registered Yes requires PWD ID; disability description appears for Disability Yes | Disability No removes registration, PWD ID and description | B |
| PR-15 | Other identification / Must | National-ID possession, solo-parent status and optional Solo Parent ID, senior citizen-ID response | Solo Parent No clears its dependent ID; no invented field for unclear abbreviation | B |
| PR-16 | Social protection / Must | PhilHealth and Member/Dependent, DSWD assistance, 4Ps and other programs; one authoritative answer for each program. Scope decision DEC-02 must be settled before native port | PhilHealth No plus PhilHealth listed as covered cannot pass review | B has contradictions/scope gap |
| PR-17 | Visits and callbacks / Must | Append dated visits; record started/completed/callback/no respondent/refused; callback requires local date/time; prior visits retained | New callback does not overwrite the history of the first visit | B latest visit only; Room visits P |
| PR-18 | Draft saving / Must | Autosave drafts after settled edits; explicit Save flushes outstanding edits. Show Saving/Saved/Unsaved. Saving may contain incomplete answers, but not cross-household references | Storage failure keeps prior durable record and offers retry; no false success | B has save failures; Room transactional P |
| PR-19 | Pictures / Must | Optional enumerator and household pictures; choose/capture, preview, replace/remove; cancel leaves existing image; reference media outside answer JSON | Denial/cancel causes no record loss; saved picture survives restart | B; Room attachment references P |
| PR-20 | Consent/signature/review / Must | Present demo notice; Yes requires drawn signature or typed fictional name. No clears signature and blocks Reviewed. Review shows missing answers with jump links | Complete all sections and signature, mark Reviewed; editing any answer invalidates review | B |
| PR-21 | Location/map / Must | Foreground capture with permission, manual coordinates, accuracy/time/source, explicit pin confirmation; map loss never blocks coordinate entry | Deny permission, enter valid coordinates, confirm and save; stale result cannot update another household | B; Room coordinate P |
| PR-22 | Export and restore / Must | Export selected/all records with version, identifiers and attachments; validate and preview restore; atomic import; conflicts are surfaced | Export→restore into empty test store preserves IDs, answers, images and review state | B export only; T native |
| PR-23 | Archive/restore / Must | Archive hides record from active counts without deleting its graph; Archived filter restores it; retention survives restart | Archive, restart, restore; same UUID and answers return | B latest-delete undo only; T native |
| PR-24 | Accessibility/responsiveness / Must | Descriptive labels, Yes/No groups, errors next to fields, keyboard-safe layout, 48dp native targets, readable scaling and consistent sentence-case labels | Complete a form with large text and keyboard open on target phone | N partial tested controls; B responsive |
| PR-25 | Cloud collaboration / Later | Authenticated enumerators and supervisors, assignment, authorized synchronization, conflict resolution and audit trail | Separate cloud requirements, security tests and rollout approval required | T only |

## 4. Questionnaire inventory and scope

The exact current browser keys, options, optional flags and conditional expressions are in the generated [field dictionary](../schemas/questionnaire-field-dictionary.md) and [JSON inventory](../schemas/questionnaire-current.json). These document the existing code; the corrective requirements above take precedence for the native target.

| # | Section | Scope | Expected content |
|---|---|---|---|
| 1 | Survey Setup | Household | Enumerator, barangay, purok, interview date |
| 2 | Visit Information | Interview visit | Visit date/result, conditional callback, notes |
| 3 | Household Profile | Household | Head/structured names, address, respondent, phone, previous city, photo, safety/reason |
| 4 | Household Members | Household roster | Member identities and count; at least one before review |
| 5 | Demographics | Each member | Name, relationship, sex, birth date/registration, civil status, voter |
| 6 | Other Demographics | Each member | National ID, solo parent/ID, disability/PWD, senior ID |
| 7 | Migration | Each member | Citizenship, moved, previous city/municipality, move year, overseas |
| 8 | Education | Each member | Attendance, school, nonattendance reason, grade, track, assistance |
| 9 | Employment | Each member | Pay/profit, occupation, industry, type, hours or job-seeking branch |
| 10 | Health | Household | Care need, provider, access |
| 11 | Food Security | Household | Food worry, skipped meals, assistance |
| 12 | Transport & Finance | Household | Usual transport, financial accounts |
| 13 | Disaster Preparedness | Household | Disaster type, hazards, evacuation, emergency kit |
| 14 | Internet & Public Safety | Household | Internet/connection, neighborhood safety |
| 15 | Social Protection | Current household; DEC-02 pending | PhilHealth, dependency, DSWD, 4Ps, assistance |
| 16 | Water & Sanitation | Household | Water, toilet, waste, handwashing |
| 17 | Housing Characteristics | Household | Roof, walls, tenure, electricity, rooms |
| 18 | Consent & Review | Interview | Consent, signature/typed name, completeness and review |

## 5. Rules shared by every form

- An unanswered value is distinct from No. Do not default binary answers to Yes or No.
- Hidden dependent answers are removed, including all downstream fields, and excluded from validation/export. Changes invalidate a previous review.
- Incomplete drafts are allowed. A Reviewed record requires all 18 sections, every member's applicable forms, consent Yes, and signature evidence.
- Dates are strict calendar dates; DOB cannot be in the future. A future callback is allowed; no automatic retrospective cutoff is assumed.
- None and Prefer Not to Answer are exclusive in multi-selects; options must belong to the field definition.
- Locality choices and official questionnaire text require a versioned reference source. Current Cogon bounds and evacuation labels remain visibly illustrative.
- Any external map action identifies its destination. Viewing or searching records must not mutate them.

## 6. Open decisions and exclusions from inference

| Decision | Current safe baseline | Owner / needed before |
|---|---|---|
| DEC-01 Cash / “6 – Cash”, 15/30 counts | No Cash feature; derive counts from records | Product owner; before adding a metric |
| DEC-02 PhilHealth/dependent and DSWD scope | Preserve existing household answers; proposed native design records personal coverage by member and derives household summary | Adviser/product owner; before Social Protection port |
| DEC-03 Public/Private versus SNED/SPED | Preserve existing values; proposed split is school ownership + education program | Adviser/product owner; before Education port |
| DEC-04 Evacuation list and service boundary | Sample areas and approximate demo rectangle | Barangay representative; before claiming local accuracy |
| DEC-05 Covered assistance field / unclear ID/program | No invented question or inferred program | Product owner; before adding fields |
| DEC-06 Official notice, questionnaire and retention | Fictional-data demo wording; no official approval claimed | Project sponsor; before resident-data deployment |
| DEC-07 Native embedded map SDK/provider | No SDK selected/pinned yet; coordinates and external map URL contract fixed | Developer; before map implementation |

## 7. Definition of release completion

All Must requirements pass on the target OPPO CPH2529/Android 15 and a second supported configuration. The exact signed APK passes fresh install, upgrade, offline interview, force-stop/reboot, failed-write recovery, archive/restore and backup/restore. No known data-loss or required-workflow blocker remains. Source revision, artifact hash, UAT findings and installation/backup instructions accompany handover. Five completed planning cards do not equal completion of this product.

Related: [technical requirements](02-technical-requirements.md), [flows/wireframes](04-user-flows-and-wireframes.md), [traceability](../traceability.md).
