# Research: Subagent invocation & context

## Host
- `SubagentHost.kt` — `buildChildAssistant` L679-730 copies parent with profile overrides; `enableMemory = profile.enableMemory`.
- `runToCompletion` L583+ calls `generationHandler.generateText(... memories = emptyList())` with **no** `inputTransformers` today (default empty).
- GenerationHandler supports `inputTransformers` parameter (`GenerationHandler.kt` ~L136-140).

## Main-agent injection pattern (reuse)
- `ChatService.kt` L1575-1627: resolve templates/documents → `MemoryTableInjectionTransformer`.
- Transformer: `MemoryTableInjectionTransformer.kt` — `buildMemoryTablePrompt`, macro `{{memory_tables}}`.
