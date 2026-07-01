# Subagent steps countdown reminder

## Goal

在子代理工具循环的最后几步追加倒计时提醒，让 AI 意识到剩余步数有限，集中完成核心任务而非继续探索。

## Background

- `GenerationHandler.generateText()` 的工具循环已有 `stepIndex` / `maxSteps`，最后一步（`isLastStep`）已通过 `MAX_STEPS_PROMPT` 强制禁用工具并要求文本总结。
- 但在非最后一步时，AI 完全不知道还剩多少步，容易在无关操作上浪费步数。
- 子代理步数通常 20-64，步数更紧张，更需要倒计时提醒。
- 主代理 maxSteps=256，倒计时意义不大，但统一机制不影响。

## Requirements

### REQ-1: 最后 N 步倒计时提醒
- 在 `GenerationHandler.generateText()` 的工具循环中，当剩余步数 `maxSteps - stepIndex <= threshold` 且**非最后一步**时，在调用 `generateInternal()` 前追加一条 user 消息，告知 AI 剩余步数并提醒集中核心任务。
- threshold = `min(5, maxSteps / 2)`，取整（整数除法），至少为 1。
- 最后一步仍使用现有的 `MAX_STEPS_PROMPT`（禁工具+强制总结），不与此提醒冲突。

### REQ-2: 提醒消息格式
- 消息内容简洁明确，如：`"[Steps remaining: {remaining}/{maxSteps}] Focus on completing the core task. Avoid further exploration."`
- 不添加配置项，不暴露 UI 开关。

### REQ-3: 兼容性
- 不影响主代理（主代理 maxSteps=256，threshold=min(5,128)=5，只在最后 5 步提醒，合理）。
- 不改变现有 `MAX_STEPS_PROMPT` 行为。
- 不改变 `SubagentHost.buildFallbackSummary()` 行为。
- 提醒消息不参与上下文窗口限制计算（与 `MAX_STEPS_PROMPT` 一致，作为临时注入消息）。

## Acceptance Criteria

- [ ] 工具循环在剩余步数 ≤ threshold 时（且非最后一步），追加倒计时提醒消息
- [ ] threshold = min(5, maxSteps / 2)，整数除法
- [ ] 提醒消息格式包含剩余步数和总步数
- [ ] 最后一步仍使用 MAX_STEPS_PROMPT 禁工具强制总结
- [ ] 编译通过并安装到设备

## Out of Scope

- UI 可配置开关或 threshold 调节
- 主代理特殊逻辑
- 改变 MAX_STEPS_PROMPT 内容或行为
