---
name: android-gradle
description: Android Gradle Plugin, Kotlin DSL, build diagnostics and dependency management. Use when the task concerns this Android development area.
---

# Android Gradle & Build Expert

## Apply when
Editing Gradle builds, resolving sync failures, upgrading dependencies or managing variants.

## Practices
- Inspect `gradle/libs.versions.toml`, wrapper version, AGP version, Kotlin version, compileSdk, JDK and existing plugin usage before changes.
- Prefer the project's version catalog and Kotlin DSL conventions; avoid mixing incompatible plugin declarations.
- Check Kotlin/AGP/Compose compiler compatibility before version upgrades; prefer official release notes.
- Keep dependencies scoped to their correct modules and configurations (`implementation`, `testImplementation`, etc.).
- Don't arbitrarily increase minSdk/targetSdk or introduce insecure repository endpoints.
- Diagnose from the first meaningful error and its root cause, not the last cascading message.
- Never put signing secrets, API keys, or credentials in tracked Gradle files. Use environment variables or protected properties.

## Troubleshooting sequence
1. Confirm Gradle wrapper/JDK and environment.
2. Run a focused task, e.g. `./gradlew :app:assembleDebug --stacktrace`.
3. Identify plugin, dependency resolution, compilation, or tooling source.
4. Apply the minimum fix; rerun same task and relevant tests.
5. Explain what was changed and what was not verified.
