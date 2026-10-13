---
name: android-release
description: App signing, versioning, APK/AAB generation and Play release readiness. Use when the task concerns this Android development area.
---

# Android Release & Play Publishing Expert

## Apply when
Preparing Android releases, signed artifacts, CI, store submissions, or production rollout.

## Checklist
- Verify applicationId, versionCode, versionName, flavors and min/target SDK requirements.
- Build release variants using the checked-in Gradle wrapper; prefer Android App Bundles for Google Play distribution.
- Keep keystores and signing credentials out of source control; never reveal credentials in logs.
- Confirm release-only code shrinking/resource shrinking behavior and preserve required keep rules.
- Test install, startup, network, auth, critical journeys, crash reporting and upgrade/migration path on release builds.
- Check policy-sensitive disclosures, privacy practices, permissions, SDK integrations and target API requirements against current official Play guidance.
- Prepare staged rollout and monitoring/rollback plan when feasible.

## Common commands
- `./gradlew :app:bundleRelease`
- `./gradlew :app:assembleRelease`

Never claim a Play Console submission was made unless it actually occurred.
