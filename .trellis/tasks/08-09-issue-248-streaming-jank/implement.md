# Implement: bug(#248) 长思考流式卡顿

## 前置

- Research: `research/streaming-jank-hot-path.md`, `research/ranked-fix-candidates.md`
- 顺序 **强制**: A → C → D-lite → B
- 编译：仅最终检查子代理可跑 Gradle，且必须 `--no-daemon`

## Checklist

### Step 1 — A: Reasoning LaunchedEffect key

- [ ] 读 `ChatMessageReasoning.kt:88-117`
- [ ] 将 expand/autoClose 逻辑的 `LaunchedEffect` key 从 `reasoning.reasoning` 改为 `reasoning.createdAt` + `loading`（或等价稳定键）
- [ ] 滚动跟随改为不 cancel/restart expand 的路径（`snapshotFlow { maxValue }` 或 length-only）
- [ ] 确认用户 Expanded 在 token 增长时不被重置
- [ ] 手动：短流 + 长流 Preview/Collapsed/手动展开

**验证**: 日志/断点确认 effect 不每 token 重启；展开态稳定。

### Step 2 — C: gate `checkFilesDelete`

- [ ] 读 `ChatService.kt:2558-2561`, `:2780-2789`, `Conversation.kt:49-53`
- [ ] 在 `updateConversationState` 对 stream-only 文本更新跳过 `checkFilesDelete`，或结构未变 skip
- [ ] 确保 `saveConversation` / 附件移除路径仍调用
- [ ] 手动：流式 reasoning 中 CPU；删图后文件 GC

**验证**: 纯 reasoning 更新不再双 walk `files`；删附件仍删文件。

### Step 3 — D-lite: animateContentSize while loading

- [ ] `ChatMessage.kt` 气泡：loading 时不挂 `animateContentSize`
- [ ] `ChainOfThought.kt:94-96`：step/streaming loading 时跳过
- [ ] 结束后动画可恢复（非必须连续动画）

**验证**: 流式时布局无 content-size 动画抖动。

### Step 4 — B: coalesce UI Conversation publishes

- [ ] `ChatService.kt:975-1003`：Messages → `updateConversationState` 前 ~50–100ms coalesce
- [ ] 始终保留 latest；stream end / tool-call / 审批相关 **立即 flush**
- [ ] 不改 `ChatCompletionsAPI` UNLIMITED buffer
- [ ] 不在 handler 内丢弃 messages 全量

**验证**: 长流无缺字；结束后最终文本完整；审批卡仍及时。

### Step 5 — 聚焦验证（合并一次 Gradle）

- [ ] `.\gradlew --no-daemon :app:compileDebugKotlin`（或相关模块 compile）
- [ ] 有设备时最终 `.\gradlew --no-daemon :app:installDebug`（生产代码冻结后）
- [ ] 对照 prd AC1–AC6 勾选

## 编辑目标速查

| Pri | File | Lines | Action |
|---|---|---|---|
| P0 | `.../ChatMessageReasoning.kt` | 96-109 | A keys + split scroll |
| P0 | `.../ChatService.kt` | 2558-2561, 2780-2789 | C gate |
| P1 | `.../ChatMessage.kt` | 473,496,521,528 | D-lite |
| P1 | `.../ChainOfThought.kt` | 94-96 | D-lite |
| P1 | `.../ChatService.kt` | 975-1003 | B coalesce |

## 不做

- Markdown.kt / MarkdownNew.kt debounce（D-full）
- Message.kt String + → Builder
- RikkaHubApp AppScope
- Provider buffer

## Rollback points

1. A only revert
2. C only revert
3. D-lite only revert
4. B only revert（优先若出现丢字/审批延迟）
