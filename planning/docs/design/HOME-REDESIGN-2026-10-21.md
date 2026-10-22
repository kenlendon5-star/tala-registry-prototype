# Tala Registry Home implementation and agent handoff

Date: 21 October 2026 (Asia/Manila)

Status: Implemented; verification results are recorded below. This guide is the current Home design contract. It supersedes older Home mockups and conflicting Home instructions in the broader [UI/UX plan](UI-UX-IMPLEMENTATION-PLAN-2026-10-21.md). Map-screen and questionnaire-layout redesigns remain separate work.

## Approved reference and decisions

Use [the approved full Home mockup](mockups/tala-registry-home-2026-10-21.png) for visual hierarchy and [the original supplied logo](mockups/tala-logo-original.png) for branding. Earlier images are historical drafts.

- Header: supplied logo, **Tala Registry**, configured barangay, and an initials button opening Enumerator Profile.
- Greeting and short fieldwork subtitle.
- Recent interview card: actual household identity, status, questionnaire section and optional resident, with **Continue Interview**.
- **Register Household** and an actionable callback reminder when callbacks exist.
- **Registry Overview**, **Browse All**, household count and callback count.
- **Recorded Residents**: active-household total, Male, Female, and Sex Not Recorded, with a proportionate segmented bar.
- **Households By Purok**: recorded purok categories, counts, and bars relative to the largest group.
- The existing five destinations remain in bottom navigation.
- Remove Home's large slogan/hero, Quick Actions, Local Demo badge, Saved On This Device row, and demo footer.
- Capitalize the first letter of every word in fixed Home labels, preserving spaces. Use **Tala Registry**, not the earlier lowercase wordmark. Preserve user-entered names/addresses. Other screens retain their existing save/error messaging.
- The mockup is a scrolling page. Never squeeze every card into one phone viewport or hardcode its fictional numbers, member names, section, or puroks.

## Implementation map

All paths in this table are relative to the repository root.

| File | Responsibility |
|---|---|
| `android-native/app/src/main/java/ph/tala/registry/ui/home/HomeScreen.kt` | Production Home composable and cards, responsive layout, chart drawing, labels and test tags |
| `android-native/app/src/main/java/ph/tala/registry/ui/home/HomeInsightsViewModel.kt` | Reactive purok aggregation, recent interview resolution, fallback rules and navigation-context writes |
| `android-native/app/src/main/java/ph/tala/registry/data/InterviewResumeStore.kt` | Durable DataStore reference: household ID, section route, optional member ID |
| `android-native/app/src/main/java/ph/tala/registry/ui/TalaApp.kt` | Screen wiring, navigation callbacks, questionnaire-scope tracking, global bar visibility |
| `android-native/app/src/main/java/ph/tala/registry/ui/RegistryViewModel.kt` | Existing active-household/resident/sex/callback counts |
| `android-native/app/src/main/java/ph/tala/registry/data/local/RegistryDao.kt` | Reactive query of the explicit setup.purok answer joined to its household ID |
| `android-native/app/src/main/java/ph/tala/registry/data/local/RecordStore.kt` | Decode purok answers into a household-ID map |
| `android-native/app/src/main/java/ph/tala/registry/data/HouseholdRepository.kt` | Expose that map to the ViewModel |
| `android-native/app/src/main/res/drawable-nodpi/tala_registry_logo.png` | Original supplied artwork, copied without regenerating it |
| `tools/verify-home-redesign.ps1` | Focused isolated-emulator persistence, Room and responsive render verification |

The existing theme and platform sans-serif typography are reused with a deliberate size/weight hierarchy. No font or dependency download is needed. Theme colors support light and dark mode.

## Data contracts

### Totals and resident categories

Use active households only. Residents are recorded members across those households, not a claim to represent the barangay's whole population. Existing stored demographics sex answers determine Male/Female; all remaining residents are Sex Not Recorded. Do not infer sex from names.

Segment widths derive directly from counts. Zero-count segments are omitted; zero total renders an empty track and an explicit empty message. Text labels and numeric values accompany chart colors.

### Purok groups

Read only the household questionnaire answer at section `setup`, field `purok`. Do not parse a free-form street address or infer a purok from the map.

Trim and collapse whitespace. Numeric answers such as `2`, `02`, and `purok 2` share **Purok 2**. Named answers are grouped by normalized case/spacing. Numeric puroks sort numerically, followed by named categories; missing/blank values appear last under **Not Recorded**. Group counts sum to active households. Archived households do not contribute.

No Room schema/version change is required: the new query reads existing tables. `FakeRecordStore` mirrors the production map for meaningful repository tests.

### Continue Interview

DataStore saves navigation context only. Questionnaire answers still use the existing repository, debounce, save-before-switch/exit, draft restoration, and conflict checks.

Opening a household records it as recent; reopening the same household retains its previous scope. A loaded questionnaire records its selected section and member. No first-record fallback is used.

- No recent interview: show a household-browsing invitation when records exist.
- Empty registry: show first-household registration and zero/empty summaries.
- Missing or archived recent household: omit Continue and offer browsing.
- Removed member or invalid section: return to household setup; never substitute another resident's answers.
- Resume-store read/write failure: show an explicit warning and retain browsing access.
- A navigation reference is not evidence that unsaved answers were committed.

**Browse All** clears old search/filters. Callback actions clear search and select Callback. Profile and registration open the existing flows. No new inert export or shortcut actions were introduced.

## Responsive and accessibility behavior

The header and cards scroll above the existing bottom navigation, which remains outside the Home list. Home owns its header; the generic app bar is hidden only for Home and the questionnaire.

- Content width is capped at 1100dp and centered.
- At 840dp or wider with ordinary text, resident and purok cards sit beside each other.
- Narrow windows and enlarged text stack the cards.
- Summary cards stack below 340dp or at font scale 1.5 and above.
- Resident categories stack below 330dp of card width or at font scale 1.5 and above.
- Text and buttons have flexible heights; no diagram uses mockup pixel coordinates.
- At font scale 1.5 and above, global bottom navigation uses icons with accessible destination names to avoid clipped five-column labels.
- The avatar, buttons and summary actions have sufficient target size. Chart data has visible labels and a resident-bar content description.

## Agent instructions for follow-through

1. Read applicable repository/RTK instructions and relevant Android skills. Use `rtk` for shell commands.
2. Inspect the current diff. This workspace includes other uncommitted map/data work; preserve it.
3. Keep Home UI in `ui/home`. Keep aggregation, resume validation and I/O outside composables.
4. Preserve stable tags: `home-screen`, `register-household`, `continue-interview`, `browse-all`, `enumerator-row`, `residents-dashboard`, `purok-dashboard`, and `metric-<label>`.
5. Household roster rows belong on Households, not Home. Older tests that waited for roster rows on Home must navigate to the list first.
6. Verify live data and fallbacks before polishing decoration. Use numeric chart proportions rather than copying the generated image's illustrative geometry.
7. Do not restore rejected Quick Actions or slogan copy. Do not remove saving/error feedback from editors because the static Home status row was removed.
8. Keep future changes scoped; questionnaire navigation, map layout and Reports functionality are separate unless the user requests them.
9. Update this record with checks actually run. Clearly distinguish a fixture render from the actual running app.

## Verification commands

From `android-native/`:

```powershell
rtk proxy ./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedTestApp=true --console=plain
```

From the repository root, with the emulator booted and authorized:

```powershell
rtk proxy powershell -NoProfile -File tools/verify-home-redesign.ps1 -DeviceSerial emulator-5554
rtk proxy node tools/check-docs.cjs
```

The device script validates the isolated application ID, installs only the isolated APKs, runs real Room and DataStore checks, then renders the production Home composable with fictional fixtures in portrait/landscape at 100%, 150%, and 200% text. It retains screenshots/logs and restores font/rotation settings. It leaves the isolated package installed for further review.

Existing relevant checks include `HomeDashboardRulesTest`, `HomePurokRepositoryTest`, `ShellViewModelTest`, `HomeDashboardDeviceTest`, and the updated persisted-count assertion in `RegistrationUiDeviceTest`.

## Verification record

JVM verification: **202 tests across 36 suites, 0 failures, 0 errors**, including 7 new Home tests covering purok normalization/missing values, active-only totals, repository save/clear updates, recent scope retention, stale-reference fallbacks, and resume-write failure handling.

Build verification: **assembleDebug, assembleDebugAndroidTest, and lintDebug passed** for the isolated application. Lint reports **0 errors and 16 warnings outside the new ui/home files**. The final production-source build is recorded in `logs/home-redesign-final-build.log`; later test-only builds updated capture/orientation verification without changing production code.

Device verification: **9 focused tests passed** on API 35:

- Real in-memory Room query after a questionnaire purok write.
- DataStore navigation reference after closing and reopening its store.
- Production Home composable actions and categories at portrait/landscape, each at font scales 1.0, 1.5, and 2.0 (6 runs).
- Existing `RegistrationUiDeviceTest.dashboardAfterRestartDisplaysPersistedCounts` against the running app's actual Room-backed totals.

The six responsive runs explicitly set the test activity orientation and assert actual window aspect ratio. Logged viewports are **720×1600 portrait** and **1600×720 landscape**, at density 280 (the same logical window size as 1080×2400 at density 420). Do not accept a landscape filename alone as proof of orientation.

Authoritative device logs, APK hashes, restored font/rotation settings, actual viewport log, and fixture screenshots are under `planning/03-evidence/home-redesign-2026-10-21/orientation-verified/`. Earlier `verified/` and `accepted/` folders are diagnostic attempts, not the final acceptance record. The live-count regression is `orientation-verified/live-counts.log`.

Visual inspection included the actual app's [portrait Home](../../03-evidence/home-redesign-2026-10-21/runtime/portrait-top.png), [enlarged text](../../03-evidence/home-redesign-2026-10-21/runtime/portrait-large-dashboard.png), and [landscape app frame](../../03-evidence/home-redesign-2026-10-21/runtime/landscape-puroks.png), plus the [wide dashboard fixture](../../03-evidence/home-redesign-2026-10-21/orientation-verified/home-redesign/landscape-1.0/home-puroks.png). Runtime captures are viewport snapshots, not full-page scroll exports. Standalone fixture captures exercise card layout without the app Scaffold; use runtime captures when assessing system-bar insets, which the app Scaffold owns.

Verification repairs: an initial Compose screenshot timeout/system-UI stall led to device screenshots; the 200% action test now scrolls each stacked action into view before tapping; and actual orientation assertions replaced reliance on device setting labels. The PowerShell harness handles adb's successful stderr progress and uses .NET SHA-256 so it works without the Get-FileHash cmdlet. `-CollectOnly` can finish collection after all seven case logs show success.

Evidence directory: `planning/03-evidence/home-redesign-2026-10-21/`. The normal phone/app database was not used for these checks.

Remaining broader UI work: questionnaire compact navigation and map adaptation remain in the [broader plan](UI-UX-IMPLEMENTATION-PLAN-2026-10-21.md). TalkBack speech and outdoor readability require dedicated checks beyond screenshots.
