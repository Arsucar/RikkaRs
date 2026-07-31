# Implement plan: #195 Semantic Memory

## Order

1. design.md (done)
2. Entities + DAO + Migration_47_48 + AppDatabase v48 + DI dao
3. SemanticMemoryConfig + StateFlow stats + DataStore + Assistant field
4. Repository + Embedding + Recall + Summarizer + Manager
5. SemanticMemoryTransformer + ChatService wire
6. SemanticMemoryVM + Setting/Browser pages + routes + SettingPage entry
7. AssistantMemoryPage third section
8. strings en+zh
9. Unit tests: cosine / score / JSON extract / slim merge helpers

## Validation (implement agent)

- Focused JVM unit tests only
- No full gradle / no install

## Rollback

- Drop Migration_47_48, version 47, remove semantic packages + wiring
