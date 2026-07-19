# Design: Subagent memory table injection (#164)

## Data model

```kotlin
// SubagentProfile
val injectedMemoryTableDocumentIds: Set<String> = emptySet()
```

- Stable id = `MemoryTableDocument.id` (string UUID) — **document instances**, not templates.
- Default empty → no injection; kotlinx missing field → default.
- Backup/restore inherits via assistant `subagentProfiles` JSON.
- Rename from WIP `injectedMemoryTableTemplateIds` (unreleased); no migration needed.

## Resolution at spawn

Pure helper (testable):

```kotlin
fun resolveSubagentMemoryTableInjection(
  selectedDocumentIds: Set<String>,
  templates: List<MemoryTableTemplate>,
  documents: List<MemoryTableDocument>,
  parentMemoryTableEnabled: Boolean,
  memoryTableIsolation: Boolean = false,
): Pair<List<MemoryTableTemplate>, List<MemoryTableDocument>>
```

Rules:
1. If selected empty or parent gate off → empty.
2. Keep only documents whose id ∈ selected and present in the effective documents list already scoped for parent assistant (+ conversationId when provided by host).
3. When `memoryTableIsolation == true`, keep only documents with `scopeType == CONVERSATION` (mirror `ChatService.kt` isolation).
4. Keep only templates whose id is referenced by the resolved documents (for schema in `buildMemoryTablePrompt`).
5. Stable order: documents sorted by templateId then id; templates sorted by id.
6. Skip missing / inaccessible ids silently; empty after filter → no transformer.

## Isolation source

Loader lambda signature:

```kotlin
suspend (
  parentAssistant: Assistant,
  conversationId: Uuid?,
  selectedDocumentIds: Set<String>,
  settings: Settings,
) -> List<InputMessageTransformer>
```

Inside DI:
1. Load `memoryTableIsolation` via `ConversationRepository.getConversationById` when `conversationId != null` (default false if missing).
2. `getEffectiveDocuments(assistantId, conversationId?.toString())`.
3. Resolve + build `MemoryTableInjectionTransformer` only when both templates and documents non-empty after resolve.

## Injection point

`SubagentHost` first `runToCompletion` for a new spawn only (`!reusedContext` && selected ids non-empty):

```kotlin
MemoryTableInjectionTransformer(
  templates = templates,
  documents = documents,
  maxDocuments = settings.memoryTableMaxInjectDocuments,
  maxTokens = settings.memoryTableMaxInjectTokens,
  maxChars = settings.memoryTableMaxInjectChars,
)
```

Continuation / summary reuse message history → do not re-inject.

Child assistant does **not** enable memory table tools; injection is transformer-only (read-only).

## UI

`AssistantSubagentProfilePage` near `enableMemory`:
- Multi-select chips of **assistant-visible document instances** from `vm.memoryTableDocuments` (not templates).
- Chip label: template name (lookup by `document.templateId`) + optional short scope hint; blank template → document id prefix.
- Show selected count; missing selected document ids as invalid chips removable.
- Empty list when no documents.

## Permissions

- No write tools granted.
- Never load documents outside parent assistant effective scope.
- Isolation rules match ChatService.

## Risks

- Ordinary memory still gated by `enableMemory` only.
- Token budget: reuse settings.
- Templates still needed at inject time for schema; loaded via `getEffectiveTemplates` and filtered to those referenced by selected documents.
