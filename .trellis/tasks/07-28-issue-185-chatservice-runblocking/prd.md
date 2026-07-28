# 修复 #185 ChatService workspaceToolsFactory 内 runBlocking 线程饥饿风险

## Goal

service/ChatService.kt:3009 在 subagent workspace 工具工厂 lambda 内用 runBlocking 桥接 suspend 调用（取 assistantPrivateSkillMounts 并创建 workspace 工具）。该工厂在生成链路调用，可能运行于 Dispatchers.IO，池饱和时阻塞线程且不响应取消，存在线程饥饿/死锁风险。修复：工具工厂改 suspend lambda，或在构建工具列表前预先 suspend 取值后传入纯函数工厂。

## Requirements

- 消除 `service/ChatService.kt` 中 `toolsForSubagentProfile` 内 `workspaceToolsFactory` lambda 的 `runBlocking`。
- 首选方案：在进入工具工厂前（`toolsForSubagentProfile` 已是 suspend）预先 suspend 取值 `assistantPrivateSkillMounts(assistant.id)`，将结果捕获进纯函数工厂；`createSubagentWorkspaceTools` 若为 suspend，需相应把 factory 签名改为 suspend 或将其结果预先物化。
- 保持 `buildSubagentTools` 与 `SubagentPermissionBuilder` 调用点语义不变（同步 factory）或同步改为 suspend，二选一且保持编译一致。
- 全链路可取消、不阻塞 `Dispatchers.IO` 线程。
- 异常处理保持与现状一致（取 mounts 失败降级为空 mounts，不抛出中断生成）。

## Acceptance Criteria

- [ ] 该路径代码中无 `runBlocking`。
- [ ] 子代理 workspace 工具仍能正确构建（含 `/skills` 与私有 skill 挂载）。
- [ ] `:app:compileDebugKotlin` 通过。
- [ ] 逻辑评审确认高并发子代理生成下无线程饥饿/死锁引入。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
