# 优化 Issue 与 PR 修复

## Goal

修复对 issue #170、#169 和 #172 相关提交的审查发现，使记忆表并发写入、回复草稿交互、更新卡片滚动和 Trellis UI 工作流具有一致、可验证的行为。

## Confirmed Facts

- 本地提交 `89ed11ea` 包含 #170 的记忆表 CAS 写入，`729972f8` 包含 UI 修改指南。
- 远端 `origin/release/rikka-arsucar` 已合并 PR #171（issue #169）与 PR #173（issue #172），当前本地分支尚未包含这些提交。
- 当前未提交的三个 `.agents/skills/trellis-*` 文件属于 UI 指南接线，必须保留并与流程修复一起处理。
- PR #173 的滚动实现静态正确，剩余风险是缺少编译、设备和交互验证。

## Requirements

- 合并远端分支并保留所有本地提交及未提交 skill 修改。
- 回复草稿不得在编辑历史消息时启用或覆盖历史消息。
- 回复草稿收到空白或仅 reasoning 的正常完成结果时必须恢复原输入，并提供失败反馈。
- ASR 和回复草稿必须双向互斥；取消草稿仍应始终可达。
- `memory_table_tool` 的描述、参数语义和响应 JSON 必须一致：使用 `resolved_row_keys`，区分模板与文档元数据，并拒绝创建时无意义的 `expected_revision`。
- Trellis UI 指南仅在存在 Trellis task 时要求 PRD/design 记录；无 task 的轻量 UI 修改仍需执行检查矩阵并在交付说明中记录证据。
- 新增回归测试覆盖上述行为和真实 Repository CAS 路径；不以 mock callback 代替事务行为验证。
- 保持 PR #173 的滚动修复，并通过最终编译和设备安装流程验证，不引入无关 UI 重构。

## Acceptance Criteria

- [x] 编辑消息状态下回复草稿按钮禁用，普通聊天状态仍可生成和取消草稿。
- [x] 空白草稿完成不会丢失原输入，用户能看到生成失败反馈。
- [x] ASR 录音、连接或停止期间不能启动草稿；草稿生成期间不能启动 ASR。
- [x] `list_templates` 返回兼容的 `id` 和明确的 `template_id`，并返回复数 `resolved_row_keys`；工具说明不再宣称模板响应含文档 revision。
- [x] 无 `document_id` 的创建请求携带 `expected_revision` 时被清晰拒绝且不写入。
- [x] 无 Trellis task 的轻量 UI 修改不会因缺少 PRD/design 而无法通过 `trellis-check`。
- [x] 聚焦 JVM 测试覆盖草稿状态策略、工具响应/验证和真实 CAS 的成功、冲突、revision/snapshot 行为。
- [x] `git diff --check`、资源处理、Kotlin 编译和聚焦测试通过。
- [x] 按仓库流程安装 Debug 包到可用设备；若设备不可用，按规定构建并如实报告替代交付。

## Out Of Scope

- 改写 PR #173 已静态确认正确的卡片布局结构。
- 改变回复草稿的模型选择、提示词内容或对话上下文窗口。
- 改变记忆表 CAS 的 last-write-wins 兼容默认值。

## Open Questions

- 无。用户已要求全部优化，并批准创建任务及合并远端分支。

## Verification Evidence

- 聚焦 JVM：`MemoryTableToolsTest` 49、`MemoryTableRepositoryTest` 38、`InputDraftPolicyTest` 3，全部零失败。
- `:app:processDebugResources` 与 `:app:compileDebugKotlin` 产物已生成；`git diff --check` 通过。
- `:app:installDebug` 在设备 `ebc3de22`（PJF110 / Android 16）成功，包名 `me.arsucar.rikka.debug`。
- 设备处于安全 PIN 锁屏；未执行或宣称更新卡片滚动、草稿按钮、ASR 的视觉/手势验证。
