# fix: 切换会话再切回时生成中消息内容被 DB 覆盖丢失

## Goal

修复同进程内"会话1 流式生成中 → 切到会话2 → 切回会话1"时，会话1 正在生成的消息内容（流式文本、子代理 transcript）被 DB 旧快照覆盖、UI 退化为"空消息 + 兔子 loading"的问题。

范围限定为**热切换覆盖**（同进程内 ConversationSession 仍存活、`generationJob` 仍在跑的场景）。不含冷启动/进程被杀后的内容恢复（属独立议题）。

## Background / Root Cause

- 流式 chunk（含子代理 transcript）只写内存 `ConversationSession.state`，生成成功结束才 `saveConversation` 落盘（`ChatService.kt:741-744` vs `764`）。
- 每个会话 ID 绑定一个 `ChatVM`（`ChatPage.kt:102-106`），切走会 `onCleared`，切回会**新建 ChatVM** 并再次 `initializeConversation`（`ChatVM.kt:79-86`）。
- `initializeConversation`（`ChatService.kt:393-401`）从 DB 读快照 → `hydrateConversationFromDb` → `updateConversation` → `commitConversationState`（`1174-1181`）**全量替换** `session.state`。
- `hydrateConversationFromDb`（`1263-1266`）只在 `!isGenerating` 时清 stale metadata；**即便 `isGenerating=true`，仍然让 DB 全量覆盖内存** → 流式内容丢失。
- `commitConversationState` 没有"生成中跳过覆盖"的 guard。
- `generationJob` 不受切换影响（`ChatVM.onCleared` 只 `removeConversationReference`，`ChatService.kt:325-336` 在 in-use 时不拆 session），所以切回后 `conversationJob != null` → 列表底部 `RabbitLoadingIndicator` 仍在，配合空消息形成"坏数据样"。

## Requirements

### R1 `initializeConversation` 内部 early return（对所有调用方生效）
- `initializeConversation`（`ChatService.kt:393`）在 `getOrCreateSession` 之后、读 DB 之前，加 guard：若 `session.isGenerating == true`，**立即 return**，不读 DB、不 `hydrateConversationFromDb`、不 `updateConversation`、不 `saveConversation`、**不 `settingsStore.updateAssistant`**。
- 判定来源：`ConversationSession.isGenerating`（`ConversationSession.kt:39`，public 计算属性，`_generationJob.value?.isActive == true`）。
- guard 加在函数内部而非调用方，覆盖所有 7 处调用点：`ChatVM.init`（`ChatVM.kt:85`）+ 6 处 web 路由（`ConversationRoutes.kt:224/271/290/302/313/325/366`）。
- 理由：
  - in-use session 不会被 `removeSession`（`ChatService.kt:325-336`），内存 `session.state` 始终持有最新流式内容，无需 DB 补充。
  - "生成中不要用 DB 覆盖内存 state" 是对所有调用方都成立的正确性约束。
  - web 路由调用 `initializeConversation` 仅用于"确保 session 存在 + state 最新"，生成中跳过 hydrate 不破坏它们后续的 `updateConversationState` / `saveConversation`。
  - 跳过 `updateAssistant`：生成中会话的 assistant 不会变（正在用它跑），跳过是安全的；避免切回瞬间 UI 因 assistant 设置刷新而闪烁。
- `hydrateConversationFromDb`（`1263-1266`）的 stale 清理语义保持不变（仅在 `!isGenerating` 路径执行）。
- 兜底：若极端情况下 `isGenerating=true` 但 `session.state` 为空（不应发生），接受"空白"——不为此引入 merge 复杂度。

### R2 切回后 UI 能继续看到流式内容
- 切回会话1 后，正在生成的 assistant 消息文本 / reasoning / 子代理 transcript 与切走前一致（来自内存 `session.state`）。
- 后续 chunk 继续正常 append，UI 实时刷新。

### R3 切走→切回不影响生成 Job
- 不引入"切走即 cancel 生成"的副作用（保持现状：生成在后台继续）。

### R4 非生成中会话的 hydrate 行为不变
- 首次进入、或会话不在生成时，`initializeConversation` 仍按现状从 DB 加载并清理 stale（`c002b2cb` 引入的逻辑保持）。

## Acceptance Criteria

- [ ] **AC1**：会话1 流式生成中（含子代理运行）→ 切到会话2 → 切回会话1，最后一条 assistant 消息内容（文本/reasoning/tool 含子代理 transcript）与切走时一致，不出现"空消息 + 仅 loading"。`initializeConversation` 对 `isGenerating=true` 的 session early return，不读 DB / 不覆盖 state。
- [ ] **AC2**：切回后若生成仍在继续，后续新 chunk 能正常 append 到内存 state 并刷新到 UI。
- [ ] **AC3**：切走→切回不取消 `generationJob`；切回后 `conversationJob` 状态与切走时一致。
- [ ] **AC4**：非生成中会话（首次进入、或已结束的会话）`initializeConversation` 行为与修复前一致（DB 加载 + stale 清理），回归用例通过。
- [ ] **AC5**：新增纯函数单测，覆盖 guard + hydrate 决策（零 mock，符合仓库现有测试风格，不引入新依赖）：
  - **AC5a**：`ChatService.initializeConversation` 抽出 guard 纯函数（如 `private fun shouldSkipInitializeOnGenerating(session: ConversationSession): Boolean = session.isGenerating`，命名可调），`initializeConversation` 调用它决定 early return。新增该纯函数的单测：构造 `ConversationSession`（用真实 `CoroutineScope` + `Job` 控制 `isGenerating`），断言生成中返回 true / 非生成中返回 false。
    - 注：`ConversationSession` 构造参数为 `(id, initial, scope, onIdle)`，可在测试里直接 new；`isGenerating` 通过 `setJob(job)` 控制。
  - **AC5b**：为已有的 `hydrateConversationFromDb`（`ChatService.kt:1263-1266`）补单测（此前无单测）：
    - `isGenerating=true` → 返回 loaded 原样（不 cleanStale）。
    - `isGenerating=false` → 返回 `loaded.cleanStaleStreamingMetadata()`。
    - 复用 `SubagentStreamingConsistencyTest` 的构造方式（含 `subagent_streaming=true` metadata 的 Conversation）。
  - AC5a + AC5b 组合语义等价于"生成中 initializeConversation 不覆盖 state"。
  - 端到端保护由 AC1-AC4 手动验收兜底（当前 `ChatService` 15 个 final class 依赖 + `ProcessLifecycleOwner` init 副作用，零 mock 下无法构造完整实例测 `initializeConversation` 整体）。
- [ ] **AC6**：`.\gradlew :app:compileDebugKotlin --no-daemon` 与 `.\gradlew :app:testDebugUnitTest --no-daemon --tests "*InitializeGuardTest*" --tests "*HydrateConversation*"` 通过（含新增的 guard 纯函数测试与 hydrate 补测）。

## Out of Scope

- 冷启动 / 进程被杀后的流式内容恢复（流式期间周期性 checkpoint 落盘）——独立议题。
- "空消息 + loading"的防御性 UI 占位——R1 修好后不应再出现，不做额外 UI 改动。
- 会话切换的导航栈 / `ChatVM` 一一绑定机制的重构。

## Notes

- 关键代码点：`ChatService.kt:393-401`（initializeConversation）、`1263-1266`（hydrate）、`1174-1181`（commitConversationState）。
- `isGenerating` 判定见 `ConversationSession.kt:40`（`isInUse` 含 `isGenerating`）。
- 风险点：`initializeConversation` 在 `isGenerating` 时还承担"会话首次创建后写入初始 state"等路径，需确认 guard 不误伤首次进入。
