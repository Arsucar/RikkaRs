# 处理开放 GitHub issues #178 和 #177

## Goal

处理当前仓库中全部开放 GitHub issue（#178、#177），修复用户可见行为并保留现有兼容性。实现完成后不提交、不推送，留下可审查的工作区改动。

## Requirements

- #178：Git 状态与差异读取必须使用会话有效工作目录，支持 `conversation.workspaceCwd > assistant.defaultWorkspaceCwd > /workspace`，嵌套仓库能正确显示状态和 diff。
- #177：草稿回复按钮触发时捕获输入框文本；将去首尾空白后的文本作为可选用户意图传入服务提示词，空文本保持原默认行为。
- 保留草稿流式输出、取消/失败恢复、用户或 ASR 编辑优先级、模型校验和会话持久化边界。
- 保留未绑定工作区、非 Git 目录、权限、超时、Git 不可用等既有状态分类。
- 新增或调整聚焦单元测试；遵循 Workspace 参数化执行和 Compose UI 状态/可访问性规范。
- 不处理仓库中与开放 issue 无关的 TODO 或历史文档问题；不执行提交、推送、关闭 issue。

## Acceptance Criteria

- [x] 嵌套工作目录下 Git status 使用有效 CWD 并显示正确分支/变更；根目录仓库行为不回归。
- [x] Git diff 使用与 status 相同的 CWD，路径校验仍拒绝越界和符号链接中间路径。
- [x] 非 Git、未就绪、未绑定、权限失败、超时和 Git 缺失状态保持原有 UI 分类。
- [x] 非空输入草稿提示词包含一个 `<user_instruction>` 块及遵循指令；空白输入不包含该块。
- [x] 草稿生成前清空输入框，取消/失败在未被用户或 ASR 修改时恢复原始文本，晚到的流式片段不会覆盖用户编辑。
- [x] 通过相关单元测试、`git diff --check` 与 app/workspace 编译验证；设备保持 offline，未报告真机安装或视觉验收通过。

## Verification Evidence

- `.\gradlew --no-daemon :workspace:test :app:testDebugUnitTest :app:compileDebugKotlin` passed.
- `.\gradlew --no-daemon :app:assembleDebug` passed twice; final arm64 APK uploaded for manual review.
- `git diff --check` passed.
- `adb devices` reported `100.99.129.110:5555 offline`; no installation or device visual validation was claimed.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
