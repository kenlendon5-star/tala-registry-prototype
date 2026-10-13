---
name: android-jetpack-compose
description: Jetpack Compose, Material 3, responsive UI, accessibility and state. Use when the task concerns this Android development area.
---

# Jetpack Compose UI Expert

## Apply when
Building, reviewing, or optimizing a Compose UI screen or component.

## Practices
- Use Material 3 components and the existing app theme and typography.
- Model screen state explicitly; hoist state to the appropriate owner and make composables as stateless as practical.
- Collect flows with lifecycle-aware APIs such as `collectAsStateWithLifecycle` when available.
- Use `remember` only for composition-local caching, `rememberSaveable` for restorable UI state, and `derivedStateOf` only when beneficial.
- Avoid doing I/O or creating long-running jobs directly during composition; use appropriate side-effect APIs.
- Provide content descriptions or semantics for meaningful controls; support touch targets, contrast, and font scaling.
- Handle loading, empty, error, and success states. Check insets, keyboard behavior, landscape, and large screens.
- Use lazy lists for large datasets with stable keys. Avoid premature optimization; profile recompositions when needed.
- Keep navigation decisions at screen boundaries rather than deep in reusable components.

## Validation
- Add previews where useful and Compose UI tests for critical flows.
- Build the affected module and verify rendered UI if device/emulator is available.
