# Research: issue closure readiness for #31, #39, #41

- Query: Audit remaining GitHub issues #31, #39, and #41 for closure readiness from code, tests, task artifacts, and issue text.
- Scope: mixed
- Date: 2026-07-06

## Findings

### Inputs reviewed

- `.trellis/tasks/07-06-issue-backlog-completion/issue-closure-matrix.md` - current issue-by-issue evidence table and manual blockers.
- `.trellis/tasks/07-06-issue-backlog-completion/implement.md` - execution checklist and recorded validation commands.
- `.trellis/tasks/07-06-issue-backlog-completion/prd.md` - task acceptance criteria.
- `.trellis/tasks/07-06-issue-backlog-completion/design.md` - intended data/runtime/UI design.
- `.trellis/spec/app/index.md` - app-layer quality checklist.
- `.trellis/spec/app/conversation-model-resolution.md` - #31 conversation model override contract.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - cross-layer data-flow checklist.
- `.trellis/spec/guides/kotlin-concurrency-and-compose.md` - Compose/state caution guide.
- `gh issue view 31`, `gh issue view 39`, `gh issue view 41` - all three issues are still OPEN and have no comments.

### #31 - Per-conversation chat model override

Confirmed statically:

- `Conversation` now has nullable `chatModelId`, with `null` representing no override: `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt:16`.
- Room migration adds `conversationentity.chat_model_id` with empty-string default for old rows: `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_27_28.kt:11`.
- Repository maps nullable domain value to empty storage and back to `null`: `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt:321`, `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt:345`.
- Resolution order is conversation override, then assistant default, then global default: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:823`.
- Chat page passes the resolved model to `ChatInput`: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt:357`.
- Chat-page model picker callback calls `vm.setChatModel`, not assistant settings mutation: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt:397`.
- `ChatVM.setChatModel` persists only the current conversation with the selected model id if valid: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt:147`.
- Chat input model picker enables clear affordance: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt:257`.
- `ModelSelector(allowClear = true)` exposes a clear icon that sends `Model()`; because the id is not in settings, `ChatVM.setChatModel` stores `null`: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt:257`, `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt:149`.

Supporting tests / recorded validation:

- Fallback order covered by `ChatModelResolutionTest`: `app/src/test/java/me/rerere/rikkahub/data/datastore/ChatModelResolutionTest.kt:10`, `app/src/test/java/me/rerere/rikkahub/data/datastore/ChatModelResolutionTest.kt:30`, `app/src/test/java/me/rerere/rikkahub/data/datastore/ChatModelResolutionTest.kt:48`.
- New conversation has no override covered by `ConversationTest`: `app/src/test/java/me/rerere/rikkahub/data/model/ConversationTest.kt:47`.
- Task matrix records focused `ChatModelResolutionTest` / `ConversationTest` passed and final `lint`, `test`, and `:app:installDebug` passed on 2026-07-06.

Still needs manual device evidence before closure:

- Two-conversation validation from issue #31: conversation A override does not alter assistant default or conversation B; new conversations start with no override.
- UI validation that the clear icon returns the conversation to assistant/global default and the displayed model updates accordingly.
- A closure comment should include the above manual evidence plus the focused tests and final validation from the task matrix.

### #39 - Per-memory global/local scope

Confirmed statically:

- Flat memory model has per-row `MemoryScope` with `ASSISTANT` and `GLOBAL`: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt:71`.
- `MemoryEntity` stores a `scope` column: `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryEntity.kt:16`.
- Migration adds `scope`, marks legacy `__global__` rows as `GLOBAL`, and normalizes invalid values to `ASSISTANT`: `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_28_29.kt:12`.
- DAO effective reads return all global rows plus current assistant-local rows: `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryDAO.kt:24`.
- Repository add/update assigns `__global__` owner for global scope and current assistant owner for assistant scope: `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt:52`, `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryRepository.kt:77`.
- Generation injects effective memories, not a single assistant/global pool selected by `useGlobalMemory`: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:690`.
- `memory_tool` accepts optional `scope`, defaults new records from assistant `useGlobalMemory`, and allows edit scope changes: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTools.kt:21`, `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTools.kt:94`, `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTools.kt:100`.
- UI shows each memory scope and allows toggling between global and assistant scope: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:174`, `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:777`.
- New manual memory uses `assistant.useGlobalMemory` only as default-new-memory scope: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:513`.

Supporting tests / recorded validation:

- `MemoryRepositoryTest` covers global add owner/scope, global-to-assistant reassignment, and effective read filtering: `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryRepositoryTest.kt:13`, `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryRepositoryTest.kt:29`, `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryRepositoryTest.kt:53`.
- `MemoryToolsTest` covers default scope, explicit assistant create, optional edit scope, and global edit scope: `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryToolsTest.kt:16`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryToolsTest.kt:40`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryToolsTest.kt:65`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryToolsTest.kt:91`.
- Task matrix records focused `MemoryToolsTest` / `MemoryRepositoryTest` passed and final `lint`, `test`, and `:app:installDebug` passed on 2026-07-06.

Still needs manual device evidence before closure:

- Create a local memory and a global memory from `AssistantMemoryPage`; verify both are labeled correctly in the list.
- Toggle an existing memory between assistant and global scope; verify it remains visible in the correct assistant/global context.
- Migration smoke on an install with existing memories, especially legacy `__global__` rows becoming global and assistant rows staying local.
- Optional but useful: one generation smoke showing injected memories include current assistant-local plus global rows.

### #41 - Generic memory tables

Confirmed statically:

- Feature is disabled by default globally and per assistant: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:614`, `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt:29`.
- Auto-sync is hard-disabled in loaded/saved settings and UI: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:219`, `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:468`, `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:399`.
- Gate requires both global and assistant switches: `app/src/main/java/me/rerere/rikkahub/data/model/MemoryTable.kt:40`.
- Separate Room tables/entities are added for templates and documents, with `scope_type`, `scope_id`, `payload_json`, and `revision`: `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30.kt:11`, `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30.kt:23`.
- Database version 30 includes `MemoryTableTemplateEntity`, `MemoryTableDocumentEntity`, and `memoryTableDao()`: `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt:46`, `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt:77`.
- Schema `30.json` contains memory table tables and indexes: `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/30.json:592`, `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/30.json:645`.
- Effective document reads support global, assistant, and conversation scopes: `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryTableDAO.kt:34`.
- Repository supports template/document CRUD and revision bumping on document update: `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt:69`, `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt:87`.
- Static no-read helper returns empty when disabled: `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt:59`.
- ChatService only reads templates/documents when enabled; otherwise it uses empty lists: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:654`, `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:663`.
- ChatService only adds `MemoryTableInjectionTransformer` when enabled: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:691`.
- ChatService routes tool registration through `buildMemoryTableToolsIfEnabled`; the builder returns no tools when disabled: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:722`, `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTableTools.kt:21`.
- Transformer renders bounded `<memory_tables>` content and returns no injection when no documents are loaded: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformer.kt:19`, `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformer.kt:57`.
- Assistant memory UI exposes global/assistant switches, template/document list, and CRUD handlers: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:361`, `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt:602`, `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt:295`.

Supporting tests / recorded validation:

- `MemoryTableTest` covers the two-switch gate: `app/src/test/java/me/rerere/rikkahub/data/model/MemoryTableTest.kt:8`.
- `MemoryTableToolsTest` covers disabled no-tool registration and conversation/assistant/global scopes: `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryTableToolsTest.kt:18`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryTableToolsTest.kt:58`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryTableToolsTest.kt:87`, `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryTableToolsTest.kt:114`.
- `MemoryTableInjectionTransformerTest` covers supported scopes, row limit, token/char limit, and empty disabled-content case: `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformerTest.kt:11`, `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformerTest.kt:27`, `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformerTest.kt:43`, `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformerTest.kt:62`.
- `MemoryTableRepositoryTest` covers template normalization, effective scope selection, disabled no-read helper, and revision bumping: `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryTableRepositoryTest.kt:17`, `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryTableRepositoryTest.kt:29`, `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryTableRepositoryTest.kt:49`, `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryTableRepositoryTest.kt:69`.
- Task matrix records focused `MemoryTableInjectionTransformerTest`, `MemoryTableToolsTest`, and `MemoryTableRepositoryTest` passed and final `lint`, `test`, and `:app:installDebug` passed on 2026-07-06.

Still needs manual device evidence before closure:

- UI CRUD smoke for memory table templates and documents: create/edit/delete template; create/edit/delete assistant/global document; verify displayed scope/revision.
- Toggle global and assistant switches and verify the UI disables/enables table document actions as expected.
- Runtime disabled-mode smoke from UI/logs: with global or assistant switch off, send a message and verify no `<memory_tables>` system segment, no `memory_table_tool`, no table document read, and no sync job.
- Runtime enabled smoke: with both switches on and at least one document present, verify injection/tool availability in a controlled message run.

## Caveats / Not Found

- Follow-up manual validation completed on 2026-07-06 after the device was unlocked:
  - #31: UI confirmed conversation-level model override, clear-to-default, and new conversation default behavior.
  - #39: UI confirmed per-memory `仅本助手` / `全局` labels, edit-dialog global switch, save persistence, and switch-back behavior.
  - #41: UI confirmed global/assistant table switches, disabled auto-sync copy, template CRUD, document CRUD, scope controls, revision display, and cleanup of temporary data.
- Therefore #31, #39, and #41 are ready for evidence comments and closure.
- `python ./.trellis/scripts/task.py current --source` reported no active task, so this research used the task directory explicitly provided by the user.
- No Gradle, test, lint, compile, adb, or git commands were run in this audit.
- The task's `implement.jsonl` and `check.jsonl` still contain only the `_example` row, so context manifests are not useful evidence.
- The task matrix records final validation and install as passed, but this audit did not independently rerun them.
- Live GitHub issues #31, #39, and #41 were still open at research time and need closure evidence comments.
