# Requirements traceability

11 October 2026. This matrix connects the six specifications to existing code and required acceptance work. Existing tests are evidence of their implemented scope, not proof that all proposed behavior is complete.

| Requirements | Target screens | Current source / evidence | Remaining acceptance |
|---|---|---|---|
| PR-01–03 | S01, S02, S10 | browser/workflow.js; native RegistryViewModel; ShellDeviceTest | Native editable profile, sex counts, archived filter |
| PR-04–08 | S03–S06 | browser/forms.js; FormRulesTest; FormSessionViewModelTest | UUID adapter, canonical names, durable editing, no stale overwrites |
| PR-09–10 | S03, S06 | Browser household/disaster definitions; verify-revisions.cjs | Native port and approved locality/evacuation catalog |
| PR-11–13 | S06 | Browser education/employment/demographics; native PracticeSection | Full native per-member forms and classification decision |
| PR-14–16 | S06 | Browser conditional PWD and protection fields; revision checks | Native port, authoritative program coverage and scope decision |
| PR-17–18 / TR-06–10 | S04, S06 | RoomRecordStore; RecordStoreDeviceTest; DurableSaveTest | Editing adapter, revision conflict handling, dated visits, truthful save state |
| PR-19–20 | S03, S07, S10 | Browser photos/signature; Room attachment references | Native media lifecycle, consent/review and recovery |
| PR-21 / TR-18 | S08 | browser/geomapping.js; verify-map.cjs | Native adapter/provider, permission/lifecycle tests |
| PR-22 / TR-13 | S09, S11 | Browser legacy export; proposed backup schema | Native export/restore, conflicts, file validation, rollback |
| PR-23 / TR-16 | S12 | Browser temporary delete undo only | Persistent archive/restore with graph preservation |
| PR-24 / TR-19 | All | Shared Compose controls and UI evidence | Full app large-font, second-device and accessibility checks |
| PR-25 | Future | supabase/config.toml, placeholder SQL only | Scope approval, schema, roles, policies, sync contract and integration tests |
| TR-11–12 | Storage/media | Exported Room v1; SchemaMigrationTest | Real v1→v2 migration, attachment files and partial-failure handling |
| TR-14–15 | Browser reference | System analysis reproduced stale tabs/corrupt storage | Permanent regressions and fixes; documentation work does not implement them |
| TR-17, TR-20 | Release | Build config and backup flags | Explicit backup exclusions, threat model if real data, signed artifact verification |

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

The [system analysis](reviews/SYSTEM-ANALYSIS.md) is a dated snapshot. The table above records follow-up status without rewriting its historical findings. The [planning evidence](../planning/03-evidence/) describes completed work packages; release requirements must also pass their stated end-to-end acceptance.
