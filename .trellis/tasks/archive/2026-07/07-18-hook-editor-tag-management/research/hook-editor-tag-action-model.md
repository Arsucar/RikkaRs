# Research: Assistant Hook editor & tag action model (#150)

- **Query**: Current Assistant Hook editor and tag action model for issue #150 refactor planning (ADD_CONVERSATION_TAG / TRANSITION_CONVERSATION_TAGS → unified tag management)
- **Scope**: internal (app module primarily)
- **Date**: 2026-07-18

## Findings

### 1. UI — AssistantHooksPage / HookActionEditor / TransitionTagSelector

| File Path | Key symbols | Line anchors |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantHooksPage.kt` | `AssistantHooksPage`, `AssistantHookEditorPage`, `HookActionEditor`, `TransitionTagSelector`, `validateHookEditor`, `hookActionLabelRes` | L89–301 list; L304–527 editor; L549–808 action editor; L811–843 transition selector; L861–954 validation; L956–960 labels |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt` | `upsertHook`, `setHookEnabled`, `moveHook`, `deleteHook`, `mutateHooks` | L367–424 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | route → `AssistantHooksPage` | L448 |
| `app/src/test/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantHooksPageTest.kt` | editor validation unit tests | full file |

#### List page (`AssistantHooksPage`)

- Loads assistant hooks from `AssistantDetailVM`, tags via `conversationTagsUiState`.
- Reorderable list; each card shows name, model · action label, enable switch, delete menu.
- **Only `TransitionConversationTags`** gets a secondary summary line: `removeName → addName · filter label` (L204–235).
- `AddConversationTag` has **no** allowlist summary on the list card.
- Add / edit navigates to `Screen.AssistantHookEditor(id, hookId?, conversationId)`.

#### Editor page (`AssistantHookEditorPage`)

Sections (LazyColumn):

1. **Basic** — name, enabled switch  
2. **Runtime** — fixed trigger text (`AFTER_ASSISTANT_RESPONSE_SUCCESS` only), `ModelSelector` (CHAT)  
3. **Rules** — evaluation prompt (description switches for transition vs default)  
4. **Action** — `HookActionEditor`

State is **split by action type** (L336–361):

- `selectedActionType: HookActionType` (default `ADD_CONVERSATION_TAG`)
- `addTagConfig: HookActionConfig.AddConversationTag`
- `transitionConfig: HookActionConfig.TransitionConversationTags` (placeholder `MISSING_TAG_ID` = zero UUID)
- `syncConfig: HookActionConfig.SyncMemoryTable`

`actionConfig` is derived via `when (selectedActionType)`. Switching chips keeps the other configs in memory (not discarded until save).

Save path (L388–404): builds `ConversationHook` with current `actionConfig`, calls `vm.upsertHook`, pops back.  
`upsertHook` bumps `configVersion` on update (existing + 1); new hooks get `configVersion = 1`.

#### `HookActionEditor` (L549–808)

Top-level action type chips: **`HookActionType.entries.forEach`** — all three types:

| Chip | Config UI |
|---|---|
| `ADD_CONVERSATION_TAG` | Multi-select `FilterChip` allowlist of catalog tags; remove-unavailable button |
| `TRANSITION_CONVERSATION_TAGS` | Read-only filter card (GitHub Issue completed); two `TransitionTagSelector`s (add/remove) |
| `SYNC_MEMORY_TABLE` | Target document Select, role chips, number fields, automatic switch |

Tag catalog states: Loading / Error (retry) / Success (empty → create first tag CTA) / Success with chips.

#### `TransitionTagSelector` (L811–843)

- Single-select chips among catalog tags.
- If `selectedId` not in catalog and not `MISSING_TAG_ID`: error + clear button.

#### Editor validation (`validateHookEditor`, L892–954)

| Action | Fail-closed checks |
|---|---|
| Add | catalog ready; `allowedTagIds` non-empty; all IDs ∈ available tags |
| Transition | catalog ready; add/remove ≠ missing; add ≠ remove; both ∈ available |
| Sync | target present/available/scope; roles; numeric limits |

Shared: name non-blank, chat model valid, trigger must be `AFTER_ASSISTANT_RESPONSE_SUCCESS`, prompt non-blank.

---

### 2. Models — ConversationHook & action configs

| File Path | Key symbols | Line anchors |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/model/ConversationHook.kt` | `ConversationHook`, `HookActionConfig`, `HookActionType`, `HookActionFilter`, `actionType`, `configurationHash`, statuses, `HookErrorCode`, `HookRuntimeRules` | L10–312 |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | `hooks: List<ConversationHook> = emptyList()` | L69 |

#### `ConversationHook` fields

```
id, name, enabled, trigger, modelId, prompt, actionConfig, configVersion
```

#### Current `HookActionType` enum values

```kotlin
enum class HookActionType {
    ADD_CONVERSATION_TAG,
    TRANSITION_CONVERSATION_TAGS,
    SYNC_MEMORY_TABLE,
}
```

#### Current `HookActionConfig` sealed subtypes

| SerialName | Class | Fields |
|---|---|---|
| `"add_conversation_tag"` | `AddConversationTag` | `allowedTagIds: Set<Uuid>` (default empty) |
| `"transition_conversation_tags"` | `TransitionConversationTags` | `addTagId`, `removeTagId`, `filter: HookActionFilter = GITHUB_ISSUE_COMPLETION` |
| `"sync_memory_table"` | `SyncMemoryTable` | targetDocumentId, targetScopeType, recentMessageCount, includeUser/Assistant, maxContextChars, maxOperations, minimumIntervalSeconds, automatic |

#### `HookActionFilter`

```kotlin
enum class HookActionFilter {
    GITHUB_ISSUE_COMPLETION,
}
```

Only used by transition config today.

#### `configurationHash()` material (L77–114)

- **Add**: sorted stringified `allowedTagIds` joined by `,`
- **Transition**: length-prefixed `addTagId|removeTagId|filter.name`
- **Sync**: all sync fields length-prefixed
- Outer hash also includes id, configVersion, name, enabled, trigger, modelId, prompt, **actionType.name**, actionMaterial

Changing action type or tag IDs changes hash → affects frozen execution / retry eligibility (memory table) and transition “config still current” checks.

#### Tag-related `HookErrorCode` values

`TAG_NOT_ALLOWED`, `TAG_NOT_FOUND`, `TAG_TRANSITION_CONFLICT`, `TAG_LIMIT_REACHED`, `GITHUB_ISSUE_EVIDENCE_NOT_FOUND`, plus shared `HOOK_DISABLED`, `SOURCE_MESSAGE_NOT_ACTIVE`, `SCHEMA_MISMATCH`, etc.

---

### 3. Serialization / deserialization / migration

| File Path | Role |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/utils/Json.kt` | `JsonInstant`: `ignoreUnknownKeys=true`, `encodeDefaults=true` (default kotlinx class discriminator `"type"`) |
| `app/src/test/java/me/rerere/rikkahub/data/model/ConversationHookTest.kt` | Round-trip + legacy golden JSON + configurationHash goldens |
| `app/src/androidTest/.../Migration_41_42_Test.kt` | DB execution rows store `action_type` string e.g. `"ADD_CONVERSATION_TAG"` |
| `app/src/main/java/me/rerere/rikkahub/data/db/entity/HookEntities.kt` | `HookExecutionEntity.actionType: String` (L161–162) |
| `app/src/main/java/me/rerere/rikkahub/data/repository/HookRepository.kt` | maps DB string via `HookActionType.valueOf(actionType)` (L421) |

#### Config persistence

- Hooks live **inside Assistant settings JSON** (`Assistant.hooks`), not a dedicated Room table.
- Polymorphic JSON uses kotlinx default discriminator key **`type`** + `@SerialName` values above.
- Documented legacy shape still decodes (test `literalLegacyAddTagJsonStillDecodes`):

```json
{"type":"add_conversation_tag","allowedTagIds":["…"]}
```

- Transition default `filter` survives round-trip even if omitted (default encode on).
- Unknown keys ignored at Assistant level; **unknown action `type` would fail** decode (no custom serializer / migration layer for action configs).

#### Execution audit persistence

- Runtime `actionType` stored as **enum name string** (`ADD_CONVERSATION_TAG`, etc.), not SerialName snake_case.
- History UI branches on `HookActionType` enum.
- Transition audit JSON embeds fixed `"action":"transition_conversation_tags"` string (`ConversationTagHookAudit.kt`).

#### Config versioning

- No schema migration function for action configs.
- `configVersion` increments on every `upsertHook` / enable toggle.
- Runtime uses `(configVersion, configurationHash)` for “config still matches” (transition + memory table retry).

---

### 4. Runtime execution — add vs transition (validation, allowlist, fail-closed)

#### Registration

`AppModule.kt` L49–69 registers three handlers in `HookActionRegistry`:

1. `AddConversationTagHookAction`
2. `TransitionConversationTagsHookAction`
3. `SyncMemoryTableHookAction`

#### Dispatch entry

`ChatService.dispatchFinalResponseHooks` (L884–946):

- After final assistant success snapshot
- Filters `assistant.hooks` where `enabled && trigger == AFTER_ASSISTANT_RESPONSE_SUCCESS`
- Creates run + executions with frozen `actionType` from config
- `HookDispatcher.dispatch` runs each execution sequentially (failures don’t stop later hooks)

#### Shared pipeline (`HookDispatcher.executeOne`)

1. Claim lease  
2. `handler.prepare` → Ready / Skipped / Cancelled  
3. Optional prepared audit write  
4. `modelExecutor.execute(prepared.request)`  
5. `handler.parse`  
6. `handler.execute`  
7. Finish status (or Terminalized if committer already wrote)

Timeout: `HookRuntimeRules.EXECUTION_TIMEOUT_SECONDS` (30s).

#### ADD path — allowlist & fail-closed

**File:** `AddConversationTagHookAction.kt`

| Stage | Behavior |
|---|---|
| prepare | Cast config; require source message still in `currentMessages`; resolve `allowedTagIds` → map of existing tags only (`mapNotNull` via `tagRepository.getTag`); freeze `allowedTags` for prompt |
| model prompt | `buildAddTagEvaluationPrompt`: injects allowlist; model must return `{decision, tagId, reason}` |
| parse | `HookOutputParser.parse` — exact keys `decision,tagId,reason`; apply requires UUID tagId; skip requires `tagId: null` |
| execute | SKIP decision → Skipped; null tagId → Skipped SCHEMA_MISMATCH; **`tagId !in context.allowedTagIds` → Skipped TAG_NOT_ALLOWED**; missing tag → TAG_NOT_FOUND; else `committer.commitAdd` |
| commit | Re-validate source active; re-check tag exists; `tagRepository.addTag`; SUCCESS if changed else SKIPPED (idempotent already-present); limits/errors mapped |

**Allowlist model (add):**

- Config: multi-tag `allowedTagIds` (user-selected frozen set).
- Runtime freezes resolved display names for prompt; **authorization uses original config set**, not only successfully resolved names.
- Model **chooses** which tagId to apply; runtime rejects out-of-allowlist (fail-closed SKIP, not crash).

**Note:** prepare does **not** re-check hook enabled / config hash (unlike transition). Disabled hooks are filtered out at dispatch time only.

#### TRANSITION path — fixed tags + evidence gate

**File:** `TransitionConversationTagsHookAction.kt` + `GitHubIssueEvidence.kt` + `ConversationTagHookCommitter.commitTransition`

| Stage | Behavior |
|---|---|
| prepare | Cast config; **re-check hook enabled + configVersion + configurationHash** live from settings; same add/remove → SKIP TAG_TRANSITION_CONFLICT; either tag missing → SKIP TAG_NOT_FOUND; **`detectGitHubIssueCompletionEvidence(snapshot)` null → SKIP GITHUB_ISSUE_EVIDENCE_NOT_FOUND**; freeze add/remove names + evidence |
| model prompt | `buildTagTransitionEvaluationPrompt`: tags fixed locally; model only apply/skip |
| parse | `TransitionConversationTagsHookOutputParser` — exact keys `decision,reason` only (**no tagId**); max response chars |
| execute | SKIP → Skipped; re-check config still current → else HOOK_DISABLED; `committer.commitTransition` |
| commit | remove then add in one transaction; operationCount 0 → SKIPPED else SUCCESS; audit summary/diff JSON; rollback mapping for limits/conflicts |

**Allowlist model (transition):**

- Not a multi-select allowlist. **Exactly two fixed tag IDs** in config.
- Model cannot pick tags; local evidence filter is hard gate before model call.
- Filter enum currently only `GITHUB_ISSUE_COMPLETION` (UI shows as fixed label, not chooser).

#### Model request types (`HookProviderExecutor.kt`)

```
FrozenHookModelRequest.AddConversationTag(modelId, prompt, messageTextSnapshot, allowedTags)
FrozenHookModelRequest.TransitionConversationTags(modelId, prompt, messageTextSnapshot, evidence, add/remove ids+names)
FrozenHookModelRequest.SyncMemoryTable(...)
```

Transition raw response is **not** `.trim()`’d (strict outer whitespace fails parse); add/sync trim.

#### Prepared / audit types (`HookActionRegistry.kt`)

- `PreparedHookAction.AddConversationTag` — carries `allowedTagIds`, no prepared audit
- `PreparedHookAction.TransitionConversationTags` — carries audit `ConversationTagTransition` (tagId=add, summary/diff JSON)
- Preview API on handler defaults null; only Sync implements `preview`

---

### 5. String resources (hook actions)

Primary: `app/src/main/res/values/strings.xml` (~L1766–2016). Localized variants exist (e.g. `values-zh-rTW`).

#### Action type labels

| Key | EN value |
|---|---|
| `assistant_hook_action_add_tag` | Add conversation tag |
| `assistant_hook_action_transition_tags` | Transition tags |
| `assistant_hook_action_sync_memory_table` | Sync memory table |

#### Add-tag editor

`assistant_hook_allowed_tags`, `assistant_hook_allowed_tags_description` (“frozen allowlist”), `assistant_hook_no_tags`, `assistant_hook_error_tag_required`, `assistant_hook_error_tag_unavailable`, `assistant_hook_remove_unavailable_tags`, `assistant_hook_prompt_description`

#### Transition editor / history

`assistant_hook_transition_*` (summary, filter, prompt description, add/remove labels “Completed” / “In-progress”, validation errors), `assistant_hook_unavailable_tag`, `assistant_hook_clear_unavailable_tag`, `assistant_hook_error_tag_catalog_unavailable`, `hook_transition_history_*`, `hook_transition_evidence_*`, `hook_transition_operation_*`, `hook_error_tag_transition_conflict`, `hook_error_tag_limit_reached`, `hook_error_github_issue_evidence_not_found`

#### History / status / errors (shared)

`hook_history_*`, `hook_status_*`, `hook_decision_*`, `hook_error_tag_not_allowed`, `hook_error_tag_not_found`, …

#### Sync Preview/Run/Retry (memory table only)

`hook_sync_preview`, `hook_sync_run_now`, `hook_sync_apply_preview`, `hook_sync_retry`, `hook_sync_preview_summary`, `hook_sync_history_*`, `hook_sync_action_*`, memory-table `hook_error_memory_table_*`, `hook_error_retry_not_allowed`, `hook_error_idempotent_replay`

---

### 6. History Preview / Run / Retry paths (break surface for tag refactor)

| Layer | Path | Tag actions today |
|---|---|---|
| UI | `ConversationMemoryTableDrawer.ConversationHookHistory` | Lists **all** hook runs; Preview/Run chips **only for `SYNC_MEMORY_TABLE` hooks** (L321–364); Retry **only** when `execution.actionType == SYNC_MEMORY_TABLE && FAILED` (L574–602) |
| UI | Transition branch (L604–671) | Displays audit summary/evidence/ops from `operationSummaryJson` / `diffSummaryJson`; **no** retry |
| UI | Add branch | Generic decision/tag/reason/error only (uses `execution.tagId`) |
| VM | `ChatVM.previewMemoryTableHook` / `runMemoryTableHookNow` / `retryMemoryTableHookExecution` / `applyMemoryTableHookPreview` | Names & types are memory-table-specific |
| Service | `ChatService.previewMemoryTableHookInternal` | `check(hook.actionConfig is SyncMemoryTable)` — hard fail for other types |
| Service | `retryMemoryTableHookExecution` | Requires `actionType == SYNC_MEMORY_TABLE` && FAILED && no committed cursor |
| Dispatcher | `HookDispatcher.preview` | Generic, but only Sync handler returns non-null preview |

**Historical DB rows** store `action_type` enum names. Renaming/removing `ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` without read migration breaks:

- `HookActionType.valueOf` in repository mapping
- Exhaustive `when` on `HookActionType` in UI (`hookActionLabelRes`, history branches, error mapping)
- Instrumented tests seeding those enum names
- Audit JSON `action: "transition_conversation_tags"` decode for history display (graceful: `runCatching`)

**configVersion / configurationHash:** any unified config shape change invalidates in-flight transition “config still current” checks and memory-table retry matching for hooks that were re-saved under new schema.

---

### What already exists for allowlist

1. **Config field** `HookActionConfig.AddConversationTag.allowedTagIds: Set<Uuid>`
2. **Editor multi-select chips** with intersection against live catalog; remove unavailable
3. **Save validation** requires non-empty allowlist and all IDs present in catalog
4. **Runtime freeze** of allowed tag id→name for model prompt
5. **Runtime fail-closed** `TAG_NOT_ALLOWED` if model returns id outside set
6. **configurationHash** includes sorted allowlist
7. **String copy** explicitly documents “frozen allowlist”
8. **Transition does not use allowlist** — uses two fixed IDs + local evidence filter instead

There is **no** shared “tag management action” type, **no** remove-only action, **no** multi-op tag plan from the model for add (single tag apply), and **no** allowlist on transition.

---

### Migration implications (ADD + TRANSITION → unified tag management)

Surfaces that encode the dual action model:

| Surface | Coupling |
|---|---|
| `HookActionConfig` sealed hierarchy + SerialNames | Settings JSON on disk / backups / import-export of assistants |
| `HookActionType` enum + `actionType` extension | DB execution rows, dispatcher registry, UI chips, labels |
| `HookActionRegistry` / three handler classes | DI wiring |
| `PreparedHookAction` / `FrozenHookModelRequest` / parsers | Distinct output schemas |
| `configurationHash` branches | Hash goldens in unit tests |
| Editor state triple + `when`s | UI structure |
| List summary only for transition | Presentation |
| History UI branches | Transition audit vs add tagId vs sync fields |
| Error codes & strings | Transition-specific vs allowlist-specific |
| Tests | `ConversationHookTest`, `AssistantHooksPageTest`, `ConversationTagHookCommitterTest`, `TransitionHookDispatcherTest`, `HookOutputParserTest`, migration tests |

Legacy JSON `"type":"add_conversation_tag"` is explicitly tested; any unification needs either:

- keep SerialName aliases / custom serializer, or  
- one-shot migrate Assistant.hooks when loading settings, and  
- map old execution `action_type` strings for history display (`valueOf` currently strict).

`SyncMemoryTable` is independent of tag unification but shares the same enum/editor chip row and exhaustive `when`s.

---

### Open technical risks

1. **Polymorphic settings JSON** — no intermediate migration layer; unknown `type` fails Assistant decode (hooks nested in settings).
2. **DB `action_type` is enum name** — renaming/removing enum constants breaks historical rows via `valueOf`.
3. **Two different model contracts** — add returns `tagId`; transition forbids tag IDs; unification must pick one schema or version the parser.
4. **Evidence gate only on transition** — GitHub issue detection is prepare-time hard skip; a unified action must decide whether evidence is optional strategy vs required filter.
5. **Allowlist vs fixed-pair semantics** — add freezes multi-allowlist; transition freezes exactly two IDs; config hash materials differ.
6. **Live config recheck asymmetry** — transition re-validates enabled/hash at prepare+execute; add does not (dispatch filter only).
7. **History audit formats** — transition depends on `ConversationTagTransitionAuditSummary` / operation list JSON; add stores single `tagId` column.
8. **Preview/Run/Retry are sync-only** — tag refactor likely should not assume those paths exist for tags; changing shared history UI `when`s can break compile exhaustiveness.
9. **Placeholder zero UUID** in transition editor (`MISSING_TAG_ID`) is editor-local; must not leak into saved config (blocked by validation).
10. **Golden configurationHash tests** hardcode hashes for add and transition shapes — any field merge changes them.
11. **i18n surface area** — many transition-specific strings; action chip labels used in list + editor + tests via `hookActionLabelRes`.
12. **Export** — `ExportHooks.kt` is generic file export infrastructure (name coincidence); assistant hooks export via normal settings/assistant serialization, not a separate hook export schema.

## Caveats / Not Found

- Task PRD (`.trellis/tasks/07-18-hook-editor-tag-management/prd.md`) is still TBD; this research is from code, not a finalized #150 design doc in-repo.
- No GitHub issue #150 body was fetched in this pass; requirements for “unified tag management” are inferred from task title only.
- Spec directory under `.trellis/spec/` was not found to contain hook-specific guidelines in this search.
- Full `SyncMemoryTableHookAction` implementation details omitted beyond shared surfaces (out of tag-unification core, except shared enum/editor/history).
