# 支持草稿回复附加用户意图

## Goal

让“写回复草稿”按钮把输入框中已有文本作为本次回复的附加意图，生成更符合用户要求的草稿。

## Requirements

- 触发时读取并 trim 输入文本，先保存未修改的原文用于恢复，再清空输入框并开始原有流式输出。
- 服务提示词在意图非空时增加一个 `<user_instruction>` 块和明确的遵循指令；空白意图不增加任何块。
- 保持按钮图标/入口、回复目标限制、ASR 互斥、模型/上下文校验、流式合并和不持久化会话的既有行为。
- 取消、异常和用户/ASR 中途编辑继续遵循现有 generation guard 与恢复规则。

## Acceptance Criteria

- [x] 输入 `  委婉拒绝  ` 时服务收到的提示词只包含 `委婉拒绝`，且块出现一次。
- [x] 空或全空白输入的提示词不含 `<user_instruction>` 或残留占位符，默认上下文规则不变。
- [x] 生成开始立即清空输入框；取消/失败在未编辑时恢复原始（含空白）文本。
- [x] 用户或 ASR 编辑后，晚到流式片段不会覆盖或恢复该编辑。
- [x] 新增纯提示词单元测试并通过 app 编译/相关测试。

## Verification

`app:testDebugUnitTest` and `app:compileDebugKotlin` passed with `--no-daemon`; prompt tests cover trim, one block, and blank omission.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
