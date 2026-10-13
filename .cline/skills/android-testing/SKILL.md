---
name: android-testing
description: JUnit, coroutine testing, repository/ViewModel tests and Compose UI tests. Use when the task concerns this Android development area.
---

# Android Testing & QA Expert

## Apply when
Adding tests, fixing regressions, or auditing Android test coverage.

## Practices
- Identify existing test frameworks and use them rather than creating parallel conventions.
- Favor fast JVM unit tests for business logic; use instrumentation for Android integration and UI behavior.
- Test outcomes and user-visible behavior, not fragile implementation details.
- For coroutines use `runTest` and a controllable test dispatcher; manage `Dispatchers.Main` replacement where required.
- Fake repository/data-source boundaries with deterministic state, including loading, failures, retries, and cancellation.
- For Compose, use semantics-based selectors and assert important accessible behavior; avoid timing sleeps.
- Test Room migrations and SQL behavior with suitable database tests when schemas change.
- Keep tests independent and ensure resources/dispatchers are cleaned up.

## Commands
- `./gradlew testDebugUnitTest`
- `./gradlew connectedDebugAndroidTest` (requires a device/emulator)
- Use module-specific tasks and flavors as applicable.

## Reporting
State which commands actually ran, their results, and any environment limitations.
