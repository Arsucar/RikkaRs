# Design: Semantic Memory (#195)

## Goal

Third memory mode: AI auto-summarize → online embedding → hybrid recall inject, coexisting with normal memory + memory tables.

## Architecture

```
ChatService.prepareGenerationRequest
  └── SemanticMemoryTransformer (previewPolicy=SideEffectFree)
        └── RecallService.recall → <semantic_memories> / {{semantic_memories}}

ChatService onSuccess
  └── SemanticMemoryManager.checkAndSummarize (background)

SemanticMemoryManager
  ├── MemorySummarizer (AI extract + cos>0.85 merge)
  ├── EmbeddingService (OpenAI-compatible /embeddings)
  ├── SemanticMemoryRepository (Room)
  └── import/export gzip JSON + eviction candidates
```

## Data (AppDatabase v47 → v48)

### `episodic_memory`
id PK auto, assistant_id, content, summary, importance(2-5), is_core, embedding(JSON), embedding_model,
created_at, updated_at, last_recalled_at, recall_count, source_conversation_id, source_message_index

### `semantic_memory_state`
assistant_id PK, last_summarized_message_count, last_summarized_at

Migration_47_48: CREATE TABLE + indices on (assistant_id), (assistant_id, is_core).

## Config

`SemanticMemoryConfig` JSON key `semantic_memory_config` in DataStore.
`Assistant.enableSemanticMemory: Boolean = false` (assistant-level gate; global `config.enabled` is master).

## Runtime gates

- Inject/recall when `config.enabled && assistant.enableSemanticMemory`
- Auto-summarize when `config.enabled && config.autoSummarizeEnabled` (does not require assistant flag if manager called only for enabled assistants — call site checks assistant flag too)
- Independent of `enableMemory` / memory table capabilities

## Stats

`SemanticMemoryStats` as mutable StateFlow snapshot for reactive UI (AC14).

## Non-goals MVP

- sqlite-vector / offline embedding
- Hook-based summarize trigger
