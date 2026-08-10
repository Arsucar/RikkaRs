# Design: bug(#248) 长思考流式卡顿修复

## 边界

| In | Out |
|---|---|
| Chat UI reasoning 效果 / CoT 动画 | Provider buffer / #1295 redesign |
| ChatService `updateConversationState` 旁路 GC | AppScope dispatcher 全局迁移 |
| Messages → Conversation 的 UI 合并 | Markdown debounce（D-full） |
| | Message 字符串 rope（5a） |

## 数据流（现状 → 目标）

```text
SSE → GenerationHandler (full fidelity messages, 每 chunk)
    → ChatService.collect Messages
         ├─ [B] UI coalesce (~50–100ms, latest + final flush)
         └─ updateConversationState
              ├─ ConversationSession.state
              └─ [C] checkFilesDelete 仅结构/附件变更时
    → ChatPage collect Conversation
         → ChatMessageReasoning [A] 稳定 effect keys
         → ChatMessage / ChainOfThought [D-lite] loading 无 animateContentSize
```

Handler 内 `messages` 保持全量；只对 **UI 可见的 Conversation 发布** 合并。

## 方案细节

### A — `ChatMessageReasoning` LaunchedEffect

**文件**: `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageReasoning.kt:96-109`

| 现状 | 目标 |
|---|---|
| `LaunchedEffect(reasoning.reasoning, loading)` | 键改为 `reasoning.createdAt` + `loading`（或长度 bucket，禁止全文） |
| 每 token 重启 `animateScrollTo` + 可能重写 expand | 展开逻辑仅在 loading 边沿变化；滚动用 `snapshotFlow { scrollState.maxValue }` 或 length-only 轻量 effect |

保留行为：

- loading + `showThinkingContent` → Preview
- finished + `autoCloseThinking` → Collapsed
- loading 时 scroll-follow
- `ReasoningState` 已 `remember(reasoning.createdAt)` — 保持

Duration ticker `:111-117` 仅 key `loading` — 不动或仅调 delay。

### C — 门控 `checkFilesDelete`

**文件**:

- `app/.../service/ChatService.kt:2558-2561`（调用点）、`:2780-2789`（实现）
- 可选 `app/.../data/model/Conversation.kt:49-53`（`files` getter，仅当需要廉价比较）

策略（择一或组合，优先小改）：

1. 流式 message-text-only 更新跳过 `checkFilesDelete`
2. 仅在 `saveConversation` / 非流式 commit / 附件结构变更时调用
3. 结构相等：`messageNodes` 的文件 part 集合未变则 skip

纯 reasoning 增长永不删文件 → 对该路径门控安全。

### D-lite — 禁用 loading 时 `animateContentSize`

**文件**:

- `app/.../ui/components/message/ChatMessage.kt:473,496,521,528`
- `app/.../ui/components/ui/ChainOfThought.kt:94-96`

当 `loading == true`（或 step loading）不应用 `animateContentSize`；结束后恢复。

### B — UI 状态合并

**文件**:

- 发布侧 `ChatService.kt:975-1003`（`collect` Messages → `updateConversationState`）
- 参考 emit `GenerationHandler.kt:228-239`；session `ConversationSession.kt:128-136`；消费 `ChatPage.kt:136`

实现偏好：

1. 在 `updateConversationState` 前对 Messages 做 `sample(50–100ms)` / 时间 coalesce，**始终应用最新 chunk**
2. 流结束 / 非 Messages 控制事件 / tool-call 边界 **立即 flush**
3. 通知 `tryEmit` 可保持较低频率
4. **禁止**在 `ChatCompletionsAPI.buffer(UNLIMITED)` 上 conflate

## 兼容与风险

| 风险 | 缓解 |
|---|---|
| A 丢 scroll-follow | 独立 scroll effect，不绑全文 key |
| C 泄漏聊天文件 | 附件 part 变更路径强制 GC；单测/手动删图验证 |
| B 丢 final / 审批延迟 | final + tool 边界强制 flush；合并仅 UI |
| B 与 #1295 | 不改 provider buffer |

## 验证信号

见 ranked-fix-candidates「Acceptance signals」与 prd AC1–AC6。

## 回滚

每步独立 commit 友好：A/C/D-lite/B 可单独 revert；优先回滚 B（行为面最大），A 最小。
