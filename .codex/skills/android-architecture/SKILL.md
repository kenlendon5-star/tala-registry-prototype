---
name: android-architecture
description: MVVM, repositories, dependency injection, and clean module boundaries. Use when the task concerns this Android development area.
---

# Android Architecture Expert

## Apply when
Designing Android features, refactoring layers, or integrating ViewModels and repositories.

## Guidance
- First inspect the codebase architecture; align with established patterns unless explicitly redesigning.
- Prefer unidirectional data flow: UI events -> ViewModel -> use case/repository -> UI state.
- Keep Composables and Activities focused on UI and lifecycle; keep business rules outside them.
- Expose immutable `StateFlow` UI state from ViewModels; represent loading and failure explicitly.
- Use repositories as clear boundaries for data sources; separate network/database entities from domain models when complexity warrants it.
- Apply dependency injection consistently (Hilt if already in the project); avoid service locators and hidden singletons.
- Add use cases only when they provide meaningful orchestration/reuse; don't create layers without value.
- Preserve backward compatibility and consider process death, lifecycle cancellation, offline behavior, and errors.

## Workflow
1. Map current modules, dependencies and state ownership.
2. Propose minimal component responsibilities.
3. Implement with explicit interfaces only where useful.
4. Write ViewModel/repository tests for consequential logic.
