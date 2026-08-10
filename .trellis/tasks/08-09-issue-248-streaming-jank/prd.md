# PRD: bug(#248) 长思考流式输出卡顿

## Goal

长 reasoning 流式输出（约 300s / ~40 tps）期间，聊天页滚动、输入、动画保持可用；思考块展开态不因每个 token 被重置。

## Background

Issue #248：长思考持续流式时 UI 明显掉帧，内容越长越严重。Research 已验证热路径（`research/streaming-jank-hot-path.md`）与排序修复候选（`research/ranked-fix-candidates.md`）。

权威来源：issue #248 正文 + 本任务 research。实现顺序固定为 **A → C → D-lite → B**（本轮不做 D-full / StringBuilder / AppScope 迁移）。

## Requirements

### 功能

1. **A — Reasoning 效果键修复**：`ChatMessageReasoning` 不得以完整 `reasoning.reasoning` 文本作为 `LaunchedEffect` key；保留 loading 时 Preview 自动展开、结束后 autoClose、滚动跟随。
2. **C — 门控 `checkFilesDelete`**：纯流式文本/reasoning 增长更新不得每 token 全量扫描 `Conversation.files`；附件删除场景仍须 GC 文件。
3. **D-lite — 流式期间关闭尺寸动画**：assistant 气泡与 CoT 在 `loading` 时跳过 `animateContentSize`。
4. **B — UI 发布合并**：Messages 流对 Conversation 的 UI 发布按 ~50–100ms 合并，始终保留最新快照与最终 chunk；不得丢字（#1295 类）。

### 约束

- 不改 Provider `buffer(UNLIMITED)`（#1295 防丢字）。
- 不迁移 `AppScope` 到非 Main（本轮非目标）。
- 不改 tool-call / 审批边界的即时 flush 语义：合并不得拖延审批 UI 到不可用。
- 不引入可感知的“缺字/跳字”；最终 token 与 finished reasoning 必须绘制。

## Non-goals

- Markdown debounce / 流式纯文本渲染（D-full）
- `Message.kt` StringBuilder 增量拼接（5a）
- `RikkaHubApp` AppScope dispatcher 大迁移（5e）
- Provider 背压 redesign

## Acceptance Criteria

- [ ] AC1: ~40 tps 长 reasoning 期间可滚动列表、打开 IME，无多秒级主线程冻结
- [ ] AC2: Reasoning Preview 展开态不每 token 闪烁；用户手动 Expanded 不被每 token 重置
- [ ] AC3: 流式结束后最终字符完整，无 #1295 类中间缺字
- [ ] AC4: 会话中删除图片/文档附件后文件仍被清理（C 门控后）
- [ ] AC5: B/D 节流后 final token 与 finished reasoning 一定绘制
- [ ] AC6: 实现顺序落地 A → C → D-lite → B；每步可独立验证

## Out of scope notes

本 PRD 不规定具体 API；设计见 `design.md`，执行清单见 `implement.md`。
