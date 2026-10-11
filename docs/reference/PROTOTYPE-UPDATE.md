# Prototype update: all sections and member employment

The browser prototype now supports a complete local workflow across all 18 proposed sections. The earlier implementation limits in BUILD-PLAN.md describe the initial delivery; this update supersedes those UI limits. The production architecture remains a proposal.

## What works

- Register households, edit household head/address/respondent, and search or filter records.
- Add, rename, switch between, and remove household members with explicit removal confirmation.
- Five per-member forms: Demographics, Education, Employment, Other Demographics, and Migration.
- All household forms: survey setup, visits, profile, health, food security, transport/finance, disaster preparedness, internet/public safety, social protection, water/sanitation, housing, and consent.
- Callback date/time and status are reflected in the household overview and reports.
- Automatic local saving; selected household/member survives reload in the same browser tab.
- Required-field validation, future birth-date rejection, numeric bounds, conditional fields, and exclusive None/Prefer not to answer multi-select options.
- Progress and review derive from actual answers for every member and every section.
- Drawn or typed demo signatures; refusal clears previous signature data and blocks completion.
- Finish review only after all 18 sections are ready. Editing a reviewed household returns it to In progress.
- Editable enumerator profile, per-household and all-record JSON exports, reports, and reset confirmation.

## Employment

Employment is the third tab after Demographics and Education. Saving education continues to employment; saving employment returns to the household members list. A member switcher allows comparing or continuing another member without leaving the section.

The initial question asks whether the member worked for pay or profit in the past seven days. Yes reveals occupation, industry, employment type, and hours (whole number, 0–168). No reveals job seeking and reason for not working; seeking work also reveals availability. Switching branches removes answers that no longer apply. Children can be recorded as not working with Below working age. These are illustrative prototype choices, not a claim that an official age threshold or labor questionnaire has been implemented.

## Code organization

- forms.js: 18-section schema with labels, controls, conditional visibility, optional fields, and numeric bounds.
- app.js: seed records, shared UI helpers, navigation icons, and consent dialog.
- workflow.js: rendering, state persistence, validation, progress, CRUD flows, signatures, exports, review and profile behavior.
- style.css: original visual system plus scrollable member tabs and updated form styles.
- verify.cjs: isolated browser checks; does not modify the user's browser profile or local records.

## Verification

Passed headless Edge checks for all 18 section destinations; employment required fields, bounds and branching; household/member reload context; exclusive multi-select; creation and renaming; filling all forms through UI controls; drawn signature storage and refusal clearing; typed signature; successful review and invalidation on edit; callback persistence; JSON download; profile editing; member removal; search; and 390px layout without horizontal overflow. No page errors were reported. Employment screenshot inspected at 390 × 844.

## Remaining production boundaries

This is a functional local prototype with illustrative questions, not a verified complete official CBMS form. Real authentication, encrypted local storage, backend synchronization, official coding, legal consent wording, and production deployment are not connected. Use fictional data only. Saving locally is not a backup; export if you want to preserve demo records outside this browser. No standalone offline application cache is included.
