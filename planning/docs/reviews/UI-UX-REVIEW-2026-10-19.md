# Tala native UI/UX review — 19 October 2026

Static review of the Compose UI in `android-native/app/src/main/java/ph/tala/registry/ui/` against the app's own core invariants (explicit save states, no fake completion, reviewed-only-by-review, archived recoverable), Material 3 defaults, and the field constraints the plan already commits to (D20 large text + TalkBack, direct-sunlight readability, one-handed fieldwork). No device run was performed; findings are code-level and each names its file and line. Fictional data only.

## What is already strong

- **Honest status everywhere.** `SaveStatusLine` (`households/EditScreens.kt:21`) renders Saving/Saved/Failed as a Polite live region; the workspace adds a real recovery path on failure ("Discard unsaved edits and reload saved section", `interview/InterviewWorkspaceScreen.kt:327`). The review screen says "records local completion only" next to the progress bar (`InterviewWorkspaceScreen.kt:275`) — no fake completion percentage.
- **Destructive actions are guarded and reversible-sounding.** Archive/restore and member removal both use confirm dialogs with consequence text that says exactly what is kept or deleted (`EditScreens.kt:131-146`, `211-219`).
- **Location card is a model failure-state UI.** Denied, permanently-denied, GPS-off and timeout each get a distinct next action (retry vs. open settings vs. re-check), manual correction is always available, and the card states that manual coordinates carry no accuracy (`location/LocationCard.kt:82-131`).
- **Signature pad is field-tested design**: strokes save on lift so a blank tap never signs, the pad owns its gesture stream so drawing never scrolls the form, undo/redo/clear enablement is exact, and the pad stays white-on-dark-ink in both themes (`interview/SignaturePad.kt`).
- **Touch targets and type**: primary buttons are `heightIn(min = 48–52.dp)` consistently; choice/multi-select rows are enclosed 52dp cards with the selection fill doubling as the state cue — good for sunlight and one-handed use (`form/SectionForm.kt:136-208`).
- **Validation UX**: a count-first error summary ("N answers need attention"), focus moved to the first invalid field only on an explicit Check (never on ordinary edits), `error()` semantics on choice groups, and hidden dependents cleaned to a fixed point before review.
- **Adaptive layout**: two-pane workspace at ≥ 840dp keeps the section index and member selector visible (`InterviewWorkspaceScreen.kt:43,58-63`); task screens hide the bottom bar so a mis-tap cannot abandon an edit (`TalaApp.kt:87-90`).

## Findings

| # | Severity | Finding | Where |
|---|---|---|---|
| 1 | **High (a11y)** | Section status icons carry no semantics. The 16dp drawn check/warning/circle is the *only* readiness cue on a section chip, and TalkBack reads neither the glyph nor the status. `BackArrowGlyph` and `MenuGlyph` got `contentDescription`; `SectionStatusIcon` did not. | `InterviewWorkspaceScreen.kt:374-408` |
| 2 | **High (a11y)** | Filter chips (section chips, household filters, interview tabs) are 32dp tall — below the 48dp minimum the rest of the app observes. Fieldwork with gloves or sun glare makes these the hardest targets in the app. | `InterviewWorkspaceScreen.kt:87-95,179-188`, `TalaApp.kt:411-415,477-481` |
| 3 | **Medium (trust)** | The two-pane member selector shows a check icon for the *selected* member regardless of readiness (`SectionStatusIcon(if (selected) SectionStatus.Ready else null)`). Sighted users will read it as "this member is done". It should show the member's real status or nothing. | `InterviewWorkspaceScreen.kt:164` |
| 4 | **Medium (consistency)** | The Interview "Sections" tab lists 18 sections including "Household members" at position 04, but the workspace renders 17 chips (members excluded) and numbers the rest accordingly — "Section 04" in the workspace is "Demographics", not the list's 04 "Household members". Numbering should agree. | `TalaApp.kt:547-555,702-707` vs `InterviewWorkspaceScreen.kt:57` |
| 5 | **Medium (large text)** | The top app bar stacks two text lines (title + "Tala · barangay") inside `TopAppBar`'s fixed 64dp row. At large font scale the second line clips. A `MediumTopAppBar` or a single-line subtitle strategy is safer. | `TalaApp.kt:112-122` |
| 6 | **Medium (flow)** | Member-scoped empty state says "Add a household member before answering these questions" but offers no action and the workspace has no add-member affordance — the enumerator must Save & back, switch to Members tab, then Add. An inline button (or a pointer to the exact path) removes the dead end. | `InterviewWorkspaceScreen.kt:206-207` |
| 7 | **Low (a11y)** | "Save & back" / "Review interview" are `TextButton`s (40dp min height) in the workspace header — the smallest persistent controls in the primary task screen. | `InterviewWorkspaceScreen.kt:50-53` |
| 8 | **Low (a11y)** | The signature pad has no accessibility path for the drawn mode itself; the typed-name fallback exists but is not labelled as the accessible alternative, and the pad canvas is silent to TalkBack. | `interview/SignaturePad.kt:67-97`, `InterviewWorkspaceScreen.kt:433` |
| 9 | **Low (a11y)** | Decorative text glyphs read poorly on TalkBack: "▾" in the select trigger, "‹"/"›" in Previous/Next. Drawn glyphs with descriptions, or plain words, are safer. | `SectionForm.kt:170`, `InterviewWorkspaceScreen.kt:312,320` |
| 10 | **Low (dark theme)** | `surfaceContainer` equals `surface` in the dark scheme, so tonal-elevation surfaces (sticky bottom bars, form footers) lose their separation layer in dark mode. | `theme/Theme.kt:53-54` |
| 11 | **Low (polish)** | When `phase == Idle` with a lingering message, the workspace prints it unstyled above `SaveStatusLine` (which intentionally hides at Idle) — an uncoloured orphan line that can look like an error. Same pattern on the profile screen. | `InterviewWorkspaceScreen.kt:325`, `TalaApp.kt:668` |
| 12 | **Low (home)** | "Continue an interview" shows the first two households in list order, not the most recently touched; the heading promises recency the content doesn't deliver. Sort by last visit/save or retitle. | `TalaApp.kt:370-371` |

## Recommendation order

1. **Before D22 regression / D23 UAT:** #1, #2, #3 — all three are cheap, mechanical fixes (semantics + `heightIn(min = 48.dp)` + real status), and all three are exactly what a TalkBack pass or an uncoached reviewer will hit first.
2. **With the D22 pass:** #4, #5, #6 — consistency and large-text; #5 is verifiable by the D20 font-scale pass on the target phone.
3. **Backlog or with UAT feedback:** #7–#12.

None of these contradict an invariant; #3 is the only one that touches an honesty property (a status cue showing a state that isn't true), which is why it is ranked above its cosmetic cousins.
