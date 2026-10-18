# D14 reviewer phone walkthrough (allow 45–60 minutes)

Companion to the [review worksheet](reviewer-signoff.md): tick each worksheet row as
you finish the matching part below. Record every finding — however small — in the
worksheet's findings table, marking each as a **required correction** or **proposed
feature/new scope**. Use fictional data only.

## Before you start

- OPPO CPH2529, Android 15. Open the isolated **Tala** test app (package
  `ph.tala.registry.d08test`); it works fully offline — airplane mode is fine.
- The automated interview seeded a household named **Lifecycle Acceptance** (the
  highest HH display code on the Households list) with members **Maria Sample** and
  **Juan Sample**, completed and marked Reviewed. Inspect it freely; make your own
  edits only in a separate fictional household (Part E) so the sample stays intact.
- From a household record, "Try a practice interview" opens a session-only shared
  controls example. It has no full questionnaire navigation, saved review or housing
  section. Use it only for the practice checks explicitly identified in Part D.

## Part A — Walk all 18 sections in the sample (10 min)

Open Lifecycle Acceptance → Continue questionnaire. Walk the 17 form-section chips
and inspect the roster separately on the record screen: together these are the 18
questionnaire sections. For each section, check the labels and its worksheet row:

1. **Survey setup** — enumerator, barangay, purok, interview date.
2. **Visit information** — visit date/result; the visit history below the form shows
   a dated "Completed visit" entry.
3. **Household profile** — name fields, optional labels, "Is Your House Safe?",
   address, respondent.
4. **Members / roster** — Maria and Juan both listed (go back to the record screen).
5. **Health** → **Food security** → **Transport & finance** → **Disaster
   preparedness** → **Internet & public safety** → **Social protection** → **Water,
   sanitation & waste** → **Housing** — shared household answers. The sample answers
   evacuation-known No, so evacuation choices are hidden. Check those choices on
   your separate record in Part D. Programs remain a demo inventory.
6. **Consent & review** — the sample uses the typed signature "Maria Sample". Its
   drawing pad is blank and no saved drawn preview is expected. Part E checks a
   drawn mark and preview on your separate record.

## Part B — Two members and member scoping (5 min)

In **Demographics**, **Other demographics**, **Migration**, **Education** and
**Employment**, switch the member chips:

- Maria (Head, Female, not attending school "Completed schooling", Teacher) vs Juan
  (Spouse, Male, attending Private school with track, Vendor, moved from Cebu City).
- Answers must stay distinct per member; household sections must show the same
  values regardless of the selected member.

## Part C — Branching already visible in the sample (5 min)

- Education: Maria shows "Reason for not attending"; Juan shows school type + track.
- Migration: Maria shows no previous-residence questions; Juan shows previous city
  and year moved.
- Employment: both worked "Yes" — each shows occupation/industry/type/hours.

## Part D — Validation and clearing (practice plus a separate saved record)

In "Try a practice interview", use the household/member scope selector and try:

- An invalid birth date (e.g. `2025-02-30`), a future birth date, hours `200` after
  answering Worked Yes, and an incomplete phone. Use **Check answers**: errors
  should be clear and the first invalid field focused.
- Worked Yes→No must remove occupation/type/hours and clear their answers.
- Exclusivity: in a multi-select with None/Prefer not to answer, choosing a normal
  option must clear None and vice versa.
- Practice answers remain session-only; it has no Review interview action.

Next, add a separate fictional household with two members and open **Continue
questionnaire**. Keep this record for Part E. Here, test:

- Housing rooms `60` → Check answers; correct it to `2` afterward.
- Education attendance Yes with school/track filled → No: dependent fields vanish.
  Save, switch away/back, and confirm hidden values do not return.
- Disaster evacuation-known Yes: inspect Sample choices and hint. Choose Other,
  type a fictional venue, then change evacuation-known to No. Hidden values clear.
- Review interview with gaps: an issue opens the affected section/member, and
  Mark interview reviewed stays disabled while required answers are missing.

## Part E — Your own full interview (15 min, separate household)

Continue your separate household with two members through setup → visit → household
profile → all five member sections for each person → services → consent. Use the
accepted sample as an answer reference and keep the two members' answers distinct:

- Enter a callback visit result to see the callback date field; save; use
  "Record visit result" and confirm the history entry. Leave and reopen using the
  Callback filter. Then record No respondent and Refused outcomes and inspect the
  ordered history; a draft remains recoverable.
- Save draft, leave to the Home screen and reopen: answers must reload.
- Choose consent No, save, and confirm review cannot grant Reviewed. For the next
  fictional consent scenario, choose Yes, draw a mark and test Undo, Redo and Clear.
  Save another mark, leave/reopen, and confirm its preview. Clear it and try a typed
  fictional signature as the other mode.
- Record a Completed visit outcome for this final fictional scenario. Finish any
  remaining required answers until review shows 18 of 18 ready; select **Mark
  interview reviewed** and confirm the explicit local-completion message.

## After the walkthrough

Transfer your findings, decision and sign-off into [reviewer-signoff.md](reviewer-signoff.md).
The developer then applies approved corrections, reruns the relevant tests, and
updates the D14 evidence, acceptance matrix and board before the card can leave Verify.
