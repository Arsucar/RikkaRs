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
