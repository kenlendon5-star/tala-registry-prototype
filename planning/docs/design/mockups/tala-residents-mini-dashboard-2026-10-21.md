# Residents mini dashboard draft

Created on 21 October 2026 with built-in image generation, editing the user-supplied crop.

[Mini dashboard image](tala-residents-mini-dashboard-2026-10-21.png)

This is a visual concept, not an implemented screen. Fictional demonstration data: 39 active households, 4 callbacks, 32 recorded residents: 5 Male, 6 Female, 21 Sex not recorded. These categories match the existing registry summary. They describe recorded sex, not inferred gender or full barangay population.

During implementation use live RegistryUiState values. Compute segmented-bar widths from counts (5/32, 6/32, 21/32 in this example); generated image proportions are illustrative. Provide a clear zero-residents state and readable labels at large text sizes. Preserve the two quick actions and their planned routing corrections.

## Edit prompt

Use case: ui-mockup. Edit the attached cropped Tala mobile home interface into a refined compact mini dashboard. Preserve its cool mist background, deep peacock teal, navy headings, premium native app appearance, approximately 580px logical width, and the existing quick action section below. Extend the crop vertically as needed for a readable dashboard, but keep it compact. This is a design draft using fictional demonstration counts.
Keep heading 'Registry overview' and right-aligned 'Browse all' with arrow.
Replace the existing one-row statistics card with this deliberate mini dashboard:
First a row of two small equal-width white stat cards: a minimal house outline icon and large '39' with label 'Households'; calendar outline icon and large '4' with label 'Callbacks'. Use subtle hairline borders and softly rounded corners, no heavy shadows.
Below those, one full-width refined white resident breakdown card. Top line: left title 'Recorded residents', right a prominent teal '32' with small label 'Total'. Small supporting caption 'Across active households'.
Inside this card include a clean thin horizontal segmented bar with exactly three proportional segments representing 5, 6, and 21 out of 32. Segment colors: deep teal for Male, muted blue for Female, light slate gray for Sex not recorded. All three segments remain visible, longest is gray. Below the bar show three neatly aligned labeled columns with small matching colored dots, large clear counts, and readable labels: '5' / 'Male', '6' / 'Female', '21' / 'Sex not recorded'. Allow the last label to wrap elegantly. No percentages, no pie chart, no fabricated demographic categories. Add a small understated note 'Based on recorded sex' to clarify classification.
Keep the 'Quick actions' heading and the two original action tiles below: person-plus icon and 'Add household member'; folded-map outline icon and 'View map'. Preserve the small closing line 'Small visits. A fuller picture.'
Ensure the resulting mini dashboard is visually coherent and balanced, with numeric hierarchy, generous touch targets, precise spacing, legible body text, consistent 16px corner radius, no extra controls or decorations. No smartphone frame, no perspective, no poster text. Render only this redesigned section as a clean flat UI crop.

