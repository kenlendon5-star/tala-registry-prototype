# Household profiling revisions

Implemented in the browser prototype served at http://localhost:5173.

- Home shows registered households, live total/male/female member counts, unrecorded sex counts, and enumerator data. Enumerator and household profile pictures support upload, preview, removal, and browser-local persistence.
- Household and member forms include first, middle, and last names, extension, nickname/alias, and contact number. Structured name edits update the display name. Existing full names are retained without guessing their component parts. Middle name, extension, alias, phone and pictures are optional.
- Contact numbers accept 11 digits and format on blur as 4-3-4; other formats are flagged when validating the form.
- Household profile adds previous city, house safety, and an optional reason with a CCTV example.
- Disaster preparedness adds disaster type and an evacuation dropdown with sample choices and an Other field. Saved free-text evacuation areas migrate to Other.
- Education adds SNED/SPED school options and educational assistance. Employment retains its seven-day reference period and asks whether the member earned for pay or profit.
- Member demographics add voter registration. Other demographics add Solo Parent ID and conditional disability → registered PWD → PWD ID. Hidden dependent answers are cleared.
- Social protection adds PhilHealth membership/dependent coverage, DSWD assistance, and 4Ps yes/no questions.

The ambiguous Cash/count notes, unidentified abbreviation/program, and covered education follow-up were not assigned invented meanings. The numbers 15/30 are not hardcoded; counts reflect saved records. Actual evacuation sites still need to replace the labeled sample choices.

Verification: `npm run check`, `node verify.cjs`, `node verify-map.cjs`, and `node verify-revisions.cjs`. Browser scripts use Playwright and installed Microsoft Edge. The revision checks cover names, counts, phone formatting, image upload, conditional clearing, persistence, and mobile layout.
