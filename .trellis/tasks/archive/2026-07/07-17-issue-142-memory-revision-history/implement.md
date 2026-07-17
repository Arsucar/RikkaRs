# Implementation Plan

1. Read relevant data/frontend Trellis specs and exact MemoryTable repository/DAO/UI code.
2. Wire snapshot DAO into production repository and add authorized history/rollback helpers.
3. Add/extend DAO and repository tests for snapshot cleanup, ordering and rollback-new-revision semantics.
4. Implement reusable history/detail UI and connect it to existing document revision entry points.
5. Add localized strings required by the new UI and focused pure/state tests where practical.
6. Run targeted tests and compile; perform an independent review before commit.

## Validation

- `./gradlew --no-daemon :app:testDebugUnitTest --tests me.rerere.rikkahub.data.repository.MemoryTableRepositoryTest`
- Relevant Android DAO test compilation or instrumentation only if a device is available and the test is added there.
- `./gradlew --no-daemon :app:compileDebugKotlin`

## Risk / Rollback

- DI constructor changes affect all Repository consumers; compile immediately after wiring.
- UI should not claim unavailable audit metadata.
- Do not disturb unrelated legacy duplicate-name fixes already committed.
