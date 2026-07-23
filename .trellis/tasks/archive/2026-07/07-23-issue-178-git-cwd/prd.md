# 修复嵌套仓库 Git 状态与差异 CWD

## Goal

修复聊天页 Git 抽屉固定从 Workspace 根目录读取状态/差异的问题，使会话选定的嵌套仓库可用。

## Requirements

- 将会话和助手解析出的有效 CWD 从 ChatPage/ChatVM 传递到 Git status、diff use case 和 repository。
- 将 `/workspace/...` 规范化为 WorkspaceManager 所需的相对 cwd；根目录保持空字符串。
- 扩展参数化程序执行的 validated-path API 以同时接收 cwd，并在同一同步边界校验组合后的仓库路径。
- 保持已有状态分类与路径安全契约；不枚举其他仓库、不改变工作区存储模式。

## Acceptance Criteria

- [x] `/workspace/nested/repo` 下 status 命令以 `nested/repo` cwd 执行，返回正确状态；`/workspace` 根仓库仍可用。
- [x] diff 命令以相同 cwd 执行，状态中的相对路径可正确读取；越界、绝对路径和符号链接中间路径仍拒绝。
- [x] cwd 变化不会复用旧 workspace/cwd 的加载结果；取消/刷新代际保护保持有效。
- [x] 新增 WorkspaceManager/WorkspaceCwdUtils 聚焦测试，相关 app 单元测试通过。

## Verification

Workspace tests and app unit/compile tasks passed with `--no-daemon`; device installation was unavailable because the only adb endpoint remained offline.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
