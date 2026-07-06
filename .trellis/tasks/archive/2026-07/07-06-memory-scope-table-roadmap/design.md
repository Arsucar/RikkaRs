# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt`
- Future table memory entities/settings/transformers/tools/UI.

## #39 Direction

Model memory scope per row. Generation should inject assistant-local rows plus global rows when memory is enabled. Existing assistant-level `useGlobalMemory` should be converted into either a compatibility default or a UI shortcut, not the only partitioning mechanism.

Implementation-ready shape:

1. Add a `scope` column to `MemoryEntity` with enum/string values `ASSISTANT` and `GLOBAL`.
2. Keep `assistant_id` as the owner/bucket for assistant memories; use `scope = GLOBAL` for globally injected rows.
3. Migrate existing rows where `assistant_id == MemoryRepository.GLOBAL_MEMORY_ID` to `scope = GLOBAL`; all other rows become `scope = ASSISTANT`.
4. Update repository reads so generation gets `assistant_id == currentAssistant.id AND scope = ASSISTANT` plus all `scope = GLOBAL` rows when memory is enabled.
5. Keep `memory_tool` writing to the active assistant scope by default. Add an explicit UI/tool path for global writes only after the read path is stable.
6. Treat `Assistant.useGlobalMemory` as a compatibility UI shortcut during migration, not as the sole storage partition.

## #41 Direction

Memory tables should be a separate subsystem from flat memories:

- gated by global, assistant, and optional conversation switches;
- backed by schema templates and scoped documents;
- injected by its own transformer;
- mutated by a dedicated table tool or manual sync job;
- fully inert when disabled.

Roadmap split:

- P0: data model spike only. Add disabled-by-default settings and no-op repository interfaces; prove disabled mode creates no prompt, tool, job, token cost, or document read.
- P1: schema templates and migrations for table definitions, without generation injection.
- P2: read-only injection transformer behind gates, with token accounting and tests.
- P3: mutation tools/manual sync jobs and UI management.

## Migration Risk

This group likely changes database schema. Coordinate with #31 if it also changes conversation entities.

Sequence #39 before #41. #39 touches the existing flat `memoryentity`; #41 should introduce separate table-memory entities so flat-memory migration and table-memory rollout do not share a single Room version unless explicitly batched.
