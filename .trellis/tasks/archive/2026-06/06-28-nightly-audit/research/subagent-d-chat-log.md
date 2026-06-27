# 综合审计：ChatService 流式 / 日志脱敏 / 持久化

**路径**: `D:\2026Code\Group_android\rikkahub`  
**HEAD**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6` (`chore(task): archive 06-28-06-28-fix-review-issues`)  
**近 20 提交相关 diff 统计**:
- `app/.../ChatService.kt`: +271 / -19（子代理流式 metadata、CAS、`cleanStaleStreamingMetadata` 等）
- `common/.../LogRedaction.kt`: +46（新增）
- `ConversationRepository.kt`: 本窗口无直接 diff（逻辑仍为 Room 全量删插节点）

**说明**: 任务范围中的 `LogRepository.kt` **不存在**；会话消息持久化在 `ConversationRepository` + Room，设置与请求日志开关在 `PreferencesStore` + 内存 `Logging`。

---

## 1. ChatService 流式回调（throttle / CAS / suppression）

### 1.1 Throttle（子代理进度）

| 项 | 位置 | 结论 |
|----|------|------|
| 节流 | `SubagentHost.kt:198-218` | `minIntervalMs = 120`，按 assistant `parts.size` 签名 + 时间间隔抑制；在独立 `progressScope`（`Dispatchers.IO`）上 `launch` 调用 `updateSubagentProgress` |
| 收尾 | `SubagentHost.kt:239-240` | `finally { progressScope.cancel() }`，避免泄漏 |

**P2** `SubagentHost.kt:211-215`：节流通过后仍可能排队多个 `launch`；高步数子代理下 `updateSubagentProgress` → `updateConversationState` 频率仍可能偏高（仅抑制回调次数，不合并队列深度）。

### 1.2 CAS 与会话状态

| 项 | 位置 | 结论 |
|----|------|------|
| CAS | `ChatService.kt:1108-1132` | `commitConversationState` / `updateConversationState` 对 `MutableStateFlow` 使用 `compareAndSet`，最多 50 次重试 |
| 会话载体 | `ConversationSession.kt:25` | `state` 为 `MutableStateFlow`（底层 `StateFlowImpl` 支持 CAS） |

**P1** `ChatService.kt:673-677`：主流 `GenerationChunk.Messages` 每次 chunk 调用 `updateConversationState`；子代理进度同路径。长流式回复 + 并行子代理时 CAS 重试或 `CAS retry limit exceeded`（`ChatService.kt:1118,1132`）会导致**丢一次 UI/内存状态更新**（仅打 Log，无降级合并）。

**P2** `ChatService.kt:1159-1163` + `1204-1235`：`updateSubagentProgress` 写入 `partialOutputText` 内 `"streaming":true`，而 `cleanStaleStreamingMetadata` / `cleanupStreamingSubagentMetadata` **只改 metadata 的 `subagent_streaming`**，不更新 JSON `text` 字段。`SubagentToolUIs.kt:67,116` 以 metadata 为准，当前 UI 可工作，但 **text 与 metadata 结束态不一致**，若其他消费者只解析 `text` 会误判。

### 1.3 Suppression / 清理策略

| 路径 | `cleanStaleStreamingMetadata` | `cleanupStreamingSubagentMetadata` |
|------|------------------------------|-----------------------------------|
| 流式 chunk | `ChatService.kt:677` | — |
| `onCompletion` 兜底 | `ChatService.kt:666` | — |
| `updateConversation` / `saveConversation` | `ChatService.kt:1105` | — |
| 生成成功/失败 | — | `ChatService.kt:695-706` |

**合理点**: 活跃生成过程中通过 `cleanStaleStreamingMetadata` 把仍标记 streaming 的 spawn 工具 metadata 置 false，避免与新一轮 chunk 冲突。

---

## 2. CancellationException 传播

| 位置 | 行为 | 评级 |
|------|------|------|
| `ChatService.kt:202` | `addError` 忽略 `CancellationException` | 合理 |
| `ChatService.kt:389-392,449-451` 等 | `sendMessage` / `regenerate` 等 `catch (e: Exception)` **吞掉**取消，不 rethrow | **P2** 协程结构化取消语义被破坏；调用方无法区分用户取消与真错（通常仅影响错误 Toast） |
| `ChatService.kt:687-695` | `handleMessageComplete` 外层 `runCatching { ... }.onFailure`：取消会走 failure 分支，打栈、`addError`（被 202 过滤）、仍 `cleanupStreamingSubagentMetadata` | **P3** 取消被当作失败路径处理，但 metadata 清理可接受 |
| `SubagentHost.kt:239-240` | 子代理结束取消 progressScope | 合理 |

**P0 未发现**：未见在应传播处 `catch` 后阻止父 Job 取消的致命 bug；主要问题是 **未区分 Cancellation 与普通 Exception**（P2）。

---

## 3. 子代理 transcript 残留 / `cleanupStreamingSubagentMetadata` 覆盖

| 场景 | 是否覆盖 | 证据 |
|------|----------|------|
| 生成正常结束 | 是 | `ChatService.kt:706` |
| 生成异常 | 是 | `ChatService.kt:695` |
| 流被取消 / `onCompletion` | 部分 | `onCompletion` 调 `cleanStaleStreamingMetadata`（`666`），非专用 `cleanupStreamingSubagentMetadata`；metadata 效果类似 |
| **冷启动 / 切换会话 / `initializeConversation`** | **否** | `ChatService.kt:331-335` 仅 `updateConversation(repo.load)`，**无** stale streaming 清理 |
| 进程被杀 mid-flight 后 DB 残留 | **否** | 持久化的 `subagent_streaming=true` / transcript 在加载时不修复 |

**P0** `ChatService.kt:331-335`：与任务 PRD（`06-28-review-fixes`）一致——**会话加载路径缺失 stale streaming 清理**，UI `SubagentToolUIs.kt:67,116` 可能长期显示 streaming / 加载态。

**P1** `cleanupStreamingSubagentMetadata`（`1238-1280`）只处理 **最后一条 ASSISTANT** 消息上的 `spawn_subagent`；若 streaming 标记落在更早 assistant 消息（重试/分支场景），**不会清理**。

**P2** `ChatService.kt:687-695`：`onFailure` 中 `printStackTrace` + `Logging.log` 全栈，可能把非敏感上下文打进 **未脱敏** `TextLog`（见第 4 节）。

---

## 4. 日志脱敏

### 4.1 实现

| 组件 | 文件 | 行为 |
|------|------|------|
| 核心 | `common/.../LogRedaction.kt:3-46` | 头：`authorization`, `x-api-key`, `cookie`, `set-cookie`, `x-amz-security-token` 等；体：JSON 键 `api_key`/`token`/`secret`/`access_token` 等（单正则） |
| 存储 | `common/.../Logging.kt:38-64` | 环形缓冲 **原始** 条目，`MAX_RECENT_LOGS=32` |
| 开关 | `PreferencesStore.kt:97,221,428` + `RikkaHubApp.kt:165` | DataStore 持久化 `request_logging_enabled`，启动同步到 `Logging` |
| AI 工具 | `LogsTool.kt:56,103-105` | `type` 枚举 `all|request|text`，`limit` 1–32，输出前 `.redacted()` + 体积截断 |
| UI 列表 | `LogPage.kt:72` | `remember { Logging.getRecentLogs() }` **快照，非 redacted** |
| UI 导出 | `LogPage.kt:87-89` | 导出 JSON 使用 `.redacted()` |
| UI 详情 | `LogPage.kt:304-411` | `RequestLogDetail` 直接展示 **原始** `requestHeaders` / `requestBody` / `responseHeaders` |

### 4.2 缺口

| ID | 严重度 | 位置 | 描述 |
|----|--------|------|------|
| L1 | **P0** | `LogPage.kt:186-191,304-411` | 开发者查看请求详情时 **明文密钥/ Cookie**；与导出/LogsTool 策略不一致 |
| L2 | **P1** | `LogRedaction.kt:22-27` | 仅匹配 **带引号的 JSON 键值**；`Bearer sk-...` 在 body 明文、query `?api_key=`、非 JSON 体 **不遮** |
| L3 | **P1** | `LogRedaction.kt:3-13` | 无 `x-session-id` / `session-id` / 通用 `password` 头；任务要求中的 session-id 头 **未覆盖** |
| L4 | **P2** | `LogRedaction.kt:39-40` | `TextLog.redacted()` 原样返回；`ChatService` 等 `Logging.log(TAG, stackTrace)` 可含 provider 错误正文 |
| L5 | **P3** | `Logging.kt:47-49` | 请求日志关闭时不记录；已存条目仍保留至 clear；符合设计 |

**web 模块**: 仓库 `web/` 下 **无** Logging/redaction 相关 Kotlin；HTTP 日志主要在 app + `common`。

---

## 5. DataStore / 大对象持久化性能

### 5.1 Settings（DataStore）

| 项 | 位置 | 结论 |
|----|------|------|
| 模型 | `PreferencesStore.kt:70-79,417-459` | 单 `settings` DataStore；`update()` 一次 `edit` 写入多枚 **JSON 字符串**（`providers`, `assistants`, `search_services` 等） |
| 读路径 | `settingsFlowRaw` + `distinctUntilChanged` | 合理 |
| 会话消息 | **不在** DataStore | 消息在 Room |

**P2** `PreferencesStore.kt:453-459`：任意小改（如主题）仍可能重写整块 `providers`/`assistants` JSON，**写放大**；大配置时 `edit` 成本与 ANR 风险需关注（非本次 Chat 流式热点，但属「DataStore 大对象」范畴）。

### 5.2 Conversation（Room）

| 项 | 位置 | 结论 |
|----|------|------|
| 更新策略 | `ConversationRepository.kt:220-228` | **每次** `updateConversation`：`deleteByConversation` + `insertAll` 全部节点 |
| 节点序列化 | `ConversationRepository.kt:381-391` | 每节点 `JsonInstant.encodeToString(node.messages)` |
| 流式期 DB 写 | `ChatService.kt:1295-1308` | 流式中间态主要 `updateConversationState`（内存）；**持久化**在生成成功等 `saveConversation`（如 `698`） |
| 读分页 | `ConversationRepository.kt:340-377` | `pageSize=64` 加载节点，遇 `SQLiteBlobTooBigException` 跳过页 |

**P1** `ConversationRepository.kt:225-227`：长会话每次保存 **O(n) 删+插**；编辑/翻译/ fork 多次保存时放大。流式若未来改为逐 chunk 落库将极危险（当前未逐 chunk 落库，**P3 现状可接受**）。

**P2** `ChatService.kt:1295-1308`：`saveConversation` 先 `updateConversation`（内存+CAS）再 Room 事务，无单飞合并；快速连续 `saveConversation` 可能排队多次全量 rewrite。

---

## 6. LogsTool 入参白名单

| 参数 | 约束 | 文件 |
|------|------|------|
| `type` | Schema enum：`all`, `request`, `text`；默认 `all` | `LogsTool.kt:85-92,103` |
| `limit` | 默认 20，`coerceIn(1, 32)` | `LogsTool.kt:94-97,104-105` |
| 输出 | `MAX_PAYLOAD_CHARS=16*1024`，逐条累计 JSON 长度 | `LogsTool.kt:22,58-68` |

**P3** 无额外键过滤，但 Tool schema 仅暴露上述字段；**未发现**任意路径注入或读取磁盘日志文件（仅内存 `Logging`）。

---

## 7. 发现汇总（按严重度）

| 级别 | ID | file:line | 摘要 |
|------|-----|-----------|------|
| P0 | S1 | `ChatService.kt:331-335` | 加载会话不清理 stale `subagent_streaming` / 冷启动永久 spinner |
| P0 | L1 | `LogPage.kt:304-411` | 请求详情 UI 展示未脱敏头/体 |
| P1 | S2 | `ChatService.kt:1238-1280` | cleanup 仅最后一条 assistant 的 spawn 工具 |
| P1 | S3 | `ChatService.kt:1108-1132` + `673-677` | 高频 chunk + 子代理 CAS 竞争，可能丢更新 |
| P1 | P1 | `ConversationRepository.kt:225-227` | 全量删插 message_nodes |
| P1 | L2 | `LogRedaction.kt:22-27` | body 脱敏面窄 |
| P1 | L3 | `LogRedaction.kt:3-13` | 缺 session-id/password 等头 |
| P2 | S4 | `ChatService.kt:1159-1163` vs `1204-1235` | JSON text `streaming` 与 metadata 不同步 |
| P2 | S5 | `ChatService.kt:389-392` 等 | Cancellation 被当作普通 Exception |
| P2 | S6 | `SubagentHost.kt:211-215` | 节流后 IO 队列仍可能堆积 |
| P2 | L4 | `LogRedaction.kt:39-40` + `ChatService.kt:693-694` | TextLog/栈轨迹未脱敏 |
| P2 | DS1 | `PreferencesStore.kt:417-459` | 大块 settings JSON 整包写 |
| P3 | S7 | `ChatService.kt:687-695` | 取消走 onFailure 日志路径 |
| P3 | L5 | `Logging.kt` | 关记录不清历史 |
| P3 | T1 | `LogsTool.kt` | 入参白名单充分 |

---

## 明早 Top 5

1. **P0** 在 `initializeConversation`（及必要时 repo→state 的每条加载路径）对会话执行与 `cleanStaleStreamingMetadata` 等价的持久化/内存修复，避免杀进程后 UI 假 streaming（`ChatService.kt:331-335`）。
2. **P0** `LogPage` 详情（及列表若展示敏感字段）统一走 `log.redacted()` 或分字段 redact（`LogPage.kt:304-411`）。
3. **P1** 评估 `cleanupStreamingSubagentMetadata` 扫描范围：所有含 `isStreamingSubagent` 的 assistant 消息，而非仅最后一条（`ChatService.kt:1238-1241`）。
4. **P1** 扩展 `LogRedaction`：session/cookie 变体、Bearer 明文、query 参数；补测试（`LogRedaction.kt`，对照 `LogRedactionTest.kt`）。
5. **P1** 会话保存：增量更新节点或 diff 写入，避免每次 `deleteByConversation` + 全量 insert（`ConversationRepository.kt:220-228`）；短期可记录保存频率与节点数指标。

---

*审计方式：只读 grep/read；未修改业务代码。*