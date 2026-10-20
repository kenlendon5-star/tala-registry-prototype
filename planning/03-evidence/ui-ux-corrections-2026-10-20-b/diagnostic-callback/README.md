# Callback test failure diagnostic — solo reproduction, 20 October 2026

`callbackNoRespondentRefusalAndPartialResume` failed twice in a row at the same
`await("interview-screen")` (harness runs 1 and 2), so per UX-11 it was reproduced solo with
periodic screenshots instead of being retried.

- `frame-008.png` — last rendered state: visit form with the IME open, page scrolled to the
  callback-appointment field, workspace header scrolled out of view.
- `frame-011.png` / `frame-012.png` — the 30-second wait window ending blank after teardown.
- `callback-diag.log` — solo `am instrument` output: same ComposeTimeoutException at line 174.

## Root cause

The UX-03 correction makes the workspace use the single-scroll compact layout whenever the
available height drops below 560 dp — which includes the IME being open. In that layout the
"Save & back" header buttons scroll with the page. The test scrolled down to
`field-callbackDate` (header off-screen), left the IME open, and clicked "Save & back";
Compose `performClick` does not scroll a node into view, so the tap no-ops and navigation
never happens. Deterministic, and only reachable on the corrected layout — the test was
written against the old fixed-header workspace.

## Fix applied (test only)

`androidx.test.espresso.Espresso.closeSoftKeyboard()` before both "Save & back" clicks in
`InterviewLifecycleDeviceTest`, mirroring the existing pattern in
`WorkspaceSemanticsDeviceTest`. No product code changed: with the keyboard open the
save/navigation path remains reachable by scrolling, which is the UX-03 acceptance criterion.
