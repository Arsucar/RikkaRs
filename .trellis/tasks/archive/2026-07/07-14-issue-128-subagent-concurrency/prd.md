# 修复 Issue 128 子代理并发开关

## Goal

让同轮多个 `spawn_subagent` 按 `subagentMaxConcurrent` 并行，即使普通工具并行开关关闭，同时保持普通工具严格串行。

## Requirements

1. 执行计划区分 spawn 调用与普通工具调用；`parallelToolExecution=false` 时仅连续的多 spawn 分组并发，组间及普通工具保持原顺序。
2. spawn 并发始终受 semaphore/maxConcurrent 限制，混合调用保持确定性且不会让普通工具意外并行。
3. UI 文案明确普通工具并行与子代理最大并发是独立语义。
4. 不回归 #46 对普通工具关闭并行开关的行为。
5. spawn 工具描述的并行提示由子代理启用状态与 maxConcurrent>1 决定，不再错误依赖普通工具开关。

## Acceptance Criteria

- [ ] 关闭普通工具并行后，同轮 ≥2 spawn 并发且峰值不超过配置。
- [ ] 普通工具仍串行；混合调用顺序/并发策略有测试。
- [ ] 单 spawn、禁用子代理、maxConcurrent=1 行为不变。

## Notes

- 采用 issue 建议中的“恢复多 spawn 特例并文档化”方案。
