# Claude handoff — D15 native location

Implement **D15: Capture native location** in `android-native/`, following the card
in `planning/02-progress/build-board.cjs`. D14 has owner-authorized AI peer acceptance;
D23 human usability/UAT remains separate. Move D15 from Ready to In progress when
you start. Keep the one-card WIP limit.

## Required behavior

1. Add an injectable Kotlin foreground, request-on-use location provider with explicit
   permission/acquisition states in its ViewModel. Document the provider choice and
   verify its current Android APIs against official documentation. Keep domain rules
   Android-free and keep the app offline-first. Request foreground coarse/fine access
   only when the enumerator chooses to capture a household location.
2. Handle precise and approximate access, first denial, permanent denial, permission
   revocation, disabled location/GPS, unavailable provider, cancellation and timeout.
   Explain the outcome, allow retry/settings where useful, and keep questionnaire
   entry and saving usable. Never represent a stale last-known location as a new fix.
3. Display and persist the intended household's latitude/longitude, measured accuracy
   and original fix capture time. Provide validated manual coordinate correction and
   reopen it from Room. Manual coordinates must not display the old GPS accuracy as
   if it measured the edited point; document their timestamp/provenance semantics.
4. Use the existing Room v3 `CoordinateRow` and repository full-snapshot write stream.
   Load the latest record under the write lock and preserve answers, members,
   attachments and visit history. If additional persisted fields are necessary,
   increment the schema with a non-destructive migration, exported schema and
   preservation tests; proposed documentation schemas are not applied migrations.
5. Bind requests to household ID and request/edit generation. Cancel on navigation
   and reject late callbacks after household switches, a newer request, manual edits
   or screen disposal. A result for household A must never update household B.
6. Validate finite latitude ±90, longitude ±180 and finite nonnegative measured
   accuracy. Preserve explicit Idle/Saving/Saved/Failed write outcomes and manual
   drafts. Permission denial or failed persistence must never be shown as Saved.

Keep map SDK, tiles, markers and map selection in D16. D15 is the location capture,
manual coordinate and permission/failure workflow. Preserve the no-Internet/no-backend
scope, fictional data and the normal app's data during isolated device testing.

## Verification and evidence

- Add deterministic provider/ViewModel/repository tests for the outcomes above,
  including timeout, stale/cancelled callbacks, manual edits during acquisition,
  two-household isolation, invalid manual inputs and failed writes/retries.
- Run target-phone checks for precise/approximate grants, denial/permanent denial,
  GPS off and timeout. Exercise manual entry without location permission. Distinguish
  simulated provider cases from real device evidence; record unavailable cases honestly.
- Prove coordinate/accuracy/time persistence across a real force-stop and confirm
  questionnaire answers still save after location denial. Test migrations if used.
- Run appropriate existing storage/UI/interview regressions, JVM tests, isolated
  app/test builds and lint. Preserve the Gradle memory constraints. Use RTK for every
  shell command and use repeatable fixtures that preserve existing device records.
- Add `tools/verify-d15-device.ps1`, `planning/03-evidence/D15.md` and device artifacts
  under `planning/03-evidence/day15/`. Retain actual logs, screenshots, permission
  matrix, exact APK hashes and matching source fingerprints. Restore test-device
  settings and record actual totals and limitations.
- Update the native acceptance matrix (especially PR-21/TR-18), current data-model
  documentation if affected, and board inputs. Regenerate the board and questionnaire
  outputs through their generators. Run board/docs/generator and whitespace checks.

Mark D15 Done only when all three card items and the recorded acceptance pass:
**permission denial never blocks the interview; coordinates update only the intended
household and retain accuracy/time**. Leave D16 unstarted for the next review.

Return a concise change summary, test results, evidence paths, known limitations and
any acceptance cases that still require the owner's device interaction. Codex will
independently verify the result when the owner requests it. Do not claim peer approval
for D15 in advance.
