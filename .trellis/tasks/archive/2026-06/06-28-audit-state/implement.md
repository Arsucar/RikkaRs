# 状态机与持久化修复 — 实施计划

## 前置

- 已阅读：`prd.md`、本任务 `design.md`  
- 分支建议：`release/rikka-arsucar` 或任务专用分支  
- **未** `task.py start` 前勿视为正式 in_progress 实施（规划产物本步骤除外）

---

## 有序检查清单

### 阶段 1：FR-1 + FR-5（`ChatService.kt`）

- [ ] **1.1** 确认 `Conversation.cleanStaleStreamingMetadata()`（`1204-1235`）扫描范围为**全部** assistant 消息（已满足则仅复用）。  
- [ ] **1.2** 重构 `cleanupStreamingSubagentMetadata`（`1238-1280`）：改为对 `conversation.currentMessages` 全量 assistant 应用与 1.1 相同逻辑，经 `updateConversationState` 写回；删除 `lastAssistantIndex` 单条限制。  
- [ ] **1.3** 抽取可选辅助 `hydrateConversationFromDb(loaded: Conversation, session: ConversationSession): Conversation`：  
  - `if (session.isGenerating) return loaded`  
  - `else return loaded.cleanStaleStreamingMetadata()`  
- [ ] **1.4** 修改 `initializeConversation`（`331-336`）：`getConversationById` 非 null 时，`hydrate` → `updateConversation`；若 `hydrated != loaded` 则 `saveConversation(conversationId, hydrated)`。  
- [ ] **1.5** 确认新建会话分支（`337-347`）不受影响。  
- [ ] **1.6** 日志：加载写回时可 `Log.d` 一次（可选），避免 PII。

**文件:行锚点**：`ChatService.kt:331-336`、`666`、`695-706`、`1204-1280`

---

### 阶段 2：FR-2（`SubagentToolUIs.kt`）

- [ ] **2.1** 实现 `subagentToolContentKey(context)`（约 `61` 行后）：`loading`、`toolCallId`、`isExecuted`、metadata 的 `subagent_streaming`/`subagent_steps`、output text 长度或摘要。  
- [ ] **2.2** `title`（`69-109`）：`remember(subagentToolContentKey(context))` 包裹 meta/result 解析。  
- [ ] **2.3** `Summary`（`121-125`）：同上，统一解析 meta、`metaTranscript`、`result`。  
- [ ] **2.4** `hasSummary`（`112-117`）：用轻量检查（metadata steps / task 参数）或与 2.1 指纹一致的解析函数，避免无 key 的重复重解析。  
- [ ] **2.5** 手动：子代理运行中观察标题步数、CoT、spinner 随 progress 刷新。

**文件:行锚点**：`SubagentToolUIs.kt:70-84`、`112-125`

---

### 阶段 3：FR-3（`ConversationSession.kt` + `ChatService.kt`）

- [ ] **3.1** `ConversationSession`：添加 `val stateMutex = Mutex()`（`import kotlinx.coroutines.sync`）。  
- [ ] **3.2** `commitConversationState`（`1108-1118`）：`runBlocking` 不可用；使用 `appScope` 或调用方已在协程则 `withLock` — **注意**：当前为同步函数。设计选用：`Mutex.tryLock` 或将会话更新改为 `suspend` 内 `withLock`，或 `synchronized(session)` 与协程 progress 兼容。  
  - **实施默认**：`synchronized(session.stateLock)` 对象锁包裹 read-modify-write（与现有同步 `updateConversationState` 调用栈一致）；或 `Mutex` + `runBlocking` 仅测试禁止。  
  - **推荐落地**：在 `ConversationSession` 使用 `@Volatile` + `synchronized(this)` 块内 `state.value = newState`，移除 CAS 循环。  
- [ ] **3.3** `updateConversationState`（`1121-1132`）：同锁内 `prev` → `update(prev)` → 赋值。  
- [ ] **3.4** 移除或降级 `CAS retry limit exceeded` 日志（不应再触发）。  
- [ ] **3.5** 可选：JVM 测试高频 `updateConversationState`（mock session）。

**文件:行锚点**：`ChatService.kt:673-677`、`1108-1132`、`1135-1193`；`ConversationSession.kt`

---

### 阶段 4：FR-4（`ConversationRepository.kt`）

- [ ] **4.1** 新增 `private suspend fun syncMessageNodes(conversationId: String, nodes: List<MessageNode>)`（约 `381` 附近）。  
- [ ] **4.2** 实现：读 `getNodesOfConversation` → 删孤儿 id → `forEachIndexed` upsert `MessageNodeEntity`（`insert` REPLACE）。  
- [ ] **4.3** `updateConversation`（`220-228`）：用 `syncMessageNodes` 替换 `deleteByConversation` + `saveMessageNodes`。  
- [ ] **4.4** `insertConversation` 保持 `saveMessageNodes` 全量插入。  
- [ ] **4.5** 确认 `loadMessageNodes` 分页（`340-377`）`ORDER BY node_index` 与写入一致。  
- [ ] **4.6** Room 仪器或单元测试：2 节点会话只改 node2 后 node1 行仍在（可查 count 或 id 列表）。

**文件:行锚点**：`ConversationRepository.kt:220-228`、`381-391`；`MessageNodeDAO.kt:28-41`

---

## 验证步骤

### 编译与单测

```bash
.\gradlew :app:compileDebugKotlin
.\gradlew :app:testDebugUnitTest
.\gradlew test
```

（按需）`.\gradlew lint` — 本任务未强制全仓库 lint。

### 功能验收（对照 PRD AC）

| AC | 步骤 |
|----|------|
| P0 冷启动 spinner | 造 DB/或杀进程 mid-subagent：metadata `subagent_streaming=true`，无活跃 Job → 打开会话 → 无永久 spinner；再杀进程打开仍 false |
| P1 UI 刷新 | 子代理运行中看卡片标题/CoT 更新 |
| P1 CAS | 长回复 + 并行子代理，logcat 过滤 `CAS retry limit exceeded` 应为 0 |
| P1 Room diff | 保存仅改最后节点后，DB 中其它 `message_node.id` 仍存在（测试或 sqlite 调试） |
| P1 FR-5 | 非最后 assistant 上 stale streaming，生成结束/失败后均 false |

### 真机（`AGENTS.md`）

```bash
adb devices
.\gradlew :app:installDebug
```

包名一般为 `me.arsucar.rikka.debug`。在真机复现冷启动与子代理流式场景。

---

## Review 门禁

- [ ] 代码审：FR-1 在 `isGenerating` 时不写回 stale 清理  
- [ ] 代码审：FR-4 事务内先删孤儿再 upsert，`node_index` 连续  
- [ ] 单测绿  
- [ ] 真机 P0 路径通过（有设备时）

---

## 回滚计划

| 阶段 | 回滚操作 | 影响 |
|------|----------|------|
| 1 | `git revert` FR-1/5 提交；或恢复 `initializeConversation` 直传 `updateConversation` | 冷启动 spinner 回归 |
| 2 | 恢复 `remember(context.tool)` | UI 冻结回归 |
| 3 | 恢复 CAS `repeat(50)` | CAS 丢更新回归 |
| 4 | 恢复 `deleteByConversation` + `saveMessageNodes` | 写放大回归 |

**建议**：分 2～4 个 commit（1+5、2、3、4），便于局部 revert。

---

## 建议 PR 切分

1. PR1：`ChatService` FR-1 + FR-5 + 单测  
2. PR2：`SubagentToolUIs` FR-2  
3. PR3：`ChatService`/`ConversationSession` FR-3  
4. PR4：`ConversationRepository` FR-4 + Room 测试  

或单 PR（任务规模允许时），仍保持 commit 分段。

---

## 完成后

- 勾选 `prd.md` AC  
- `trellis-check` / 质量门禁  
- 用户要求时再 commit；发版遵循 `CHANGELOG.md` 与标签流程