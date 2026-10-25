# Low-priority repair report — 24 October 2026

This follow-up closes the remaining explicitly recorded low-severity findings and reconciles current documentation affected by the D16–D18 implementation. Historical evidence remains historical; follow-up status is recorded without erasing original audit context.

## Fixed findings

| Finding | Repair | Evidence |
| --- | --- | --- |
| A malformed percent-encoded URL could throw `URIError` and terminate the loopback preview server | Refactored the handler to return HTTP 400 for malformed encoding, reject traversal, allow only GET/HEAD, return explicit MIME types, and add `X-Content-Type-Options: nosniff` | `planning/prototype as web app/tests/server.test.cjs`; prototype `check`; complete Playwright browser workflows |
| Optional visit notes lacked the project’s visible `(Optional)` suffix | Updated `forms.js`, regenerated the questionnaire inventory/field dictionary, and regenerated `QuestionnaireSections.kt` rather than editing generated outputs | `tools/generate-reference.cjs`; `tools/port-native-questionnaire.cjs --check`; Android tests |
| Current setup and acceptance docs still referenced the removed `browser/` shortcut, no Internet permission, Room v3, and pending attachment backup | Updated active command paths, offline/network description, D01–D18 acceptance summary, Room v1–v5 migrations, and completed attachment round-trip status | Documentation checker and generated-board verifier |
| Archived search ignored query text | Already fixed by the D01–D12 repair work and retained under `FoundationFixesTest`; no additional code change needed | `D01-D12-FIXES-2026-10-17.md` |

## Remaining scope, not low defects

- Physical-device reruns, TalkBack/large-text review, D19 interaction acceptance, D20 lifecycle/performance work, UAT, signing, and verified HTTPS App Links remain their scheduled work packages.
- Browser stale-tab/data-shape defects remain in the behavior-reference prototype and retain their separately recorded higher severity; this low-priority pass does not relabel or conceal them.

## Verification

- Prototype `check` passed, including malformed URL, traversal, MIME and handler-response regression assertions.
- Complete Playwright workflow, map and revision suites passed against the repaired live loopback server with no page errors.
- Questionnaire generation and `tools/port-native-questionnaire.cjs --check` passed for all 18 sections and 94 fields.
- Full Android `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed: 231 JVM tests, zero failures/errors/skips, 23 lint warnings and no lint errors, debug APK assembled.
- Documentation passed for 123 Markdown files and 715 local links; board verification passed for all 30 cards.
