# Research: Issue #122 memory-table template ownership

- Query: What the reporter means by memory-table “templates”; where template definitions are persisted, read, edited, selected, and used; why commit `9230ba76` did not isolate them; and the minimal ownership/migration fix for `release/rikka-arsucar`.
- Scope: mixed
- Date: 2026-07-14

## Findings

### Conclusion and recommended minimal fix

The reporter's “template” is the persisted `MemoryTableTemplate`: the named schema definition (`name`, `description`, `schemaJson`) that describes table names, columns, injection/update policy, and limits. It is not the `MemoryTableDocument` payload containing actual rows. The UI makes the distinction visible: the assistant page labels the definition “Memory Table Template” and edits name/description (`AssistantMemoryPage.kt:227-260`), while the full-screen document editor edits template schema and document payload side by side (`AssistantMemoryTableDocumentEditorPage.kt:285-303`, `524-568`). Issue #122's reopening comment explicitly says the first fix isolated “记忆表数据” but left “模板定义” shared.

The minimal correct fix is to make every template row explicitly owned by either `GLOBAL` or one assistant, migrate all ownerless legacy rows to `GLOBAL`, and require an actor assistant ID for every normal template read/update/delete path.

Recommended ownership fields, reusing the existing enum to minimize surface area:

- `MemoryTableTemplate.scopeType: MemoryTableScopeType`, valid values only `GLOBAL` and `ASSISTANT`.
- `MemoryTableTemplate.scopeId: String`; normalize `GLOBAL` to `MemoryRepository.GLOBAL_MEMORY_ID` (`"__global__"`) and `ASSISTANT` to the owning assistant UUID string.
- Add matching non-null `scope_type` / `scope_id` columns and a `(scope_type, scope_id)` index to `MemoryTableTemplateEntity` / `memory_table_templates`.
- A template ID remains globally unique. Same-name templates for A and B are separate rows with separate generated IDs; there is no name-based override or merge.

Visibility and mutation contract:

- Effective templates for assistant A are `GLOBAL UNION ASSISTANT(scope_id=A)` only.
- A private assistant template may be read/updated/deleted only by its owner. A known ID must not bypass that rule.
- A global template remains visible to all assistants and retains current shared edit/delete behavior. Changing global-template mutability would be a separate product change.
- New templates created from an assistant page or by the memory-table tool default to `ASSISTANT/currentAssistantId`. A global template is created only by an explicit global choice.
- Ownership is immutable during ordinary update. Converting private↔global must not be an in-place update because it changes visibility and may orphan/expose documents; use a future explicit copy/promote operation if needed.
- `GLOBAL` documents may reference only `GLOBAL` templates. An assistant-private template may back documents owned by that assistant and conversations currently belonging to it, but not a global document. The existing document-editor global switch (`AssistantMemoryTableDocumentEditorPage.kt:421-450`) must therefore be disabled/rejected for a private template; silently promoting the template would violate isolation.

Minimal persistence/API changes:

1. Bump Room from 35 to 36 in `AppDatabase`, add `Migration_35_36`, register it in `DataSourceModule`, and generate schema 36. Migration SQL should add:
   - `scope_type TEXT NOT NULL DEFAULT 'GLOBAL'`
   - `scope_id TEXT NOT NULL DEFAULT '__global__'`
   - index on `(scope_type, scope_id)`
   All existing templates were shared before this change, so `GLOBAL/__global__` is the only deterministic, lossless migration. Inferring one assistant from linked documents is ambiguous because one template can already have global, multiple-assistant, and conversation documents.
2. Add DAO effective queries equivalent to the document queries: `GLOBAL OR (ASSISTANT AND scope_id=:assistantId)`, plus actor-scoped get/update/delete. Replace `@Insert(REPLACE)` for normal updates: `REPLACE` allows a caller with a known foreign ID to overwrite the row. Use insert-on-new plus guarded update, or an equivalent transactional DAO operation. Template delete and `deleteDocumentsByTemplate` must be one transaction and cascade documents only after the actor-scoped template delete is authorized; the current order deletes documents before deleting the template (`MemoryTableRepository.kt:108-111`).
3. Add repository APIs such as `getEffectiveTemplates[Flow](assistantId)`, `getEffectiveTemplate(id, assistantId)`, `upsertTemplate(template, actorAssistantId)`, and `deleteTemplate(id, actorAssistantId)`. Preserve stored ownership on update; normalize ownership only on create. Retain all-template APIs only for explicit export/import/maintenance paths and name them accordingly to discourage runtime use. Defensively filter DAO results as commit `9230ba76` does for documents.
4. Rewire only current consumers:
   - `AssistantDetailVM.memoryTableTemplates` and its upsert/delete methods use its `assistantId`.
   - `ChatVM.memoryTableTemplates` follows `conversation.assistantId` with `flatMapLatest`, parallel to `memoryTableDocuments`.
   - `ChatService.prepareGenerationRequest` reads effective templates for `assistant.id`; `buildGenerationTools` passes actor-scoped template closures, not repository method references.
   - `AssistantMemoryPage`, the document editor, conversation drawer/create selector, injection transformer input, and memory-table tool then receive only effective templates. A small UI/model defensive `isEffectiveFor(assistantId)` filter is still useful, but UI filtering alone is not the fix.

### Why assistant A's templates remain visible to assistant B

The template model and table have no ownership fields at all:

- `MemoryTableTemplate` contains only id/name/description/schema/timestamps (`MemoryTable.kt:14-22`).
- `MemoryTableTemplateEntity` mirrors exactly those fields (`MemoryTableTemplateEntity.kt:14-27`).
- Schema 35 creates `memory_table_templates` without `scope_type` or `scope_id` (`app/schemas/.../35.json:604-658`). In contrast, documents have both fields and an ownership index (`MemoryTableDocumentEntity.kt:16-38`).

Every template query is global:

- DAO `getTemplatesFlow()` / `getTemplates()` select every row, and `getTemplate(id)` is ID-only (`MemoryTableDAO.kt:13-26`).
- Repository `getTemplatesFlow()`, `getTemplates()`, and `getTemplate(id)` simply map those unscoped results (`MemoryTableRepository.kt:34-41`).
- `upsertTemplate` uses `REPLACE` through an unscoped DAO (`MemoryTableRepository.kt:93-105`); `deleteTemplate` accepts only an ID and deletes every linked document before deleting the definition (`MemoryTableRepository.kt:108-111`).

That unscoped list flows unchanged into both assistants:

- `AssistantDetailVM.memoryTableTemplates` calls global `getTemplatesFlow()` even though its document flow uses `getAssistantMemoryDocumentsFlow(assistantId)` (`AssistantDetailVM.kt:89-99`).
- `AssistantMemoryPage` renders every template (`AssistantMemoryPage.kt:457-466`, `774-797`). Creating a template produces an ownerless `MemoryTableTemplate`; VM mutations pass no assistant identity (`AssistantMemoryPage.kt:144-145`, `AssistantDetailVM.kt:295-303`).
- The document editor resolves a template by ID from the same global list and saves schema/name/description through the same unscoped VM method (`AssistantMemoryTableDocumentEditorPage.kt:84-95`, `285-303`). Thus editing A's document can change the shared schema B sees.
- `ChatVM` gives the conversation drawer and create selector every template, while documents correctly follow the conversation assistant (`ChatVM.kt:182-197`; `ConversationMemoryTableDrawer.kt:476-484`, `829-852`).
- `ChatService` injects all templates even though documents are assistant/conversation filtered (`ChatService.kt:955-970`, `987-995`). `buildMemoryTablePrompt` indexes and computes limits from the supplied template list (`MemoryTableInjectionTransformer.kt:95-115`), so an unrelated template can affect another assistant's effective injection limit as well as appear by ID.
- The AI tool is fully global for template definitions: list/create/update/delete use unscoped callbacks (`MemoryTableTools.kt:186-245`), wired directly to repository methods (`ChatService.kt:1083-1085`). A known template ID can be updated or deleted from any assistant.

### What commit `9230ba76` fixed and omitted

GitHub reports commit `9230ba766f20911bc83d3627444f686a9dfd0c9a` (`fix(memory): isolate assistant-scoped tables`) changed seven files and added 488/deleted 86 lines. Its changes were document-only:

- Added `isMemoryTableScopeEffective` / `MemoryTableDocument.isEffectiveFor` (`MemoryTable.kt:53-72`).
- Added repository defensive filtering and `getAssistantMemoryDocumentsFlow` (`MemoryTableRepository.kt:61-81`).
- Switched `AssistantDetailVM.memoryTableDocuments` to that helper and added scoped editor loading (`AssistantDetailVM.kt:95-105`).
- Added strict UI derivation and editor document-ID/scope resolution (`AssistantMemoryPage.kt:703-732`; `AssistantMemoryTableDocumentEditorPage.kt:893-943`).
- Added DAO/repository/UI tests for document visibility.

The commit did not modify `MemoryTableTemplate`, `MemoryTableTemplateEntity`, template DAO queries, template repository APIs, `ChatVM` template flow, `ChatService` template reads/tool callbacks, Room schema/version, or migration files. Therefore its statement that assistant-scope query/write/UI were isolated was true only for the document paths it touched, not for template definitions.

### Current scope flow by layer

| Layer | Documents | Templates |
|---|---|---|
| Model/entity | `scopeType` + `scopeId` exist | No owner fields |
| DAO | Effective suspend/Flow query includes global + current assistant + optional conversation (`MemoryTableDAO.kt:34-68`) | Select-all / ID-only (`MemoryTableDAO.kt:13-26`) |
| Repository | SQL result is defensively filtered (`MemoryTableRepository.kt:61-78`) | Direct unfiltered mapping and ID-only mutation (`MemoryTableRepository.kt:34-41`, `93-111`) |
| Assistant VM | Current assistant + global docs (`AssistantDetailVM.kt:95-105`) | All templates (`AssistantDetailVM.kt:89-93`) |
| Chat VM | Follows current conversation's assistant/conversation (`ChatVM.kt:187-197`) | All templates (`ChatVM.kt:182-185`) |
| Assistant UI/editor | Re-filters documents and rejects mismatched explicit IDs | Renders/resolves/updates global list by ID |
| Chat injection | Effective documents; optional conversation-only isolation | All templates passed to transformer |
| AI tool | Read list is effective, but ID mutation callbacks remain unscoped | All definitions listable and mutable by ID |

### Compatibility and migration implications

- Database: 35→36 must preserve every existing row as global. Full WebDAV/S3 backups copy `rikka_hub.db` (plus WAL/SHM), so restored schema-35 backups will migrate normally when opened (`WebDavSync.kt:150-164`, `S3Sync.kt:127-141`). No row should be cloned or assigned by linked-document heuristics.
- Standalone memory-table bundle: current version is 1 and serializes `MemoryTableTemplate` directly (`MemoryTable.kt:237-295`). Add ownership fields with defaults so v1 templates decode as global, and bump exported `MEMORY_TABLE_BUNDLE_VERSION` to 2. New code should accept v1 and v2; old code already rejects versions above its supported version, which is preferable to silently dropping assistant ownership and re-exposing private templates during a downgrade round-trip. A v2 round-trip must preserve owner fields, including duplicate-ID remapping.
- Imported assistant owners may not exist in a different installation. The minimal behavior is to preserve the owner ID (no silent global promotion) and report orphan/not-visible ownership; “import into current assistant” is a separate explicit remap feature.
- Conversation references: conversation documents store only `templateId`. When moving a conversation A→B, an A-private template must not become visible to B. The minimal safe behavior is to reject/remap such references; copying the private template to B with a fresh ID and remapping affected conversation documents is the lossless option. At minimum add a focused move test so the new filter does not silently inject `{}` schema.

### Focused tests required

1. `Migration_35_36_Test`: insert a schema-35 template, migrate/validate to 36, assert row remains and becomes `GLOBAL/__global__`; assert new index/schema.
2. `MemoryTableDAOTest`: global+A effective suspend/Flow results exclude B/unknown/conversation; actor B cannot get/update/delete A by known ID; failed unauthorized delete does not delete A's linked documents; global is visible to A and B.
3. `MemoryTableRepositoryTest`: create defaults to current assistant; same names for A/B yield independent IDs; polluted DAO results are defensively filtered; B cannot overwrite/delete A by known ID; global behavior is retained; owner is immutable on update.
4. `MemoryTableTest`: v1 JSON lacking ownership decodes as global; v2 encode/decode preserves assistant/global ownership; duplicate import remaps IDs without changing ownership or document links.
5. `AssistantMemoryTableScopeTest` (or a small template-selection test): assistant page/editor sees global+A only; B's template cannot resolve by ID; global-document toggle is rejected for an A-private template.
6. `MemoryTableToolsTest`: list/create/update/delete use current-assistant visibility; create defaults to A; explicit global remains shared; B known-ID update/delete fails.
7. Existing document-scope tests from `9230ba76` remain as regression coverage. Add only one chat-path assertion that A's effective template list is the list passed to injection/tool wiring; transformer rendering itself does not need a second ownership implementation.

### Files found

- `app/src/main/java/me/rerere/rikkahub/data/model/MemoryTable.kt` — serializable template/document models, scope enum, bundle format, and import conflict handling.
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryTableTemplateEntity.kt` — ownerless Room template row.
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryTableDocumentEntity.kt` — existing document ownership pattern to mirror.
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryTableDAO.kt` — global template CRUD versus scoped document queries.
- `app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt` — template CRUD, document defense filtering, bundle import/export, and destructive delete order.
- `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` — current Room version 35.
- `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` — manual migration registration.
- `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_29_30.kt` — original ownerless template table and scoped document table creation.
- `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_30_31.kt` — template description additive migration precedent.
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/35.json` — authoritative current schema.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` — assistant-specific document flow but global template flow/mutations.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt` — template creation/list/delete and document selection.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableDocumentEditorPage.kt` — schema/name/description edits and document global switch.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` — conversation-scoped documents but global template selector data.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationMemoryTableDrawer.kt` — template lookup and conversation-document creation selector.
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` — runtime injection and AI-tool callback wiring.
- `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformer.kt` — schema lookup and template-derived injection limits.
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryTableTools.kt` — AI-visible template list/create/update/delete actions.
- `app/src/androidTest/java/me/rerere/rikkahub/data/db/dao/MemoryTableDAOTest.kt` — document-only DAO coverage added by `9230ba76`.
- `app/src/test/java/me/rerere/rikkahub/data/repository/MemoryTableRepositoryTest.kt` — repository fake and current template/document tests.
- `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryTableScopeTest.kt` — document-only UI/editor scope regressions from `9230ba76`.
- `app/src/test/java/me/rerere/rikkahub/data/ai/tools/MemoryTableToolsTest.kt` — template action and document mutation test surface.

### External references

- Issue #122: https://github.com/Arsucar/rikkahub/issues/122 (open; reopened 2026-07-14 after template definitions were found still shared).
- Prior fix: https://github.com/Arsucar/RikkaRs/commit/9230ba766f20911bc83d3627444f686a9dfd0c9a.
- AndroidX Room 2.8.4 is pinned at `gradle/libs.versions.toml:29`; migration/testing references: https://developer.android.com/training/data-storage/room/migrating-db-versions and https://developer.android.com/training/data-storage/room/testing-db.

### Related specs

- `.trellis/spec/guides/cross-layer-thinking-guide.md` — ownership must stay consistent from model/storage through repository, runtime, and UI.
- `.trellis/spec/app/index.md` — app runtime quality checklist; no memory-table-specific contract currently exists.

## Caveats / Not Found

- `python ./.trellis/scripts/task.py current --source` reported no active task. The output path is nevertheless unambiguous because the user explicitly supplied `.trellis/tasks/07-14-issue-122-template-isolation`.
- Current files were inspected on `.git/HEAD -> refs/heads/release/rikka-arsucar`, whose loose ref was `8415583cc7352d33edb868ab2d97d63d95145963`; no Git command was run.
- The standalone repository `exportBundle` / `importBundle` APIs were found, but no current product UI caller was found. Compatibility still matters for tests and future/indirect use.
- Commit `9230ba76` also leaves document mutation callbacks unscoped in `ChatService` (`getDocument`, `upsertDocument`, `deleteDocument`). This artifact does not broaden the recommended implementation beyond the template fix, but implementation must avoid regressing document isolation and should not claim known-ID document authorization is complete without addressing or explicitly tracking that gap.
- No Gradle command was run; no product code or `.npmrc` was modified.
