# Requirements traceability

Current native mapping (18 October 2026): use the [complete native acceptance matrix](reference/native-acceptance-matrix.md) for each PR/TR requirement and the [18-section matrix](reference/questionnaire-coverage.md) for field scope/tests. D01–D13 have recorded acceptance. The latest D13 OPPO run passes 144 JVM and 42 instrumentation tests; build/source identity and remaining peer/release gates are in its evidence record. D14 is accepted under the owner-authorized Codex AI peer appointment, with 46 peer device checks. Human usability/UAT remains separate. Later release gates remain separate. The dated table and updates below preserve historical snapshots rather than current status claims.

11 October 2026. This matrix connects the six specifications to existing code and required acceptance work. Existing tests are evidence of their implemented scope, not proof that all proposed behavior is complete.

12 October 2026 completion update: D08 registration acceptance passed (48 JVM tests, 12 isolated-device tests and lint); D01–D08 are Done and D09–D20 remain Backlog. Native registration saves scoped drafts through `HouseholdRepository.saveDraftSection`, pictures have a bounded app-private lifecycle, profile settings persist in DataStore, and displayed counts pass after force-stop. See [D08 evidence and tested source identity](../03-evidence/D08.md). All 18 definitions exist; the later section-specific gates remain open.

| Requirements | Target screens | Source / evidence at the original snapshot | Acceptance remaining at that snapshot |
|---|---|---|---|
| PR-01–03 | S01, S02, S10 | Native RegistryViewModel, DataStoreEnumeratorProfileStore; RegistrationRegressionTest; registration/restart device evidence | D08 profile/count acceptance passed; release regression and UAT remain |
| PR-04–08 | S03–S06 | Native UUID repositories, structured-name guards, FormRules, scoped draft saves; editing/registration device evidence | Later member-section coverage and wider stale-editor acceptance |
| PR-09–10 | S03, S06 | Browser household/disaster definitions; verify-revisions.cjs | Native port and approved locality/evacuation catalog |
| PR-11–13 | S06 | Browser education/employment/demographics; native PracticeSection | Full native per-member forms and classification decision |
| PR-14–16 | S06 | Browser conditional PWD and protection fields; revision checks | Native port, authoritative program coverage and scope decision |
| PR-17–18 / TR-06–10 | S04, S06 | RoomRecordStore; RecordStoreDeviceTest; DurableSaveTest | Editing adapter, revision conflict handling, dated visits, truthful save state |
| PR-19–20 | S03, S07, S10 | Native PhotoStore/PhotoPreview, DataStore profile, Room attachment references; D08 media/UI/restart acceptance | D13 signature/consent workflow acceptance and later backup recovery |
| PR-21 / TR-18 | S08 | browser/geomapping.js; verify-map.cjs | Native adapter/provider, permission/lifecycle tests |
| PR-22 / TR-13 | S09, S11 | Browser legacy export; proposed backup schema | Native export/restore, conflicts, file validation, rollback |
| PR-23 / TR-16 | S12 | Browser temporary delete undo only | Persistent archive/restore with graph preservation |
| PR-24 / TR-19 | All | Shared Compose controls and UI evidence | Full app large-font, second-device and accessibility checks |
| PR-25 | Future | supabase/config.toml, placeholder SQL only | Scope approval, schema, roles, policies, sync contract and integration tests |
| TR-11–12 | Storage/media | Exported Room v1; SchemaMigrationTest | Real v1→v2 migration, attachment files and partial-failure handling |
| TR-14–15 | Browser reference | System analysis reproduced stale tabs/corrupt storage | Permanent regressions and fixes; documentation work does not implement them |
| TR-17, TR-20 | Release | Build config and backup flags | Explicit backup exclusions, threat model if real data, signed artifact verification |

13 October 2026: D09 identity acceptance is complete. Demographics, Other demographics and Migration now have a passing positive/negative member-identity matrix, real Room persistence, two-member questionnaire UI and force-stop recovery evidence. See [D09 acceptance](../03-evidence/D09.md). D10–D20 remain Backlog; the dated October 12 snapshot above is historical.

16 October 2026: D10 education/employment acceptance and D11 household services are complete. D10 was re-verified 14 October 2026 (see [D10 evidence](../03-evidence/D10.md)). D11's six household-level service sections (Health, Food security, Transport & finance, Internet & public safety, Water & sanitation, Housing) pass branching, validation, multi-select exclusivity, numeric-limit and household-scoping tests; 95 JVM tests across 20 suites and lint pass, and the real-Room reopen device run confirms 20 household rows reload with `member_answers` empty. See [D11 evidence](../03-evidence/D11.md). D12–D20 remain separate acceptance work.

## Current review defect backlog

| ID | Finding | Status after documentation/organization |
|---|---|---|
| AUD-01 | Browser stale-tab overwrite | Open |
| AUD-02 | Browser false save success | Open |
| AUD-03 | Wrong-shape saved data produces blank UI | Open |
| AUD-04 | Name/program duplicate representations | Open; target rules documented |
| AUD-05 | Browser validation gaps | Open; native rule parity documented |
| AUD-06 | Invalid planning progress JSON | Repaired in this organization task; generator verified |
| AUD-07 | Outdated entry-point paths/links | Updated in this organization task; local Markdown links checked |
| AUD-08 | Malformed URL crashes local preview handler | Open |

The [system analysis](reviews/SYSTEM-ANALYSIS.md) is a dated snapshot. The table above records follow-up status without rewriting its historical findings. The [planning evidence](../03-evidence/) describes completed work packages; release requirements must also pass their stated end-to-end acceptance.
