---
name: android-room
description: Room entities, DAO design, schema migrations and offline-first storage. Use when the task concerns this Android development area.
---

# Android Room Database Expert

## Apply when
Working with Room entities, DAO queries, databases, migrations and local persistence.

## Practices
- Define clear entities, indexed columns where needed, foreign keys, and explicit TypeConverters.
- Expose reactive reads through `Flow` where updates should be observed; use suspend functions for one-shot operations.
- Treat migrations as production code: never use destructive fallback for user data unless specifically authorized.
- Export schemas and test upgrade paths when the existing project supports that workflow.
- Run multi-step writes in transactions; enforce constraints and handle conflicts explicitly.
- Keep Room entities isolated from external API payload types unless project design deliberately combines them.
- Don't access Room from the main thread or embed SQL in UI layers.

## Validation
Inspect existing schema versions and migrations; test DAO behavior and schema upgrades.
