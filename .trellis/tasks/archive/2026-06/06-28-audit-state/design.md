# 状态机与持久化修复 — 技术设计

## 概述

本设计覆盖 PRD 中 FR-1～FR-5：会话从 Room 注入内存时的 stale `subagent_streaming`、Compose `remember` 键、会话 `StateFlow` CAS 竞争、`ConversationRepository` 全量删插节点，以及生成结束时的 metadata 清理范围。

**不改动的契约**：`subagent_streaming` / `subagent_transcript` 等 metadata 键语义与 `06-27-sub-agent-streaming-ui/design.md` 一致。

---

## 按文件分组

| 文件 | FR | 改动性质 |
|------|-----|----------|
| `app/.../service/ChatService.kt` | FR-1, FR-3, FR-5 | 加载清理、CAS/互斥、结束清理 |
| `app/.../ui/components/message/tools/SubagentToolUIs.kt` | FR-2 | remember 键与解析缓存 |
| `app/.../data/repository/ConversationRepository.kt` | FR-4 | 节点 diff 持久化 |
| `app/.../data/db/dao/MessageNodeDAO.kt` | FR-4 | 可选：按 id 批量查询（若 diff 需要） |
| `app/src/test/...` | 全部 | 单元/仪器测试补充 |

---

## FR-1（P0）+ FR-5（P1）：stale streaming 清理

### 技术方案

1. **抽取统一清理函数**（`ChatService.kt` 内，或 `Conversation` 扩展，与现有 `cleanStaleStreamingMetadata` 同文件）  
   - 扫描范围：**所有** `currentMessages` 中 `role == ASSISTANT` 的消息。  
   - 对每个 `spawn_subagent` 且 `isStreamingSubagent(part)` 的 tool：将 output 内 `Text.metadata["subagent_streaming"]` 置为 `false`（与 `1204-1235` 逻辑一致）。  
   - `FR-5`：`cleanupStreamingSubagentMetadata`（`1238-1280`）改为调用同一扫描逻辑，**删除**「仅最后一条 ASSISTANT」限制。

2. **`initializeConversation` 加载分支**（`331-336`）  
   ```
   repo.getConversationById → 若 null 走新建分支（不变）
   若 non-null:
     session = getOrCreateSession(id)
     conv = loaded
     if (!session.isGenerating) {
       cleaned = conv.cleanStaleStreamingMetadata()  // 或等价全量 assistant 扫描
       updateConversation(id, cleaned)
       if (cleaned != loaded) saveConversation(id, cleaned)  // 写回 Room，避免下次冷启动再假 streaming
     } else {
       updateConversation(id, conv)  // 同进程内生成中不重写 stale（避免与 updateSubagentProgress 竞态）
     }
   ```

3. **与 `generationJob` 的互斥策略（产品约定）**  
   - **新进程 / 冷启动**：`ConversationSession.isGenerating == false`，DB 中残留的 `subagent_streaming=true` 视为 **stale**，一律清理并写回。  
   - **同进程且 `isGenerating`**：`initializeConversation` 再次执行时（少见）**不**对加载数据做 stale 清理写回，避免覆盖内存中正在由 `updateSubagentProgress` 刷新的 metadata。  
   - **流式进行中**：chunk 路径已在 `onCompletion` 等使用 `cleanStaleStreamingMetadata`（`666`）；活跃子代理由 `updateSubagentProgress` 持续写 `streaming=true`，不会被「全 assistant 扫描」误伤，因为扫描只改 **当前仍为 streaming 标记** 的 part；若要在生成中清理非当前 tool 的 stale 标记，与 PRD 一致且安全（仅 false 化已标 true 的项，progress 会再写 true）。  
   - **不**在 `getOrCreateSession`（`245-260`）对 `Conversation.ofId` 空壳做清理：无消息，无意义；清理点在 **DB hydrate** 的 `initializeConversation`。

4. **`getOrCreateSession` 不改动**（除非未来有「从 DB 懒加载进 session」的第二路径，届时复用同一 `hydrateAndCleanStale` 辅助函数）。

### 权衡

| 方案 | 优点 | 缺点 |
|------|------|------|
| 加载时一律清 stale + 写回 DB | 冷启动 UX 确定 | 多一次 `saveConversation` I/O |
| 仅内存清、不写 DB | 少写 | 杀进程后 DB 仍脏，下次仍 spinner |
| **选用**：内存清 + 有变更则写回 | 满足 AC | 需防 `isGenerating` 时写回覆盖 |

### 风险

- **中**：`saveConversation` 在打开会话时触发全量节点写（FR-4 未落地前仍 O(n)）；与 FR-4 同批或先 FR-1 接受一次额外写。  
- **低**：`isGenerating` 判断遗漏导致加载覆盖进行中的会话 — 用 `ConversationSession.isGenerating` 缓解。

---

## FR-2（P1）：`SubagentToolUIs` remember 依赖

### 技术方案

1. 新增 **稳定内容键**（文件内 `private`）例如：  
   `subagentToolContentKey(context: ToolUIContext): Any`  
   组合：`context.loading`、`context.tool.toolCallId`、`context.tool.isExecuted`、以及 output 指纹：  
   - 首个 `UIMessagePart.Text` 的 `text` 长度 + `metadata` 中 `subagent_streaming`、`subagent_steps`、`subagent_transcript` 的 `toString()` 或步数（避免全量 JSON 哈希成本时可仅用 steps + streaming + text.length）。

2. **`title` / `Summary`**：`remember(subagentToolContentKey(context)) { ... }` 内集中调用 `parseSubagentMetadata`、`parseSubagentResult`、`transcriptStepsFromMetadata`。

3. **`hasSummary`**：改为使用与 Summary 相同的解析结果，或在 Composable 外不可行时，用 **相同指纹函数** 的轻量判断（例如 `subagentToolContentKey` + 解析一次），避免每帧全量 `parseSubagentResult`（R2）。

4. **不**改 `ToolUIRenderer` 接口；不依赖 `session.isGenerating`（UI 层无 ChatService 注入时保持 metadata + `context.loading` 驱动）。

### 权衡

| 方案 | 优点 | 缺点 |
|------|------|------|
| `remember(tool, loading, outputHash)` | 简单 | 哈希需稳定 |
| `derivedStateOf` | 自动跟踪 | 需读 state 在 Composable 内 |
| **选用**：显式 key 列表 + 单一 remember 块 | 与审计建议一致 | 指纹需随 metadata 字段演进维护 |

### 风险

- **低**：指纹过粗导致多余重组；过细导致仍冻结 — 以 `metadata` 流式字段为准调参。  
- **低**：`hasSummary` 在非 Composable 中无法 remember — 用共享 `parse` 工具 + 调用方传入已解析值（仅 `Summary`/`title` 缓存，`hasSummary` 用轻量字段检查）。

---

## FR-3（P1）：CAS 重试饱和

### 选型（推荐）

**方案 A — 每会话 `Mutex` 串行化状态写（推荐）**

- 在 `ConversationSession` 增加 `stateMutex: Mutex`（或 `ChatService` 内 `ConcurrentHashMap<Uuid, Mutex>`）。  
- `commitConversationState` / `updateConversationState` 在 `session.stateMutex.withLock { ... }` 内：读 `prev` → 计算 `next` → `session.state.value = next`（或单次 `compareAndSet` 无竞争）。  
- **去掉** 50 次 CAS 循环作为主路径；保留 `checkFilesDelete` 在锁内执行。

**方案 B — CAS 失败降级（备选，可与 A 二选一）**

- 50 次失败后：对 `updateConversationState` 的 `update`，在最新 `prev` 上 **再执行一次** `update` 并 `session.state.value = result`（强制写）。  
- 风险：丢失中间帧，但优于静默丢弃整次 delta。

**方案 C — 仅节流 chunk（不充分）**

- 对 `GenerationChunk.Messages` debounce — 降低频率但 **不** 解决 `updateSubagentProgress` 与 chunk 并行；PRD 要求禁止仅增大重试次数。

**不采用**：单纯 `repeat(500)` 增大重试。

### 权衡

| 方案 | 优点 | 缺点 |
|------|------|------|
| Mutex | 无丢更新、实现清晰 | 高频更新串行，略增延迟 |
| 强制 set 降级 | 改动小 | 可能跳帧 |
| **选用 Mutex** | 满足 AC「无静默丢整表」 | 需在 `ConversationSession.cleanup` 时无锁泄漏 |

### 风险

- **中**：锁粒度为整表 `Conversation`，单次 `update` 过慢会阻塞 progress — 保持 `update` 轻量（现有 map 消息列表）。  
- **低**：与 `saveConversation` 并发：保存读 `StateFlow.value`，应在锁外读快照或读时短暂锁 — 设计为读 `state.value` 与写同锁。

### 验证关注点

- 长流式 + 多子代理：Log 无 `CAS retry limit exceeded`（或删除该日志路径）。  
- `currentMessages` 顺序与 chunk 顺序一致。

---

## FR-4（P1）：Room 消息节点 diff

### 技术方案（短期最小交付）

在 `ConversationRepository.updateConversation`（`220-228`）事务内：

1. `conversationDAO.update(entity)` 不变。  
2. **替换** `deleteByConversation` + 全量 `saveMessageNodes`：  
   - `existing = messageNodeDAO.getNodesOfConversation(conversationId)`  
   - `existingIds = existing.map { it.id }.toSet()`  
   - `newIds = nodes.map { it.id.toString() }.toSet()`  
   - **删除孤儿**：`existingIds - newIds` → `messageNodeDAO.deleteById`  
   - **写入/更新**：对 `nodes.forEachIndexed { index, node ->` 构建 `MessageNodeEntity(..., nodeIndex = index, messages = encode)`，`messageNodeDAO.insert`（已有 `OnConflictStrategy.REPLACE`）或 `insertAll` 仅新列表（REPLACE 覆盖同 id）  
3. **未变更节点**：若 `messages`/`selectIndex`/`nodeIndex` 与 DB 相同，可跳过 insert（可选优化）；最小实现可对全列表 REPLACE，但 **不** `deleteByConversation`，则未删除的 id 保留，仅更新有变化的行 — 仍满足「不删除未变更节点行」。  
4. **更优最小 AC**：仅当节点 id 不在新列表时才 delete；新列表中每个节点 upsert；**禁止**整表 `deleteByConversation`。  
5. `insertConversation` 仍全量插入（新会话）。  
6. 事务后 `messageFtsManager.indexConversation` 不变。

`MessageNodeDAO` 已有 `insert(REPLACE)`、`update`、`deleteById`（`28-41`），无需 schema 迁移。

### 权衡

| 方案 | 优点 | 缺点 |
|------|------|------|
| 全量 REPLACE 无 deleteAll | 实现快，满足「不删未变更 id」 | 仍 rewrite BLOB 对 touch 的节点 |
| 字节级 diff 跳过写 | 最少 I/O | 实现复杂 |
| **选用**：孤儿 delete + 全列表 REPLACE | 平衡 AC 与工期 | 大节点 JSON 仍整行写 |

### 风险

- **中**：`node_index` 重排遗漏导致分页顺序错 — 每个 upsert 必须写 `node_index = index`。  
- **中**：fork 删节点 — 孤儿 delete 必须执行。  
- **低**：FTS 与事务一致性 — 保持 index 在事务提交后。

---

## 跨项依赖与实施顺序

```
FR-1 + FR-5（ChatService 清理统一）
    ↓
FR-2（UI，可并行）
FR-3（Mutex，独立）
FR-4（Repository，改动面大，建议 FR-1 写回 DB 前或后均可，但 FR-1 写回会放大 FR-4 收益）
```

---

## 回滚策略（设计层）

- **FR-1/5**：还原 `initializeConversation` 与 `cleanupStreamingSubagentMetadata`；保留 `cleanStaleStreamingMetadata` 原样。  
- **FR-2**：还原 `remember(context.tool)`。  
- **FR-3**：还原 CAS 循环，移除 Mutex。  
- **FR-4**：还原 `deleteByConversation` + `saveMessageNodes`。

---

## 测试策略

| FR | 建议测试 |
|----|----------|
| FR-1 | JVM：对含 stale metadata 的 `Conversation` 扩展清理；仪器/手动冷启动 |
| FR-5 | JVM：`cleanupStreamingSubagentMetadata` 多 assistant 消息场景 |
| FR-2 | Compose UI test 或手动步骤文档 |
| FR-3 | JVM fake session 高频 `updateConversationState` |
| FR-4 | Room in-memory DB：更新单节点后 `getNodesOfConversation` 数量与其它 id 仍存在 |