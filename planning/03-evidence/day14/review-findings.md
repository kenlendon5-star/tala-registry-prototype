# D14 follow-up findings — 18 October 2026

The audit of commit `d360d34` found:

1. `port-native-questionnaire.cjs --check` failed because the source inventory still
   used `Program / track (optional)` while the generated Kotlin used `(Optional)`.
2. A fresh execution of the committed full-entry test against the already-used OPPO
   installation failed in `FullCoverageDeviceTest.save`: `ComposeTimeoutException`,
   condition not satisfied after 30000 ms. The test ran for 45.587 seconds and reported
   one failure. It reused fixed household/member IDs and retained earlier answers.
3. Claude's implementation review was recorded as satisfying the peer/adviser item.
   No independent reviewer's dated findings or sign-off was present.

The follow-up updates the source JSON and regenerates Kotlin, gives every interview
writer run fresh UUIDs persisted in the isolated app for its force-stop verifier,
and repeats the full interview/reopen pair on the same installation. The harness
checks generation before testing and includes the source JSON in its fingerprints.

The review record now distinguishes internal AI review from independent acceptance.
Use the [review worksheet](reviewer-signoff.md) for the remaining reviewer gate.
See [D14 evidence](../D14.md) for actual post-repair results and build identity.
