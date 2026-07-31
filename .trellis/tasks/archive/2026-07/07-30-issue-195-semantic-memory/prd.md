# 语义记忆系统

GitHub issue: #195

## Goal

在普通记忆与记忆表之外，新增语义记忆：AI 自动总结提取 + 在线 embedding 向量召回 + 核心记忆双轨 + 浏览器/导入导出。

## Requirements（摘要，细节以 Issue 正文为准）

- Data：`EpisodicMemoryEntity` + `SemanticMemoryStateEntity`，DB v45→v46 AutoMigration
- Service：EmbeddingService / RecallService / MemorySummarizer / SemanticMemoryManager；Stats 用 StateFlow
- Transformer：`SemanticMemoryTransformer`（`previewPolicy = SideEffectFree`），`{{semantic_memories}}` 注入
- UI：设置页、记忆浏览器、助手记忆页第三栏；改即存；测试连接；手动总结/召回测试/淘汰确认
- `Assistant.enableSemanticMemory`；与普通记忆/记忆表并存
- 纯在线 embedding，不引入离线模型；第一版不做 sqlite-vector

## Acceptance Criteria

- [ ] AC1–AC14 对齐 Issue #195

## Non-Goals

- 不替换现有普通记忆/记忆表
- 第一版不 Hook 化总结触发

## Complexity

Very complex：必须 `design.md` + `implement.md`，建议分阶段（Data → Service → Transformer → UI）。
