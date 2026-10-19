# Native requirement-to-code/test acceptance matrix

19 October 2026. Maps every product and technical requirement to the current native implementation, its tests and the remaining acceptance work. D01–D14 have recorded acceptance. D14 includes owner-authorized Codex AI peer review; human usability/UAT remains separate. D15 location is implemented with device acceptance still in progress. Later workflows retain their own gates. See [section coverage](questionnaire-coverage.md), [product requirements](../specifications/01-product-requirements.md), [technical requirements](../specifications/02-technical-requirements.md), [D12 evidence](../../03-evidence/D12.md), [D13 lifecycle evidence](../../03-evidence/D13.md) and [D14 review evidence](../../03-evidence/D14.md).

Tests below are class names under `android-native/app/src/test` or `android-native/app/src/androidTest`. A test's existence is not a passing result: dated acceptance records contain execution outcomes and artifact identity.

| Requirement | Native implementation | Behavioral tests / evidence | Remaining acceptance |
|---|---|---|---|
| PR-01 Profile | DataStoreEnumeratorProfileStore, EnumeratorProfileViewModel; attribution captured by createHousehold | RegistrationRegressionTest; RegistrationDeviceTest; D08 | Release regression |
| PR-02 Counts | RegistryViewModel derives active household/member/sex/callback counts | RegistrationRegressionTest; RegistrationUiDeviceTest; D08 | Full release dashboard/UAT including reviewed count |
| PR-03 Search/filter | RegistryViewModel and HouseholdsScreen; Archived query shares active search rules | ShellViewModelTest; FoundationFixesTest; ShellDeviceTest | Release regression |
| PR-04 Registration | UUID repository, household fields, photo slot, saved-state editors | HouseholdRepositoryTest; RegistrationRegressionTest; RegistrationUiDeviceTest; D06/D08 | Release regression |
| PR-05 Roster | MemberEditViewModel, UUID members, per-member answers and confirmed removal | HouseholdRepositoryTest; EditingDeviceTest; IdentityDeviceTest; D06/D09 | Explicit roster ordering remains a release requirement |
| PR-06 Questionnaire | QuestionnaireSections, FormRules, shared renderer, scoped Room saves | QuestionnaireSectionsTest; section suites in coverage matrix; D09–D12; D14 scripted questionnaire interview with two members | Release regression |
| PR-07 Identity | RecordTextLimits, EditRules, derived valid display names; invalid drafts remain repairable | RegistrationRegressionTest; FoundationFixesTest; D08/D12 | Release regression |
| PR-08 Phone | FormRules strict syntax/formatPhone, SharedField focus handling | FormRulesTest; FormDeviceTest; D04 | Release regression |
| PR-09 Safety | Household definition, Answered condition and optional safetyReason | RegistrationRegressionTest; QuestionnaireSectionsTest; D08 | Approved wording/UAT |
| PR-10 Disaster | Disaster definition; fixed-point clearing and exclusive hazards | DisasterProtection suites; DisasterProtectionPersistenceTest; FoundationSectionsDeviceTest; D12 | Approved real-world site list remains outside demo acceptance |
| PR-11 Education | Member education definition, SNED/SPED retained as reference values | EducationEmployment suites; FoundationSectionsDeviceTest; D10/D12; D14 label correction and owner-authorized AI peer review | DEC-03 label decision and UAT |
| PR-12 Employment | Seven-day question; work and seeking branches; hours 0–168 | EmploymentValidationTest; EducationEmployment suites; FoundationSectionsDeviceTest; D14 full interview | Release regression |
| PR-13 Voter | Member demographics registeredVoter field | DemographicSections suites; IdentityDeviceTest; D09 | Release regression |
| PR-14 Disability | Other demographics with nested PWD branch | DemographicSectionsBranchingTest; IdentityDeviceTest; D09 | Release regression |
| PR-15 Other IDs | National/solo-parent/senior responses with scoped dependent clearing | DemographicSections suites; IdentityDeviceTest; D09 | Unresolved official wording stays explicit |
| PR-16 Protection | Household protection definition and contradiction check in InterviewRules | DisasterProtection suites; FoundationSectionsDeviceTest; D12 | Demo scope retained; authoritative program/scope approval before real use |
| PR-17 Visits | Explicit dated visit actions/history, callback list/date, no respondent/refusal and resume | VisitHistoryTest; InterviewLifecyclePersistenceTest; InterviewLifecycleDeviceTest; D13 | Release regression; future zone/attribution metadata remains target scope |
| PR-18 Draft saves | Debounced workspace; save-before-switch drains new edits; restored draft retains conflict base | FoundationFixesTest; InterviewSectionsViewModelTest; RegistrationRegressionTest | D20 broad lifecycle/low-storage gate |
| PR-19 Media | PhotoStore, Photo Picker, PhotoPreview, Room references/DataStore profile | RegistrationDeviceTest; RegistrationUiDeviceTest; D08 | Backup round trip D17/D18; camera capture is not claimed |
| PR-20 Consent/review | Drawn/typed signatures, undo/redo/clear, refusal cleanup and explicit local Reviewed guard | InterviewLifecycle suites; native completion/force-stop tests; D13; D14 two-member reviewed interview survives force-stop | Broad release accessibility/lifecycle checks |
| PR-21 Location/map | D15 foreground acquisition and manual correction implemented; Room v3 coordinate writes preserve the latest full graph | LocationRulesTest; LocationViewModelTest; CoordinatePersistenceDeviceTest; [D15 evidence](../../03-evidence/D15.md): 166 JVM and 50 device checks across 13 invocations pass, including the full permission/fix matrix with explicitly-mocked provider fixes | D16 map flow |
| PR-22 Backup | Documented schema, no accepted native export/restore | Schema artifact verifier | D17/D18 full implementation and rollback/attachment acceptance |
| PR-23 Archive | Persistent archivedAt, active-count exclusion, search/recovery filter | SprintAcceptanceRegressionTest; FoundationFixesTest; EditingPersistenceDeviceTest | D19 export-selection and release interaction checks |
| PR-24 Accessibility | Shared semantics, errors, focus, insets and minimum targets | FormDeviceTest; ShellDeviceTest; D03/D04 | D20 large text/TalkBack and broad device checks |
| PR-25 Cloud | Separate future scope; no connected Android backend | Manifest/dependency inspection | Separate approved project scope |

| Technical requirement | Native implementation | Behavioral tests / evidence | Remaining acceptance |
|---|---|---|---|
| TR-01 Toolchain/offline | Wrapper/catalog, minSdk 26, target 37, JDK 17, no Internet permission | Build/lint; ShellDeviceTest; D02/D12 | Exact signed-release checks |
| TR-02 IDs | Typed UUIDs and unique displayCode index | HouseholdRepositoryTest; EditingPersistenceDeviceTest | Release regression |
| TR-03 Scope integrity | Composite Room foreign keys and repository scope checks | RecordStoreDeviceTest; member isolation suites | Release regression |
| TR-04 Validation | FormRules plus shared record text limits | FormRulesTest; section validation suites; FoundationFixesTest | Approved label decisions |
| TR-05 Branch cleanup | Fixed-point cleanup and hidden-field callback refusal | FormRulesTest; section branching suites; FoundationSectionsDeviceTest | Location/picker stale-result gates in later features |
| TR-06 Write ordering/conflicts | Mutex and compare-with-base section saves | InterviewSectionsViewModelTest; FoundationFixesTest | Wider non-section stale-editor handling remains a release review item |
| TR-07 Explicit outcomes | Saved/Failed results; conflicts surfaced as Failed with explanatory text | HouseholdRepositoryTest; FoundationFixesTest; DurableSaveTest | Separate typed Conflict result remains target design; UI already refuses overwrite |
| TR-08 Atomic IO | RoomRecordStore full-snapshot transaction and reactive reads | RecordStoreDeviceTest rollback/reopen | Performance gate D20 |
| TR-09 Debounce/flush | 500 ms debounce; Save/Back/switch await latest accepted write | RegistrationRegressionTest; FoundationFixesTest; device restart tests | D20 low-storage/lifecycle scenarios |
| TR-10 Restored state | SavedStateHandle editors, list inputs and workspace route/member/draft/base; Room reload | EditScreensViewModelTest; FoundationFixesTest; WorkspaceRestorationDeviceTest | D20 broad process/focus lifecycle gate |
| TR-11 Migrations | Exported v1/v2/v3, migrations 1→2→3, no destructive fallback | SchemaMigrationTest preserves legacy visit/attachment rows; D13 | Any future schema increment requires new upgrade tests |
| TR-12 Media lifecycle | Bounded normalization, reference-first replacement, JPEG/PNG orphan cleanup | RegistrationDeviceTest; real signature PNG decode/reopen and native undo/clear/redo tests; D08/D13 | Attachment backup D17/D18 |
| TR-13 Backup validation | Planned versioned backup contract | Artifact verifier checks schema examples only | D17/D18 native import/export |
| TR-14 Read/stale browser | Native read flows are separate from save; browser stale-tab issue remains reference backlog | RegistrationRegressionTest; InterviewSectionsViewModelTest | Browser-specific repair outside native D01–D12 |
| TR-15 Recovery | Room enforces shape; unsupported migrations fail without deleting records | SchemaMigrationTest; RecordStoreDeviceTest | User-facing corruption recovery/import and browser issue remain later work |
| TR-16 Archive persistence | archivedAt migration and graph-preserving archive/restore | EditingPersistenceDeviceTest; SprintAcceptanceRegressionTest | D19 interaction gate |
| TR-17 Privacy | No networking; app-private storage; explicit OS backup exclusions; safe write error wording | ShellDeviceTest; manifest/data-extraction inspection | D24 release inspection; real data requires separate security scope |
| TR-18 Permission fallbacks | D15 explicit denial/permanent denial, location-off, unavailable and timeout states; manual entry and interview saving remain usable | LocationViewModelTest; full D15 phone permission matrix in [D15 evidence](../../03-evidence/D15.md): real denial dialogs, mocked precise/approximate fixes, location-off and timeout phone checks pass | D16 map permissions if applicable |
| TR-19 Accessible controls | SharedField semantics and deliberate first-error focus | FormDeviceTest; FoundationSectionsDeviceTest | D20 TalkBack/scaling report |
| TR-20 Signed release | Build settings only; debug acceptance does not establish release signing | Debug build identity in evidence | D25/D29 exact signed APK, upgrade, custody and source tag |

Domain validation consumes the read-only `InterviewData` contract; Room row decoding stays in `data/local/StoredRecord`. No database migration is introduced by the D01–D12 repair work.
