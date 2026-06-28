# 状态机与持久化修复

## Goal

修复夜间审计（`06-28-nightly-audit`）中与子代理流式生命周期、会话内存状态 CAS、以及会话消息持久化相关的 **P0/P1** 问题，避免冷启动假 streaming、子代理卡片 UI 冻结、高频更新丢状态、以及长会话全量删插带来的性能与一致性风险。

**范围外**：日志脱敏（`LogPage`）、Web JWT、i18n、subagent 沙箱审批等（见父任务或其它子任务）。

## 背景与依赖

- 审计总报告：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`（批次 B）
- UI 研究：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-b-ui.md`
- ChatService / 持久化研究：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-d-chat-log.md`
- 与 `06-28-review-fixes` R3 同源：**会话加载不清理 stale `subagent_streaming`**

## Requirements

### FR-1（P0）会话 bootstrap 清理陈旧 `subagent_streaming`

| 项 | 内容 |
|----|------|
| **来源** | Subagent D **S1**；Subagent B **S1/S5** |
| **位置** | `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:331-335`（`initializeConversation`）；关联 `getOrCreateSession` `245-260`（仅 `Conversation.ofId`，不 hydrate 清理） |
| **现状** | 从 Room 加载会话后 `updateConversation(conversationId, conversation)`，**未**对持久化消息执行 `cleanStaleStreamingMetadata()`（实现见 `1204-1235`）。进程在子代理流式中途被杀后，DB 仍可能保留 `subagent_streaming=true`，UI 按 metadata 显示长期 loading（`SubagentToolUIs.kt:74,123`）。 |
| **需改** | 在 **任何** 将会话从 DB 注入内存的路径（至少 `initializeConversation` 加载分支）在 `updateConversation` 前对 `Conversation` 调用等价清理：将所有 ASSISTANT 上 `spawn_subagent` 且 `isStreamingSubagent` 的 metadata 中 `subagent_streaming` 置 `false`（复用 `Conversation.cleanStaleStreamingMetadata()`）。若清理改变了消息内容，应在首次加载后 **写回 Room**（`saveConversation` 或 repo `updateConversation`），避免下次冷启动再次假 streaming。 |
| **验收标准** | 1）构造/单测或手动：DB 中 assistant 工具 metadata `subagent_streaming=true`、且无活跃 `generationJob`，打开该会话后 UI **不再**显示子代理 streaming spinner。2）清理后持久化字段与内存一致；重新杀进程再打开仍为非 streaming。3）正常生成中（活跃 Job + 真实 streaming）**不得**被加载路径误清（加载路径仅在无进行中生成或按产品约定在 bootstrap 时一律清 stale——须在 `design.md` 明确与 `generationJob` 的交互）。 |

**优先级**：P0

---

### FR-2（P1）`SubagentToolUIs` 的 `remember` 依赖修正

| 项 | 内容 |
|----|------|
| **来源** | Subagent B **R1** |
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt:70,84,122-125`（`title` / `Summary` 中 `remember(context.tool)`）；`hasSummary` `112-117` 每次重组全量解析（关联 **R2**，本任务可选一并优化） |
| **现状** | `remember` 仅以 `UIMessagePart.Tool` **引用**为 key；流式更新若原地修改 `output`/`metadata` 而引用不变，Compose 跳过重组，标题/摘要/transcript **冻结**。 |
| **需改** | 为 `remember` 增加稳定内容指纹，例如：`context.loading`、`context.tool.output`（或 output 内 Text 的 metadata/transcript 序列化哈希）、`context.tool.toolCallId`；或改为 `derivedStateOf` / 显式 key 列表。`hasSummary` 应与 `Summary` 共用同一缓存键，避免重复解析。 |
| **验收标准** | 1）子代理运行中 metadata/`output` 递增更新时，卡片标题步数、CoT/transcript、streaming 指示 **实时**刷新（无需整页导航）。2）完成态与失败态切换后 UI 与 `ChatService` 最终 metadata 一致。3）补充或扩展 Compose/UI 层测试（若已有 subagent UI 测试基架）或文档化手动验证步骤。 |

**优先级**：P1

---

### FR-3（P1）长流式 + 并行子代理下 CAS 重试饱和

| 项 | 内容 |
|----|------|
| **来源** | Subagent D **S3** |
| **位置** | `ChatService.kt:1108-1132`（`commitConversationState` / `updateConversationState`，50 次 CAS 后仅 `Log.w` 并 **丢弃**本次更新）；`673-677` 主流每个 `GenerationChunk.Messages` 调用 `updateConversationState`；`1135-1193` `updateSubagentProgress` 同路径 |
| **现状** | 高 chunk 频率与多路 `updateSubagentProgress` 竞争同一 `MutableStateFlow`，易出现 `CAS retry limit exceeded`，导致 **单次** UI/内存状态更新丢失（无合并重试）。 |
| **需改** | 至少一种可验证策略（在 `design.md` 选型）：合并/节流会话级更新（例如 chunk 与子代理进度 coalesce 到单帧）、提高失败路径的 **强制 set** 或队列化单线程 mutator、对 `updateSubagentProgress` 与主流 chunk 做版本号/脏标记合并。禁止仅增大重试次数而无背压。 |
| **验收标准** | 1）压力场景（长 assistant 流式 + ≥2 并行子代理或高频 progress）下，日志中 `CAS retry limit exceeded` **显著减少或为零**；若仍触发，须有降级（例如最后一次强制写入或合并 pending delta）。2）不引入消息顺序错乱或丢失整段 `currentMessages`。3）现有 `ChatService`/会话相关单测通过；新增针对 CAS 失败或高频更新的回归测试（推荐）。 |

**优先级**：P1

---

### FR-4（P1）`saveConversation` / Room 全量 delete+insert 节点

| 项 | 内容 |
|----|------|
| **来源** | Subagent D **DS-1** |
| **位置** | `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt:220-228`（`updateConversation`：`messageNodeDAO.deleteByConversation` + `saveMessageNodes` 全量插入）；调用链 `ChatService.kt:1295+` `saveConversation` |
| **现状** | 每次保存 O(n) 删插全部 `message_nodes`；长会话、编辑/翻译/多次保存写放大；若未来逐 chunk 落库风险极高（当前流式中间态主要在内存，但成功路径仍会全量写）。 |
| **需改** | 短期（本任务最小交付）：按节点 id **diff**——仅 `insert`/`update`/`delete` 变更节点，或 upsert 单节点 API；保持事务与 FTS `indexConversation` 行为正确。长期优化可在 `implement.md` 分阶段。 |
| **验收标准** | 1）同一会话仅修改最后一条消息节点时，Room 层 **不**删除未变更节点（可通过 DAO 调用计数、测试 fake、或 SQL 日志断言）。2）fork/分支、`messageNodes` 增删顺序与审计前行为一致（回归：加载分页 `ConversationRepository.kt:340-377` 仍可用）。3）`saveConversation` 后冷启动加载内容与保存前一致。 |

**优先级**：P1

---

### FR-5（P1，建议同批）`cleanupStreamingSubagentMetadata` 扫描范围

| 项 | 内容 |
|----|------|
| **来源** | Subagent D **S2**（与 FR-1 同主题，生成结束路径） |
| **位置** | `ChatService.kt:1238-1280`（仅 **最后一条** ASSISTANT 上的 `spawn_subagent`） |
| **需改** | 生成完成/失败时，对所有含 `isStreamingSubagent` 的 assistant 消息清理 metadata（与 `cleanStaleStreamingMetadata` 扫描范围对齐），避免重试/分支场景遗留。 |
| **验收标准** | 非最后一条 assistant 上若残留 streaming 标记，在 `handleMessageComplete` 成功/失败路径后均被清除。 |

**优先级**：P1（可与 FR-1 同一 PR，共享清理逻辑）

---

## 非功能约束

- 不改变 `subagent_streaming` / `subagent_transcript` 对外 metadata 契约（见 `06-27-sub-agent-streaming-ui/design.md`）。
- 加载路径清理 stale streaming 时须与 `getGenerationJobStateFlow` / 活跃生成互斥策略一致，避免打断进行中的合法流式。
- Kotlin/Compose 遵循仓库 `.editorconfig`；改 `app` 模块后按 `AGENTS.md` 执行 `:app:installDebug` 真机验收（用户未禁止时）。

## Acceptance Criteria（汇总）

- [ ] **P0** 冷启动/切换会话：DB 残留 `subagent_streaming=true` 不再导致永久 spinner；必要时写回 DB。
- [ ] **P1** 子代理卡片在流式中原地更新 output/metadata 时 UI 持续刷新（`SubagentToolUIs` remember 修复）。
- [ ] **P1** 高频 chunk + 子代理 progress 下 CAS 饱和有明确降级或合并策略，且无静默丢整表消息。
- [ ] **P1** `ConversationRepository.updateConversation` 不再每次全量 delete 全部 message_nodes（diff/upsert）。
- [ ] **P1**（建议）生成结束清理覆盖所有 assistant 上的 stale spawn streaming，不仅最后一条。

## 建议实施顺序

1. FR-1 + FR-5（共享 `cleanStaleStreamingMetadata` / 扫描逻辑）
2. FR-2（UI，可并行）
3. FR-3（CAS）
4. FR-4（持久化，改动面最大，需 `design.md`）

## Notes

- `cleanStaleStreamingMetadata` 当前在 `onCompletion` 路径调用（`ChatService.kt:666`），**不**覆盖 `initializeConversation`（`331-335`）。
- `updateSubagentProgress` 内 JSON `text` 字段 `streaming:true`（`1159-1163`）与 metadata 清理不同步为 **P2 S4**，本任务可选 follow-up，不阻塞上述 AC。
- 复杂任务：在 `task.py start` 前补充 `design.md`（CAS 方案、加载时与 Job 互斥、Room diff 策略）与 `implement.md`（验证命令、回滚点）。