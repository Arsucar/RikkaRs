# Research: Service 层未提交改动代码审查

- **Query**: 审查 `ChatService.kt`、`RouteActivity.kt` 在 `release/rikka-arsucar` 上的 git diff（子代理全局配置 + 流式进度 + 扩展路由）
- **Scope**: internal（diff + 关联 `GenerationHandler` / `SubagentHost` / `SubagentRegistry`）
- **Date**: 2026-06-28

## 审查范围

| 文件 | 改动主题 |
|------|----------|
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 全局子代理合并、`conversationId` 下传、流式 transcript 写回、`updateConversationState` CAS、`manageSubagentProfile` 语义 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `ExtensionSubagents` / `ExtensionSubagentProfile` 路由、`route_dev_mode` 字符串资源化 |

---

## 1. `ChatService.kt`

### 1.1 与 GenerationHandler / Subagent 集成（整体）

**做得好的地方**

- `buildSubagentToolsForChat` 传入 `conversationId`，与 `GenerationHandler` 中 `withToolCallId` + `currentToolCallId()` 配套，便于并行 `spawn_subagent` 时按 `toolCallId` 区分进度（`GenerationHandler.kt` L453、`ChatService.kt` L1585/L1694）。
- `mergeSubagentProfiles(custom, global, disabledGlobal)` 与 `SubagentRegistry.resolveProfile(..., globalProfiles)` 一致，和 `PreferencesStore` 将 builtin 迁入 `globalSubagentProfiles` 的方向一致（`SubagentRegistry.kt` L60–70）。
- `onProgress` 仅在 `conversationId != null` 时挂载，避免无会话上下文时无意义写状态。
- `handleMessageComplete` 在 `onFailure` / `onSuccess` 均调用 `cleanupStreamingSubagentMetadata`，与 `SubagentHost` 在 `finally` 中 `progressScope.cancel()` 形成互补（`SubagentHost.kt` L233–235）。

### 1.2 Issues（按严重度）

| 严重度 | Issue | 位置 / 说明 |
|--------|--------|-------------|
| **中** | **双路径更新会话状态：CAS vs 直接赋值** | `updateConversationState` 使用 `compareAndSet`（L1104–1115），但生成主路径仍通过 `updateConversation` 直接 `session.state.value = cleaned`（L1096–1101，且在 L660/L671 的 `GenerationChunk.Messages` 收集处调用）。`SubagentHost` 在 `Dispatchers.IO` 上异步调用 `updateSubagentProgress`（L201–210），可能与主协程上的 `updateConversation` 交错，导致一方覆盖另一方或 CAS 反复重试。 |
| **中** | **流式结束只改 metadata，未同步 JSON `text`** | `updateSubagentProgress` 写入 `partialOutput.text` 含 `"streaming":true`（L1140–1142）；`cleanStaleStreamingMetadata` / `cleanupStreamingSubagentMetadata` 仅把 metadata 中 `subagent_streaming` 设为 `false`（L1190–1194、L1230+），**不**更新 `text` 内 JSON。若 UI 同时读 `text` 与 metadata，结束态可能不一致。 |
| **中** | **`toolCallId == null` 时的匹配过宽** | L1155：`toolCallId == null` 时匹配「最后一条 assistant 上任意符合条件的 `spawn_subagent`」。若 `currentToolCallId()` 偶发为 null 且同轮多个 spawn，进度可能写到错误 tool part。 |
| **低** | **`profileName` 未转义嵌入 JSON 字符串** | L1141：`"{\"profile_name\":\"$profileName\",...}"` — 名称含 `"`、`\` 等会破坏 JSON 或造成展示异常。 |
| **低** | **进度仅绑定「最后一条 assistant」** | L1147–1151：假定正在生成的 assistant 消息始终是 `currentMessages` 中最后一条 assistant。若将来在生成中插入 system/用户消息或分支切换，进度更新会静默失败（`return@mapIndexed message`）。当前主流程下通常成立。 |
| **低** | **CAS 重试上限后静默失败** | L1115：`CAS retry limit exceeded` 仅打日志，`updateSubagentProgress` 内嵌套的 `updateConversationState` 可能丢一次进度快照。 |
| **低** | **`updateSubagentProgress` 与 `cleanStaleStreamingMetadata` 逻辑重复** | 清理 streaming 标志的两套实现（全量 assistant 扫描 vs 仅最后一条），维护成本高，行为需保持完全一致。 |
| **信息** | **`manageSubagentProfile` delete 语义变更** | L1787–1808：`isGlobal` 仅看 `globalSubagentProfiles`；全局名 → `disabledGlobalSubagents`，否则只 `removeSubagentProfile`。与旧版 `disabledBuiltinSubagents` 不同，依赖迁移后 builtin 均在 global 列表中；与 UI/`PreferencesStore` 迁移设计一致，但需在升级场景用测试覆盖（参见任务 `06-28-review-fixes` PRD）。 |
| **信息** | **流式阶段 `isExecuted` 与 output 不同步** | 流式时覆盖 `output` 为 partial，未明确保持 `isExecuted=false`；依赖 `matchesTool` 中 `!part.isExecuted \|\| isStreamingSubagent`（L1153–1154）。需与 `GenerationHandler` 最终写回 tool 结果的路径一致，避免 UI 误判「已完成」。 |

### 1.3 消息处理流程一致性

- 主生成循环：`generationHandler.generateText(...).collect { GenerationChunk.Messages -> updateConversation }`（L666–671）与子代理进度：`updateConversationState { ... updateCurrentMessages }`（L1145–1168）**不是同一套原子更新 API**，这是本次改动里最大的流程一致性问题。
- `updateConversation` 在写入前调用 `cleanStaleStreamingMetadata()`（L1099），可在每次主路径刷消息时把 metadata 的 streaming 标为 false；与仍在进行的 subagent（metadata 仍为 true）可能产生短暂竞态，取决于 collect 与 IO 回调时序。

### 1.4 协程与资源

- `SubagentHost`：`progressScope` 在 `runSubagent` 的 `finally` 中 `cancel()`，避免泄漏；节流 120ms + signature（`SubagentHost.kt` L197–212）减轻 UI 压力。
- `ChatService` 侧 `updateSubagentProgress` 使用 `runCatching`，失败不抛到 spawn 路径，合理。
- **建议关注点（非必改）**：主路径是否应在高频率 `GenerationChunk.Messages` 上也走 CAS，或串行化「生成 collect」与「subagent progress」对同一会话的写操作。

### 1.5 建议（审查意见，非实现）

1. 统一会话写入口：考虑让 `updateConversation` 内部也走 CAS，或生成 collect 改用 `updateConversationState`。
2. 结束流式时同时更新 `text` JSON 的 `streaming` 字段，或 UI 契约明确「仅以 metadata 为准」并文档化。
3. `toolCallId == null` 时记录 warn 并跳过更新，避免误绑。
4. 对 `profileName` 使用 `Json` 编码或 `buildJsonObject` 生成 `text`，避免手工拼接。
5. 为并行 spawn + progress 与 `cleanupStreamingSubagentMetadata` 顺序补充单元/集成测试。

---

## 2. `RouteActivity.kt`

### 2.1 路由正确性

| 路由 | 页面 | 评估 |
|------|------|------|
| `Screen.ExtensionSubagents` | `ExtensionSubagentsPage()` | 与 `ExtensionsPage` `navigate(Screen.ExtensionSubagents)` 一致（`ExtensionsPage.kt` L85）。 |
| `Screen.ExtensionSubagentProfile(profileName, createMode)` | `ExtensionSubagentProfilePage(...)` | 与列表页 `navigate(Screen.ExtensionSubagentProfile(...))` 一致；助手侧 `AssistantSubagentPage` 亦跳转同一 `ExtensionSubagentProfile` 编辑全局配置（L89），双入口设计清晰。 |
| 既有 `AssistantSubagent` / `AssistantSubagentProfile` | 未改动行为 | 助手级子代理配置与扩展级全局配置路由并存，符合产品拆分。 |

`Screen` 均为 `@Serializable`，`ExtensionSubagentProfile` 带参序列化与 `AssistantSubagentProfile` 模式一致，利于深链接/状态恢复（需与 Navigation 3 序列化注册一致，本次 diff 仅增类型与 entry）。

### 2.2 Issues

| 严重度 | Issue | 说明 |
|--------|--------|------|
| **低** | **import 顺序** | `import me.rerere.rikkahub.R` 插在 UI pages import 中间（diff L83），不符合常见「android → third → project」分组，可整理。 |
| **信息** | **纯空白/格式 diff** | `QuickMessages` entry 仅尾随空白变化，无功能影响。 |
| **无** | **Activity 生命周期** | 改动仅为 `NavDisplay` 的 `entry<>` 与 `Screen` 密封类型扩展，未新增 `remember` 泄漏、未改 `onCreate`/`onDestroy` 协程作用域；**无新增生命周期风险**。 |

### 2.3 国际化

- `[开发模式]` → `stringResource(R.string.route_dev_mode)`：`values` / `values-zh` 等已存在 `route_dev_mode`，改动合理。

---

## 3. 跨文件一致性检查

| 检查项 | 结果 |
|--------|------|
| 全局子代理在 Chat 与 Registry | ✅ 使用 `settings.globalSubagentProfiles` + `disabledGlobalSubagents` |
| 扩展 UI 路由与 ChatService | ✅ Chat 不依赖新 Screen；配置经 `Settings` / store |
| 流式 UI 数据契约 | ⚠️ metadata `subagent_transcript` / `subagent_streaming` 与 partial `text` 需在 UI 层确认单一数据源 |
| Web/API `ChatService` | `conversationId` 在聊天生成路径有值；其他调用方若 `conversationId==null` 则无流式写回（符合预期） |

---

## 4. 总结

- **RouteActivity**：改动小、路由与现有导航一致，生命周期无新增问题；仅风格与空白 diff。
- **ChatService**：功能方向正确（全局 profile、toolCallId、流式 transcript、CAS），但 **生成主路径与子代理进度路径的会话更新机制不统一** 是提交前应重点确认或收敛的一点；流式结束态 metadata/text 一致性与 `toolCallId` 回退策略建议跟进。

## Caveats / Not Found

- 未审查 `GenerationHandler.kt` / `SubagentHost.kt` 是否在同一次未提交改动中修改（本 diff 仅上述两文件）；`currentToolCallId` 行为以当前仓库 `GenerationHandler.kt` 为准。
- 未运行仪器测试验证并行 `spawn_subagent` 进度 UI。