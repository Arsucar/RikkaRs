# #151 Workspace 绑定与可用状态设计

## Domain Decisions

新增纯 Kotlin workspace tool capability 投影，作为 UI 和 runtime 的共享规则：

- `configured`：Assistant 存在非空 workspaceId。
- `workspace`：绑定 ID 能在当前 WorkspaceEntity 列表/查询中解析到实体。
- `available`：实体存在且规范化 shellStatus 为 READY。
- `availableToolNames`：READY 普通模式为四个 workspace 工具；read-only 为 `workspace_read_file`；其他为空。
- `unavailableReason`：UNCONFIGURED、MISSING、DISABLED、INSTALLING、BROKEN/UNKNOWN。

状态字符串通过单一规范化函数处理；未知值按 DISABLED/不可用 fail closed，不在 UI 和 ChatService 分别比较裸字符串。

## Toggle Flow

OFF -> ON 由 `decideWorkspaceEnable(workspaces)` 决策：

- 0 个有效 workspace：不写 Assistant，展示创建/管理入口并导航现有 `Screen.Workspaces`。
- 1 个有效 workspace：提交该 ID；成功后反馈，失败保持原配置。
- N 个有效 workspace：打开 `WorkspaceSelectSheet`，用户明确选择后提交；dismiss/cancel 不写配置。
- 非法 entity ID：不抛异常、不写配置，显示本地化错误。

ON -> OFF 只把 `assistant.workspaceId` 设为 null，不删除 workspace/rootfs。

## Persistence Boundary

让 `AssistantDetailVM` 暴露 workspace binding 的可观察保存结果。保存函数接收当前 Assistant 与目标 ID，
仅在 `PreferencesStore.updateAssistantConfig` 成功后报告成功；异常报告失败，UI 继续以 Settings flow 的已提交 Assistant
作为唯一真相，不维护会污染统计的乐观绑定。

现有 `update()` 供其他字段继续使用；workspace 绑定走专用 API，避免扩大本次改动面。

## UI

- 复用 `WorkspaceSelectSheet`；补足 selected/radio semantics，使 TalkBack 能读出选择状态。
- workspace 组的 switch checked 表示 configured；工具行与顶部 count 只按 available 展示为有效。
- dangling/non-READY 绑定保留 checked，但显示稳定 reason；已有实体时提供进入 `Screen.WorkspaceDetail(id)` 的修复按钮。
- 0 workspace 提供带文字的“创建工作区”入口，先进入现有 Workspaces 页面；不扩展路由自动弹创建框。
- 所有新增文案使用 Android string resources，并按 locale workflow 补齐配置 locale。

## Tests

- table-driven enable decision：0/1/N、invalid、cancel。
- capability：null/missing/DISABLED/INSTALLING/READY/BROKEN/unknown/readOnly。
- stats/tool names 与 shared capability 一致。
- workspaceId 持久化 null->A、A->null、A->B，只改目标 Assistant。
- save failure 不改变 committed configuration。

## Rollback

共享 capability 纯函数可独立保留；若 UI 选择器回归，只回滚页面 wiring。专用保存 API 不改变序列化 schema，
可安全回滚到旧 `update()` 调用。
