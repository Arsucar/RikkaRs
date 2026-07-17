# Issue #144：Room/协程 OOM 技术设计

## Boundary

本任务处理当前 HEAD 中已经能证明的不必要内存峰值和诊断缺口，不把“最后在 Room continuation 分配 24 bytes 失败”当作增长源。主要实现边界是 `ConversationRepository`、`MessageNodeDAO` 和 `recent_chats`；其他事务路径只做回归与风险记录。

## Data Flow Changes

### Recent conversation summaries

当前：`ConversationTools.recent_chats` → `getRecentConversations` → 对每个会话 `loadMessageNodes` → 全量 JSON 反序列化 → 最终只读取 title/date。

目标：`ConversationTools.recent_chats` → 轻量 conversation rows → `Conversation` summary（`messageNodes = emptyList()`）。返回 JSON 合同不变，不读取 `message_node`。

### Conversation node sync

当前：事务内 `SELECT *` 全量历史节点 → 仅提取 ID → 计算 diff → 逐项编码写入。

目标：DAO 提供仅返回 `id` 的有序投影；Repository 用 ID 列表计算删除项，逐项 upsert 新状态。授权、事务原子性和节点顺序不变。

### Initial/fork save batching

当前：把全部 `MessageNode` 编码为完整实体列表，再一次交给 Room。

目标：按固定批次（默认 32 或 64，实施时以测试和现有页大小统一）编码、插入；完成一批后不保留该批实体引用。原始 `Conversation.messageNodes` 仍由调用方持有，但额外编码字符串峰值受单批上限约束。

### Full conversation reads and diagnostics

完整会话模型当前要求最终持有全部节点，因此不通过总量截断改变语义。保留分页查询与顺序，并记录元数据级统计：operation/source、conversationId、pageCount、nodeCount、encodedChars、elapsedMs、heapUsed/max。日志只在大规模阈值或失败路径产生，避免高频日志本身放大内存。

索引重建继续逐会话加载、索引并释放局部引用。若 `MessageFtsManager` 被发现保留完整 Conversation，检查阶段必须阻断交付。

## Error and Compatibility Contract

- 既有 `SQLiteBlobTooBigException` / 反序列化失败处理行为不得被静默扩大；失败页和跳过行为需要保留或以更明确错误替代。
- 不增加 Room 版本，不改变实体字段。
- 不记录消息正文、JSON payload、附件路径或模型输出。
- `recent_chats` 的 JSON 字段、limit 1..30 和排序保持兼容。
- 无复现证据时，提交和 Issue 评论使用“降低已识别峰值/增强诊断”，不使用“已修复原 OOM 根因”。

## Validation Design

- 纯 JVM 测试覆盖批次拆分和 sync diff；大列表验证每批不超过上限且顺序不变。
- Repository/DAO 测试覆盖 ID-only projection、完整 load 顺序和无效摘要加载路径；若现有测试架构无法直接 mock Repository，则为摘要映射和批次策略提取可测纯函数，并用 instrumentation 验证 DAO SQL。
- 聚焦运行 ConversationRepository、ConversationTools、索引相关测试；之后运行 app 编译和设备安装。
- 设备上若可构造大会话，记录节点规模与 heap 趋势；不能稳定复现时不声称原事故消失。

## Rollback

摘要读取、ID 投影、批量写入和诊断应以独立小提交组织。任何批次写入回归可回退到单项 upsert，而无需 schema rollback。
