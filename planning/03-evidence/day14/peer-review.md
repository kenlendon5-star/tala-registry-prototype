# D14 internal AI review — peer/adviser acceptance pending

**Status: Internal review only; independent sign-off pending.** Claude Code wrote the original implementation and review notes on 18 October 2026. That self-review does not establish the planned peer/adviser gate. Use the [review worksheet](reviewer-signoff.md) to record the actual reviewer, outcomes and decision. D23 UAT is a separate gate and does not replace D14 review.

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

No other label defects found: all optional labels carry "(Optional)", the sample
evacuation-site marking (`(Sample)` labels + confirming hint) is intact, and the
social-protection programs list remains a recorded demo inventory rather than an
authoritative one.

## Flow observations from the AI review — awaiting independent review

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
- **Validation**: strict calendar dates with `mustBePast` for birth/interview/visit
  dates, weekly hours 0–168, sleeping rooms 0–50, move year 1900–2026, phone format
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
- Roster display names derive from the demographics section only; middle name /
  extension do not alter the roster label (prototype parity).

## Verdict

Independent peer/adviser review remains pending. These notes support the reviewer; they do not approve the week-two gate. Technical execution and repeatability outcomes are recorded separately in [D14 evidence](../D14.md).
