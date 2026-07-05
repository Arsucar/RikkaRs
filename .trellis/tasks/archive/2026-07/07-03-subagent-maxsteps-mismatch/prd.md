# fix subagent max_steps mismatch and false max-steps summary

## Goal

修复 GitHub issue #32：子代理 `max_steps` 配置与运行行为不一致，且总结常误报"已达最大步数"。涉及 4 个相互关联的子问题：配置合并不继承、返回 JSON 口径缺失、fallback 文案误报、流式/最终 `subagent_steps` 语义不一致。

## Background

- 子代理 `maxSteps` 默认 32，内置 coder=64/explore=48/reviewer=24
- `mergeInheritedFrom()` 只继承 displayName/description/systemPrompt，不继承 maxSteps
- 稀疏本地副本（只改 prompt）运行时仍用默认 32，UI 可能显示合并后的 64
- `SubagentResult.truncated` 仅表示 `maxToolCalls` 预算用尽，不表示 `maxSteps` 用尽
- `buildFallbackSummary()` 在 summary 为空时无条件输出"Max steps reached"
- `subagent_steps` 流式=循环步数，最终=续写段数，UI fallback 会跳变

## Requirements

### R1: maxSteps 继承
- `SubagentProfile.maxSteps` 改为可空 `Int? = null`，null 表示"未设置，继承 base"
- `mergeInheritedFrom()` 对 maxSteps 做 `?: base.maxSteps` 合并
- 运行时使用处对 null 做兜底默认（如 `?: 32`）
- 内置 profile 显式值不变（64/48/24），全局 DataStore profile 显式值不变
- 向后兼容：旧 DataStore 无 maxSteps 字段的 profile 反序列化为 null → 继承 base

### R2: 返回 JSON 对齐
- slimPayload 新增 `max_steps`（生效上限）和 `truncated`（是否因预算上限截断）
- `truncated` 语义扩展为：因 `maxSteps` 或 `maxToolCalls` 任一预算用尽而截断
- finalMetadata 新增 `subagent_max_steps`、`subagent_truncated`（供 UI）
- 保留 `steps`（续写段数）和 `tool_loop_steps`（循环步数），父模型有了完整口径

### R3: maxSteps 用尽信号 + fallback 文案
- `runToCompletion` 能判断本次 run 是否跑满 maxSteps（比较 assistant 增量与 effective maxSteps）
- `RunCompletion` 新增 `maxStepsReached: Boolean`
- `SubagentResult.truncated` 汇总时 `= toolBudgetTruncated || maxStepsReached`
- `buildFallbackSummary(transcript, maxStepsReached)`：仅 maxStepsReached=true 时用"Max steps reached"文案，否则用中性文案"子代理未产出文本总结，由 transcript 生成"
- 空 transcript 的"ran out of steps"文案同理改为中性

### R4: subagent_steps 语义统一
- 最终结果 `subagent_steps` 从 `result.steps`（续写段数）改为 `result.toolLoopSteps`（循环步数）
- 流式与最终 `subagent_steps` 语义一致（均为循环步数）
- UI fallback 不再跳变

## Acceptance Criteria

- [ ] 助手仅存稀疏 coder 副本（maxSteps=null）时，运行时 maxSteps 生效为 64（继承 builtin），与 UI 显示一致
- [ ] 助手显式设 maxSteps=50 时，运行时生效 50，不被继承覆盖
- [ ] slimPayload 含 `max_steps`、`tool_loop_steps`、`truncated` 三个字段
- [ ] 子代理未跑满 maxSteps 且 summary 为空时，fallback 文案不含"Max steps reached"
- [ ] 子代理跑满 maxSteps 时，fallback 文案含"Max steps reached"，且 truncated=true
- [ ] `subagent_steps` 在流式与最终均为循环步数，UI 标题结束时无跳变
- [ ] 编译通过（`./gradlew :app:compileDebugKotlin --no-daemon`）

## Constraints

- 不破坏 SubagentProfile JSON 序列化向后兼容
- 不改变内置 profile 的 maxSteps 值
- 不改 `SubagentResult.steps`（续写段数）字段的现有语义，slimPayload 不再输出它，finalMetadata `subagent_steps` 改用 toolLoopSteps
- 不改 GenerationHandler.generateText 的返回类型（Flow<GenerationChunk>）

## Notes

- 参考 research 文件：
  - `.trellis/tasks/07-03-subagent-maxsteps-mismatch/research/github-issue-32-subagent-maxsteps.md`
  - `.trellis/tasks/07-03-subagent-maxsteps-mismatch/research/generation-handler-maxsteps-stop-signal.md`
- issue: https://github.com/Arsucar/rikkahub/issues/32
