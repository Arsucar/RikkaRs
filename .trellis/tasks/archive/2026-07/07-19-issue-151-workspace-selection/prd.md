# 修复工作区工具选择与状态 #151

## Goal

修复工作区工具开启时的错误绑定和可用状态误报。

## Requirements

- 0 workspace 提供创建入口；1 workspace 明确绑定；N workspace 必须选择且可取消。
- OFF 仅解除助手绑定；非 READY 不计入有效工具并提供修复入口。
- 保存失败、无效 ID、workspace 删除和状态变化不崩溃、不静默改配置。

## Acceptance Criteria

- [ ] 0/1/N、取消、READY/INSTALLING/DISABLED、无效 ID、保存失败均有测试。
- [ ] UI 统计与 `ChatService.createWorkspaceToolsIfReady()` 门控一致。
- [ ] app 编译并完成安装验收。

## Notes

- 依赖：无；其 configured/available 语义必须被 #153 复用。
