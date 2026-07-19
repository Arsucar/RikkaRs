# Research: Memory table injection pipeline

## Models
- `MemoryTableTemplate.id` stable string UUID.
- `MemoryTableDocument.templateId` links payload to template.
- Scope: GLOBAL / ASSISTANT / CONVERSATION via `isEffectiveFor`.

## Repository
- `MemoryTableRepository.getEffectiveTemplates(assistantId)`
- `getEffectiveDocuments(assistantId, conversationId)`

## Budget
- Settings: `memoryTableMaxInjectDocuments/Tokens/Chars`
- Defaults in `MemoryTableInjectionBudget.kt` / model defaults used by transformer.

## Tools vs inject
- Tools: `MemoryTableTools.kt` when generation plan enables tools.
- Inject: transformer only — preferred for subagent read-only.
