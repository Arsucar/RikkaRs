# Research: Subagent config model & UI

## Model
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt` L59-97
- Fields include `enableMemory: Boolean = false` (ordinary memory tools), no memory-table field yet.
- Merge helpers: `mergeSubagentProfiles`, `upsertSubagentProfile`, `mergeInheritedFrom`.

## UI
- `AssistantSubagentProfilePage.kt` — profile editor; `enableMemory` Switch ~L701-707.
- `AssistantSubagentPage.kt` — list.
- Extension: `ExtensionSubagentProfilePage.kt`, `ExtensionSubagentsPage.kt`.
- Helpers: `SubagentUiHelpers.kt`.

## Persistence
- Stored on `Assistant.subagentProfiles` (and global settings profiles).
- kotlinx.serialization roundtrip covered in `SubagentModelTest.kt`.
