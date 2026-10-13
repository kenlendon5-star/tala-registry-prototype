---
name: android-kotlin
description: Kotlin coding, coroutine usage, Flow, and Android language conventions. Use when the task concerns this Android development area.
---

# Android Kotlin Expert

## Apply when
Implementing, reviewing, or refactoring Kotlin source code.

## Practices
- Follow Kotlin idioms and existing project conventions; prefer `val`, data classes, sealed interfaces for bounded state, and explicit null-handling.
- Use structured concurrency. Launch work in `viewModelScope` and lifecycle-aware scopes; never use `GlobalScope`.
- Prefer suspend functions for one-shot async calls, Flow for streams, and StateFlow for observable state.
- Keep dispatchers injectable when testability matters. Never block the main thread with I/O.
- Handle cancellation separately: rethrow `CancellationException`; avoid indiscriminate `catch (Exception)`.
- Keep public APIs simple; avoid `!!` except when an invariant has been established.
- Avoid introducing dependencies or language-feature upgrades without checking project versions.

## Workflow
1. Inspect Kotlin version, module structure, and existing patterns.
2. Make the smallest cohesive change that fulfills the task.
3. Add or update tests when meaningful.
4. Run targeted compilation/tests if a working Gradle environment is present; report actual outcomes.
