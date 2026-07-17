# Memory Capability Contract

## 1. Scope / Trigger

Use this contract whenever code reads or changes `Assistant.enableMemory`, `Settings.enableMemoryTable`, `Assistant.enableMemoryTable`, ordinary memory prompts/tools, or memory-table repositories/transformers/tools.

The two capabilities are independent. Closing ordinary memory must never disable structured memory tables, and closing memory tables must never disable ordinary memory.

## 2. Signatures

```kotlin
data class MemoryCapabilities(
    val normalMemoryEnabled: Boolean,
    val memoryTableEnabled: Boolean,
)

fun resolveMemoryCapabilities(
    normalMemoryEnabled: Boolean,
    settingsMemoryTableEnabled: Boolean,
    assistantMemoryTableEnabled: Boolean,
): MemoryCapabilities
```

Persistent inputs remain:

- `Assistant.enableMemory: Boolean`
- `Settings.enableMemoryTable: Boolean`
- `Assistant.enableMemoryTable: Boolean`
- `Settings.memoryTableAutoSyncEnabled: Boolean`

Do not rename, merge, or derive one stored field from another during normal updates or backup restore.

## 3. Contracts

- `normalMemoryEnabled = Assistant.enableMemory`.
- `memoryTableEnabled = Settings.enableMemoryTable && Assistant.enableMemoryTable`.
- `ChatService.prepareGenerationRequest` is the shared Preview/real-generation boundary and must use the same resolved capability instance for data loading, transformers, and tools.
- Ordinary capability controls MemoryRepository reads, normal memory prompt injection, and `memory_tool` only.
- Table capability controls MemoryTableRepository reads, `MemoryTableInjectionTransformer`, and `memory_table_tool` only.
- UI Switches display stored preferences. A global table gate may disable the assistant table control while leaving its checked preference intact.
- Settings persistence must read and write `memoryTableAutoSyncEnabled`; never hard-code `false` while serializing an unrelated setting change.

## 4. Validation & Error Matrix

- Normal OFF / Table ON -> no ordinary memory read, prompt, or tool; table read/injection/tool enabled.
- Normal ON / Table OFF -> ordinary memory enabled; no table read/injection/tool.
- Global table OFF / Assistant table ON -> stored assistant preference remains true, effective table capability false, UI explains the global gate.
- Both ON -> each prompt/tool appears through its own path exactly once.
- Both OFF -> neither repository path is loaded for generation and neither tool is exposed.
- Persistence failure -> UI continues rendering the authoritative Settings/Assistant flow; do not maintain a separate optimistic toggle copy.

## 5. Good / Base / Bad Cases

- Good: resolve once in `prepareGenerationRequest`, load ordinary memories only when `normalMemoryEnabled`, and pass table capability to table loaders/tools.
- Base: keep `shouldEnableMemoryTable(settings, assistant)` as a compatibility helper only when it delegates to the central rule.
- Bad: `if (assistant.enableMemory) { loadMemoryTable(); add(memory_table_tool) }`.
- Bad: changing `enableMemoryTable` also writes `enableMemory`, assistant table preference, or auto-sync preference.

## 6. Tests Required

- Pure Kotlin parameterized tests for OFF/OFF, ON/OFF, OFF/ON, ON/ON.
- Global OFF / assistant table ON assertion: preference checked, effective capability false, control disabled.
- Generation preparation regression: normal OFF produces an empty ordinary-memory list while table transformer/tools remain enabled when their capability is ON.
- Existing MemoryTableRepository, MemoryTableTools, MemoryTableInjectionTransformer, and Settings JSON/default tests remain green.
- Run `:app:processDebugResources`, `:app:compileDebugKotlin`, focused JVM tests, lint audit, and app install acceptance with `--no-daemon`.

## 7. Wrong vs Correct

### Wrong

```kotlin
if (assistant.enableMemory) {
    memories = memoryRepository.getEffectiveMemories(assistant.id.toString())
    tools += buildMemoryTableToolsIfEnabled(assistant.enableMemoryTable)
}
```

### Correct

```kotlin
val capabilities = resolveMemoryCapabilities(
    normalMemoryEnabled = assistant.enableMemory,
    settingsMemoryTableEnabled = settings.enableMemoryTable,
    assistantMemoryTableEnabled = assistant.enableMemoryTable,
)
val memories = if (capabilities.normalMemoryEnabled) {
    memoryRepository.getEffectiveMemories(assistant.id.toString())
} else {
    emptyList()
}
tools += buildMemoryTableToolsIfEnabled(capabilities.memoryTableEnabled)
```

## Scenario: Assistant memory-table document list and creation

### 1. Scope / Trigger

Use this contract when changing the assistant memory page's table list, add
dialog, template persistence, document persistence, or editor navigation.

### 2. Signatures

- `createMemoryTableDocument(template, onDone)` creates an assistant-scoped
  document from an already-persisted effective template.
- `createMemoryTableTemplateAndDocument(template, scopeType, onDone)` persists
  a private/global template first, then creates the assistant-scoped document.
- `normalizeMemoryTableTemplateName(name)` applies trim-equivalent whitespace
  handling, Unicode NFC, whitespace collapsing, and `Locale.ROOT` lowercase.
- `upsertMemoryTableTemplate(template, onDone)` and
  `deleteMemoryTableTemplate(template, onDone)` return `Result` callbacks so
  management UI can retain form/sheet state on failure.

### 3. Contracts

- The assistant memory page lists `MemoryTableDocument` items, not unused
  templates. Resolve template metadata only for display.
- A card opens its document as a whole-card action; card deletion deletes only
  that document.
- Derive one shared UI projection for the document list and template picker.
  For each effective template, the primary document is the first current-
  assistant document, falling back to the first global document.
- The add sheet may choose an effective template or create a private/global
  template. A template whose primary document already exists is marked added
  and opens that document without another persistence write.
- New-template scope selection is explicit and limited to `ASSISTANT` or
  `GLOBAL`; trim the required name before persistence. The created document
  remains assistant-scoped regardless of template scope.
- Navigation occurs only after all required Room writes succeed.
- Persistence errors keep the dialog open and show localized UI copy; raw
  exception messages are not exposed.
- Destructive document actions live outside the whole-card click target and
  confirmation identifies the table by display name, never by raw payload JSON.
- Normalized template names are unique in every effective namespace:
  assistant-private create/update checks global + the same assistant; global
  create/update checks every template so it cannot conflict for another assistant.
- Repository conflict checks are authoritative. UI pre-checks provide immediate
  feedback but must still map `MemoryTableTemplateNameConflictException` after a race.
- Legacy duplicate IDs are preserved. Only the picker groups by normalized name;
  document projection and template management continue using the complete list.
- Picker winner order is deterministic: has primary document, current-assistant
  scope, global scope, newer update time, then stable ID.
- Template management lists every effective template ID and permits name/
  description editing with read-only scope. Deletion is ID-scoped and warns that
  documents are cascaded; global deletion additionally warns that all assistants
  are affected.

### 4. Validation & Error Matrix

- Effective template missing/invalid -> document creation fails; no navigation.
- Template has a primary document -> open its persisted document ID; perform no
  template/document create write.
- Template has no primary document -> persist one assistant-scoped document,
  then navigate with its non-null ID.
- New template persistence fails -> document write is not attempted.
- Document persistence fails -> dialog remains open; no navigation.
- Empty template name -> creation action disabled.
- Normalized name conflicts -> creation/save disabled by UI when visible and
  rejected by Repository otherwise; form remains open with localized conflict copy.
- Multiple historical documents for one template -> keep every visible document
  in the main list; use only the derived primary document for the picker state.
- Multiple historical templates with one normalized name -> one picker row but
  every ID remains visible in management for explicit rename/delete.
- Template delete failure -> keep the management sheet open and show localized
  error; never delete other same-name template IDs.

### 5. Good/Base/Bad Cases

- Good: persist template, await success, persist document, then navigate.
- Good: derive `primaryDocumentsByTemplate` once and reuse it for both the
  document list and the picker's added/open-existing behavior.
- Good: enforce name conflicts in Repository and use a separate picker-only
  deduplication projection for legacy duplicates.
- Base: use an existing effective template and persist only the document.
- Base: an already-added template opens the primary document without writing.
- Bad: start independent `viewModelScope.launch` writes and navigate immediately.
- Bad: render every template click as "new" and create another UUID document
  even when the picker already has a primary document.
- Bad: infer template deletion when a displayed document is absent.
- Bad: `distinctBy(name)` on the full effective template list, which hides
  documents and prevents management of historical IDs.
- Bad: UI-only duplicate checking; AI/tool/race paths can bypass it.

### 6. Tests Required

- Assert template persistence completes before document persistence.
- Assert template failure prevents the document callback.
- Assert document failure produces no success/navigation event.
- Assert multi-template primary mapping uses assistant-first/global-fallback,
  preserves null entries for unused templates, and excludes invisible templates.
- Assert normalized duplicate-name rules for assistant/global create and update,
  including self-exclusion and same-name private templates owned by other assistants.
- Assert picker deduplication winner priority without dropping documents or
  management entries, and ID-scoped deletion leaves same-name templates intact.
- Run resource processing, Kotlin compile, focused JVM tests, and translation
  coverage checks for every configured locale.

### 7. Wrong vs Correct

#### Wrong

```kotlin
viewModelScope.launch { repository.upsertTemplate(template) }
viewModelScope.launch { repository.upsertDocument(document) }
navController.navigate(editor)
```

#### Correct

```kotlin
viewModelScope.launch {
    runCatching {
        val savedTemplate = repository.upsertTemplate(template)
        repository.upsertDocument(document.copy(templateId = savedTemplate.id))
    }.onSuccess { savedDocument -> navigate(savedDocument) }
}
```

#### Wrong picker behavior

```kotlin
onTemplateClick = { template ->
    createMemoryTableDocument(template)
}
```

#### Correct picker behavior

```kotlin
onTemplateClick = { template ->
    val existing = selection.primaryDocumentsByTemplate[template.id]
    if (existing != null) {
        navigate(existing)
    } else {
        createMemoryTableDocument(template)
    }
}
```

## Scenario: Memory-table template scope migration

### 1. Scope / Trigger

Use this contract when creating, editing, copying, listing, or authorizing a
`MemoryTableTemplate` through Repository, `memory_table_tool`, or the assistant
memory UI.

### 2. Signatures

```kotlin
suspend fun upsertTemplate(
    template: MemoryTableTemplate,
    actorAssistantId: String,
    requestedScopeType: MemoryTableScopeType? = null,
): MemoryTableTemplate

suspend fun copyGlobalTemplateToAssistant(
    templateId: String,
    actorAssistantId: String,
    copyName: String,
): MemoryTableTemplate
```

The guarded DAO update writes ordinary fields and ownership in one statement:

```kotlin
suspend fun updateEffectiveTemplateFields(
    id: String,
    assistantId: String,
    name: String,
    description: String,
    schemaJson: String,
    scopeType: String,
    scopeId: String,
    updatedAt: Long,
): Int
```

### 3. Contracts

- Repository is the only owner of `scopeId` derivation. UI and tools pass the
  requested scope type and actor identity; they never construct another
  assistant's owner ID.
- Create + null target defaults to current-assistant `ASSISTANT`; update + null
  preserves the stored scope. Explicit `ASSISTANT` / `GLOBAL` performs an
  in-place migration with the same template ID.
- Template actions reject `CONVERSATION`. `GLOBAL` always stores
  `__global__`; `ASSISTANT` always stores the current actor assistant ID.
- The guarded update authorizes against the row's old owner, then writes name,
  description, schema, scope, and timestamp atomically.
- Migration never changes document rows, document scope, payload, revision, or
  template references. Standard reads become invisible to other assistants
  because effective template IDs change, not because documents are rewritten.
- Target-namespace normalized-name checks are authoritative in Repository and
  exclude the same template ID. Scope changes must re-run them even if the name
  did not change.
- Copy is not migration: copy creates a new assistant-owned ID and preserves the
  source GLOBAL row. UI must offer an editable copy name so a prior copy does
  not create a dead-end conflict.

### 4. Validation & Error Matrix

- Foreign/malformed/missing known ID -> reject without changing any row.
- `requestedScopeType = CONVERSATION` -> reject before DAO update.
- Update target omitted -> keep stored owner even if the draft model carries a
  different scope.
- Target normalized name conflicts ->
  `MemoryTableTemplateNameConflictException`; keep edit/copy UI open.
- Scope equals stored scope -> idempotent ordinary update; no migration prompt.
- GLOBAL -> ASSISTANT -> other assistants no longer list the template; linked
  document rows remain byte-for-byte unchanged.
- ASSISTANT -> GLOBAL -> all assistants list the template after Room refresh.

### 5. Good / Base / Bad Cases

- Good: tool parses optional `scope`, preserves null-vs-explicit presence, and
  passes both draft + nullable target to the actor-scoped Repository closure.
- Good: Compose confirms a real scope change, disables dismiss/submit while the
  write is active, and reports success/error after Room completes.
- Base: edit name/description with null target; ownership stays unchanged.
- Bad: tool or UI writes `scopeId = assistantId` itself.
- Bad: infer migration from `template.scopeType` on update; this loses the
  difference between omitted scope and explicit scope.
- Bad: copy the same GLOBAL name directly and leave the user no way to resolve
  the target namespace conflict.

### 6. Tests Required

- DAO/instrumented: GLOBAL→A update, A→GLOBAL update, foreign actor returns 0,
  ordinary fields and scope change together, linked documents unchanged.
- Repository: create null defaults ASSISTANT; update null preserves; explicit
  both directions; same-scope idempotence; conversation/foreign/malformed
  rejection; target-name conflicts; document scope/reference unchanged.
- Tool: create/update `assistant|global`, omitted update target, and conversation
  rejection for both actions.
- UI pure/state: scope-change confirmation only when different; target-scope
  conflict checking; copy and migrate are distinct; loading prevents repeat.
- Resource/Kotlin compile and existing #122/#140 memory-table regressions remain
  green; run app installation when a device is available.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val target = params["scope"] ?: "assistant"
draft.copy(scopeType = target, scopeId = assistantId)
repository.upsertTemplate(draft, assistantId)
```

#### Correct

```kotlin
val requestedScopeType = params["scope"]?.toTemplateScopeOrNull()
repository.upsertTemplate(
    template = draft,
    actorAssistantId = assistantId,
    requestedScopeType = requestedScopeType,
)
```
