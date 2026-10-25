# Medium-priority repair report — 24 October 2026

This follow-up audits the account, backup-restore, and cloud-attachment code added while closing the high-priority gaps. Historical D01–D12 medium findings were already closed by `D01-D12-FIXES-2026-10-17.md`; the items below are current findings in the new implementation.

## Fixed findings

| Finding | Risk | Repair | Regression evidence |
| --- | --- | --- | --- |
| Restore decoded the complete 50 MiB JSON file through `readText`, temporarily retaining an additional UTF-16 copy | Avoidable memory pressure and possible restore failure on constrained phones | Decode directly from a buffered file stream while retaining the pre-parse 50 MiB limit | `BackupImporterTest`; Android test compilation |
| Backup validation checked identities only within each household | Duplicate member, interview, visit, or attachment primary keys across records reached the staging/transaction phase | Reject cross-household duplicate IDs before any attachment staging; require the fictional-data marker and matching consent/signature summaries | New duplicate-attachment and consent-summary `BackupImporterTest` cases |
| Account creation constraints existed only in Compose button state | Programmatic ViewModel calls could submit malformed email, short/mismatched passwords, or oversized profile fields | Centralized pure `AccountInputRules`; both Compose and `AccountViewModel` enforce them, while sign-in no longer incorrectly assumes an eight-character legacy password | New `AccountViewModelTest`; `AccountDeviceTest` |
| Android and Supabase configuration did not explicitly forbid cleartext transport | A mistaken HTTP endpoint or cleartext request could expose credentials/records | Set `usesCleartextTraffic=false` and instantiate Supabase only for a valid HTTPS host without user-info | `ShellDeviceTest#launchRequestsOnlyDeclaredPermissionsAndContentRespectsSystemBars` |
| Stale attachment metadata was deleted before its Storage object | A Storage deletion failure lost the only durable object path, preventing later retries from cleaning the orphan | Delete the private Storage object first, then remove metadata; a retry retains the stale path until object deletion succeeds | Source contract plus full compile/lint gate |

## Verification

- Focused backup/account JVM tests and Android-test compilation passed.
- `AccountDeviceTest` passed on the Android 17/API 37 isolated emulator application.
- The focused Shell device check passed and confirmed cleartext traffic is disabled with only the approved permissions.
- Full `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed: 231 JVM tests, zero failures/errors/skips, 23 lint warnings and no lint errors, debug APK assembled.
- Documentation passed for 121 Markdown files and 713 local links; generated-board verification passed for all 30 cards.

## Remaining boundaries

- Custom-scheme email confirmation cannot provide Android App Links domain verification without an owned HTTPS callback domain. Supabase PKCE/session handling and strict scheme/host checks remain in place; production deployment should move the callback to a verified App Link.
- Cloud download/merge, retention policy, remote deletion, and a live authenticated end-to-end sync run remain later-scope work rather than regressions in the explicit upload-only contract.
