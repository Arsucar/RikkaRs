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

### 3. Contracts

- The assistant memory page lists `MemoryTableDocument` items, not unused
  templates. Resolve template metadata only for display.
- A card opens its document as a whole-card action; card deletion deletes only
  that document.
- The add dialog may choose an effective template or create a private/global
  template. Navigation occurs only after all required Room writes succeed.
- Persistence errors keep the dialog open and show localized UI copy; raw
  exception messages are not exposed.

### 4. Validation & Error Matrix

- Effective template missing/invalid -> document creation fails; no navigation.
- New template persistence fails -> document write is not attempted.
- Document persistence fails -> dialog remains open; no navigation.
- Empty template name -> creation action disabled.

### 5. Good/Base/Bad Cases

- Good: persist template, await success, persist document, then navigate.
- Base: use an existing effective template and persist only the document.
- Bad: start independent `viewModelScope.launch` writes and navigate immediately.
- Bad: infer template deletion when a displayed document is absent.

### 6. Tests Required

- Assert template persistence completes before document persistence.
- Assert template failure prevents the document callback.
- Assert document failure produces no success/navigation event.
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
