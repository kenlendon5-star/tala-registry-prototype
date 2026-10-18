# D14 historical author AI review

**Status: Historical author review; supporting material for the owner-authorized Codex AI peer acceptance.** Claude Code wrote the original implementation and review notes on 18 October 2026. The original self-review did not establish the gate. The owner subsequently appointed Codex as AI peer; the [dated sign-off](reviewer-signoff.md) records that assessment and its limits. D23 UAT is a separate gate and does not replace D14 review.

## Method

- Compared the generated native specs (`QuestionnaireSections.kt`, regenerated from
  `planning/docs/schemas/questionnaire-current.json`, verified with
  `port-native-questionnaire.cjs --check`) against the prototype reference labels in
  `planning/prototype as web app/forms.js` via the field dictionary (18 sections / 94 fields).
- Walked the flow rules in `FormRules`, `InterviewRules` and the workspace UI
  (section chips, member scopes, review issues, reviewed guard) and the section
  suites listed in the coverage matrix.
- The scripted 18-section, two-member interview (`FullCoverageDeviceTest`) exercises
  required questionnaire answers and selected branches on-device; optional photos and other branches have separate acceptance; results are recorded in the D14 evidence files.

## Internal correction (separate from new scope)

| Finding | Decision | Action |
| --- | --- | --- |
| `education.track` label read "Program / track (optional)" — the only one of 94 fields with lowercase "optional" | Internal correction: standardize capitalization | The audit found the source JSON had not been updated. The follow-up fixes the JSON and regenerates the native spec; `--check` now passes. The prototype `forms.js` is intentionally left unchanged (behavior reference only, not packaged) |

Follow-up observation: optionality follows the source flags; Visit notes is optional
without an Optional suffix. This is nonblocking label-consistency polish. The sample
evacuation-site marking (`(Sample)` labels + confirming hint) is intact, and the
social-protection programs list remains a recorded demo inventory rather than an
authoritative one.

## Flow observations from the author review

- **Scoping**: the 5 member sections (Demographics, Other demographics, Migration,
  Education, Employment) key answers per member; the 12 household sections refuse
  member scopes and keep one shared draft bucket (ScopingTest suites; asserted again
  by the D14 device test checking `member_answers` stays empty for household routes).
- **Branching and clearing**: attendance, employment seven-day, illness/treatment,
  internet/connection, evacuation-known, disability → registered PWD → PWD ID,
  migration moved, callback-date-on-Callback, and consent typed-signature visibility
  all follow the prototype conditions; hidden dependent answers clear to a fixed point.
- **Exclusivity**: `None` / `Prefer not to answer` behave exclusively in the
  financial-accounts, hazards and programs multi-selects.
- **Validation**: strict calendar dates; birth dates carry `mustBePast`, while interview
  and visit dates follow their current source flags. Weekly hours 0–168, sleeping
  rooms 0–50, move year 1900–2026, phone format
  and record text limits.
- **Review/completion**: review issues aggregate across both member scopes plus the
  household scope; 18-of-18 readiness is required before "Mark interview reviewed";
  refusal is saved as a draft and never blocks progress, only review; completion is
  explicitly local ("This records local completion only"), never server sync.

## New scope / unresolved (recorded, not acted on)

- `demographics.relationship` offers "Head" to every member with no uniqueness
  constraint — prototype parity; a convention note for the demo, not a defect.
- Evacuation-area options remain marked `(Sample)` pending the barangay's approved
  list (documented in the coverage matrix).
- The social-protection programs list is not an authoritative coverage inventory;
  ambiguous prototype source notes stay unresolved (documented in the revision notes).
- Follow-up correction: roster display names derive from the demographics structured
  name, including middle name and extension (`RecordTextLimits.structuredName`).

## Verdict

These are historical author notes. The owner-authorized Codex AI peer decision, corrections and current verification are recorded in the [sign-off](reviewer-signoff.md) and [D14 evidence](../D14.md). Human usability/UAT remains separate.
