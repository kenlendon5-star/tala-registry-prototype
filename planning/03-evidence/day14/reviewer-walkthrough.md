# D14 reviewer phone walkthrough (~30–40 minutes)

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
- "Try a practice interview" (Home) is session-only: nothing you type there is
  saved. Use it for the validation/branch-clearing checks in Part D.

## Part A — Walk all 18 sections in the sample (10 min)

Open Lifecycle Acceptance → Continue questionnaire, and walk the section chips left
to right. For each section, check the labels read clearly and the flow matches the
worksheet row:

1. **Survey setup** — enumerator, barangay, purok, interview date.
2. **Visit information** — visit date/result; the visit history below the form shows
   a dated "Completed visit" entry.
3. **Household profile** — name fields, optional labels, "Is Your House Safe?",
   address, respondent.
4. **Members / roster** — Maria and Juan both listed (go back to the record screen).
5. **Health** → **Food security** → **Transport & finance** → **Disaster
   preparedness** → **Internet & public safety** → **Social protection** → **Water,
   sanitation & waste** → **Housing** — shared household answers. On Disaster, check
   the evacuation options carry visible "(Sample)" wording and the confirming hint;
   on Social protection, check the program list reads as a demo inventory.
6. **Consent & review** — the consent question, the drawn signature pad (a saved
   mark preview appears) and the typed-name field.

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

## Part D — Validation and clearing (10 min, practice interview)

In "Try a practice interview", pick any section and try:

- An invalid date (e.g. `2025-02-30`), a future birth date, hours `200`, rooms `60`,
  an incomplete phone — then **Check answers**: clear inline errors, first field
  focused.
- Branch reversal: answering "Currently attending school?" Yes→No must remove the
  school/track questions and their typed answers.
- Exclusivity: in a multi-select with None/Prefer not to answer, choosing a normal
  option must clear None and vice versa.
- **Review interview** with gaps: the issue list jumps to the section/member with
  missing answers; "Mark interview reviewed" stays disabled until ready.

## Part E — Your own full interview (15 min, separate household)

Add a fictional household and member, then walk setup → visit → household profile →
member sections → services → consent:

- Enter a callback visit result to see the callback date field; save; use
  "Record visit result" and confirm the history entry.
- Save draft, leave to the Home screen and reopen: answers must reload.
- Choose consent "Yes", sign by drawing **or** typing a fictional name, then
  **Review interview → Mark interview reviewed**. Confirm the "local completion
  only" wording. (Optional refusal check: consent "No" must save but never grant
  Reviewed.)

## After the walkthrough

Transfer your findings, decision and sign-off into [reviewer-signoff.md](reviewer-signoff.md).
The developer then applies approved corrections, reruns the relevant tests, and
updates the D14 evidence, acceptance matrix and board before the card can leave Verify.
