# 处理所有开放 GitHub Issues

## Goal

系统处理当前仓库所有未关闭 GitHub Issues：确认每个 Issue 的真实状态，完成仍需处理的实现或交付动作，并以可核验的证据关闭已解决 Issue。

## Requirements

- 盘点当前仓库全部未关闭 GitHub Issues，包括标签、依赖、重复关系、关联提交和发布状态。
- 对已在代码中解决但尚未关闭的 Issue，补齐验证、定位和中英文交付评论后关闭。
- 对仍需实现的 Issue，按可独立验收的交付物拆成 Trellis 子任务，完成规划、实现、审查、验证、提交和推送。
- app 模块功能改动按仓库规范优先执行真机安装验收；无可用设备时如实降级为编译验证。
- Issue 关闭评论必须逐条对应实际实现，包含目标分支、修复提交、正式版本（若已发布）、验证结果、定位和已知边界，并分别发布中文与英文版本。
- 开发目标分支为 `release/rikka-arsucar`，提交推送到用户的 `origin`，不得向上游创建 Pull Request。

## Acceptance Criteria

- [ ] 所有规划时处于开放状态的 Issues 均被分类为：已解决待关闭、需实现、重复、无法复现/信息不足或明确保留。
- [ ] 每个需实现的 Issue 都有独立且可测试的子任务与验收标准。
- [ ] 所有可在本任务范围内解决的 Issues 已完成代码或文档交付，并通过与风险相称的编译、测试、lint 或安装验证。
- [ ] 所有已解决 Issues 均已发布事实一致的中英文交付评论，重新读取确认后关闭。
- [ ] 未关闭的 Issues 均记录清晰的阻塞原因、所需外部输入或保留理由，不以虚假成功状态收尾。
- [ ] 相关变更已提交并推送至 `origin/release/rikka-arsucar`，且未包含本地代理配置或敏感文件。

## Confirmed Facts

- 当前分支为 `release/rikka-arsucar`，工作区在任务创建前为 clean。
- 本任务是复杂的父任务；实际交付应拆成可独立规划和验收的子任务。
- 用户已明确同意创建 Trellis 任务并进入规划阶段。
- 2026-07-18 实时盘点 `Arsucar/RikkaRs` 后开放 Issue 为：
  - [#144](https://github.com/Arsucar/RikkaRs/issues/144) Room/协程事务完成时 512 MiB Java heap OOM，触发场景与真实增长对象未知。
  - [#145](https://github.com/Arsucar/RikkaRs/issues/145) 记忆表模板 GLOBAL/ASSISTANT scope 显式迁移及 tool/UI 支持。
- #136、#137、#140、#141 已完成并由 v2.3.31 正式发布；#138、#139 以 not planned 关闭。
- 原两个子任务已完成并归档：
  - `07-16-issue-136-remove-conversation-archive`
  - `07-16-issue-137-independent-memory-toggles`
- 新增两个子任务：
  - `07-18-issue-144-room-transaction-oom`
  - `07-18-issue-145-memory-template-scope-migration`
- #145 需求完整且可独立实现；#144 缺少复现/heap 证据，先交付可证明的内存峰值降低和诊断，证据不足时保持开放并发布真实边界。

## Out of Scope

- 向上游仓库创建 Pull Request。
- 在证据不足时擅自关闭无法复现或仍需产品决策的 Issue。

## Open Questions

- 无。用户已明确同意创建 Trellis 任务、持续处理且不要求中途决策确认。
