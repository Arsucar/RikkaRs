# Implementation Plan

## Checklist

- [x] Inspect current memory schema and DAO/repository APIs.
- [x] Plan #39 migration and UI behavior.
- [x] Keep #39 as the next concrete implementation target after this roadmap task.
- [x] Split #41 into P0/P1/P2/P3 roadmap phases.
- [x] Require #41 P0 gates and zero-intrusion checks before table mutation/UI expansion.

## Validation

- Planning-only validation:
  - inspected `MemoryEntity`, `MemoryDAO`, `MemoryRepository`, `GenerationHandler`, `ChatService`, and assistant memory UI references.
  - confirmed current storage is flat `memoryentity(id, assistant_id, content)` with global memory represented by `assistant_id == "__global__"`.
  - confirmed current generation/tool routing is still controlled by `Assistant.useGlobalMemory`.
- Future #39 validation:
  - `./gradlew test`
  - `./gradlew :app:compileDebugKotlin`
  - manual memory checks:
  - assistant-local memory,
  - global memory,
  - mixed injection,
  - memory table disabled path.
