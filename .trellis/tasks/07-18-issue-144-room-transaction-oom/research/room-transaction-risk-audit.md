# #144 Room/transaction 静态风险审计

## 证据边界

- 当前 Issue 只有一次 R8 后的 512 MiB Java heap OOM 栈，没有触发入口、版本、设备、数据规模、logcat、heap dump 或 allocation trace。
- 下列路径是从当前代码可确认的潜在大对象/大量小对象峰值，不是 #144 的已证实根因。
- 本任务只修正会话持久化路径中已经能证明的无效全量读取和额外序列化峰值；其他产品容量策略没有证据时不做猜测式限流。

## MemoryTable / 结构化记忆

### 有效文档读取先于注入预算

- `MemoryTableDAO.getEffectiveDocuments` / Flow 使用 `SELECT *` 读取 GLOBAL、当前 Assistant 与当前 Conversation 的全部文档，只有排序，没有 `LIMIT`、分页或轻量投影（`app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryTableDAO.kt:132`）。实体包含完整 `payloadJson`。
- Repository 在 DAO 返回后才过滤并映射完整模型（`app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt:87`）。
- 注入侧的 `documents.take(limit)` 在完整列表已经进入内存后才执行（`app/src/main/java/me/rerere/rikkahub/data/ai/transformers/MemoryTableInjectionTransformer.kt:102`）。
- 风险条件：大量文档或单个超大 payload 时，Room materialization、JSON 解析树和渲染字符串峰值不受 prompt 注入预算保护。
- 现有缓解：查询范围不跨全部 Conversation；后续有文档/token/字符预算。

### 单文档更新/rollback 的旧新 payload 并存

- `MemoryTableRepository.upsertDocument` 在事务中规范化、解析新 payload，读取旧完整实体，并把旧 payload 复制到 snapshot 后写入新实体（`app/src/main/java/me/rerere/rikkahub/data/repository/MemoryTableRepository.kt:222`）。
- rollback 同时读取当前文档和目标 snapshot，再走更新/快照路径（同文件 `:301`）。
- 风险条件：单个超大 payload 更新时，调用方字符串、规范化字符串、解析树、旧实体、snapshot 和新实体可能同时存活。
- 现有缓解：单次只处理一个文档；正常写入会把每文档 snapshot prune 到 20。

### 管理型全量入口

- `getDocuments()` 是全表 `SELECT *`；export 同时加载全部 templates/documents 并编码成一个 JSON 字符串（`MemoryTableDAO.kt:129`，`MemoryTableRepository.kt:348`）。
- import、scope copy、relink 也可能同时持有多份完整 payload。这些是显式管理入口，现有证据没有指向它们。

## Hook history / run

- `HookDAO.observeHistory` 是 `@Transaction` 的无上限 `SELECT * FROM hook_runs WHERE conversation_id = ...`，并通过 Room relation 载入对应 executions（`app/src/main/java/me/rerere/rikkahub/data/db/dao/HookDAO.kt:363`）。Repository/ChatVM 会保留映射后的当前会话历史列表。
- 风险条件：清理未运行、历史遗留或异常写入时，一个会话的全部 runs/executions 会一次性物化。
- 现有缓解：reason/error 均截到 500 code points；正常 cleanup 保留 30 天、每会话最多 100 runs（`app/src/main/java/me/rerere/rikkahub/data/model/ConversationHook.kt:212`，`HookRepository.kt:277`）。这里没有会话消息 JSON 大字段。
- 启动恢复的 active runs 查询仅投影 `run_id` 和状态；异常数量会增加事务时长和小对象数，但优先级较低。

## Conversation tags

- `ConversationTagDAO.observeRelations()` 无上限读取全表 cross-ref，Repository 再映射，ChatDrawerVM 再按 conversation 分组（`app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationTagDAO.kt:18`，`ConversationTagRepository.kt:24`）。
- 风险条件：会话关系很多时会同时产生实体列表、模型列表和 group map。
- 现有缓解：cross-ref 只有两个 ID，不读取 Conversation 或消息 JSON；全局 tag 最多 100，单会话最多 20。merge/copy 使用 SQLite 内 `INSERT ... SELECT`，不先把完整关系搬到 Kotlin。

## Subagent run / context

### 冷恢复先全量读取和解码，再裁到内存上限

- `SubagentContextDAO.getRestorable` 对全部未过期 context 使用 `SELECT *`，没有 LIMIT、分页、conversation/profile 过滤或轻量投影（`app/src/main/java/me/rerere/rikkahub/data/db/dao/SubagentContextDAO.kt:12`）。实体包含完整 `scope_json`、`messages_json`、`usage_json`。
- `RoomSubagentContextStore.loadRestorable` 先取得全部实体，再逐个解码完整消息树（`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentContextStore.kt:21`）。Cache 完成恢复后才执行 `evictToLimitLocked()`（`SubagentContextCache.kt:363`）。
- 风险条件：一小时 TTL 内产生超过内存上限的 context，冷启动仍会先物化并解码全部；单行消息历史没有存储字符硬上限。
- 现有缓解：恢复前删除已过期且非 RUNNING 行；默认 TTL 1 小时，恢复后 LRU 目标 16。

### 流式进度反复复制并异步持久化完整消息树

- 每个 subagent message chunk 都调用 `updateProgress`；Cache 对完整 messages 做 snapshot，并由 `persistAsync` 启动 IO coroutine（`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentContextCache.kt:195`、`:414`）。Store 随后把完整消息树编码成一个 `messages_json` 字符串。
- 风险条件：长 run、大 tool output 或 Room 写入落后于生成速度时，多个 revision 的消息树 snapshot、JSON 字符串和 bind 参数可能短时并存。reuse 还会持续追加历史，没有持久化前 trimming。
- 现有缓解：revision guard 防止旧写覆盖新写；默认并发子代理为 3；tool-call 数有默认预算，但单次输出和总消息字符没有存储硬上限。

## 当前优先级结论

静态上最值得后续单独取证的是：Subagent 冷恢复全表大 JSON、流式完整快照写入、MemoryTable 注入预算晚于 DAO 全量 payload 读取、MemoryTable 单文档更新事务的旧新快照并存。Hook history 有正常保留策略，Tags 主要是跨会话 ID 小对象。

没有任何现有堆栈、复现或 retained-object 证据指向 `MemoryTableDAO`、`HookDAO`、`ConversationTagDAO` 或 `SubagentContextDAO`，因此这些候选不能写成 #144 根因或“已修复项”。
