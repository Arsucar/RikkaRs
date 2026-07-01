# feat: 助手级持久化工作区目录与路径校验

## Goal

1. 同一助手下的工作区目录（cwd）持久化并在新会话中自动继承，而非每次新建会话都回到默认 `/workspace`
2. 加强工作区路径的边界校验，统一路径规范化处理

## Background

### CWD 持久化问题

当前 `workspaceCwd` 仅存储在 `Conversation.workspaceCwd`，每个会话独立。新建会话不设 `workspaceCwd`（为 null），UI 显示 `/workspace` 但未写入 DB。

- `Assistant` 只有 `workspaceId`，**无** cwd 字段
- `Conversation.workspaceCwd` 是 rootfs 绝对路径（如 `/workspace/work`）
- 新建会话：`Conversation.ofId` 不设置 `workspaceCwd`
- 切换会话：各会话各自 `workspace_cwd` 列，无助手级默认
- `ChatService` → `createWorkspaceToolsIfReady(assistant.workspaceId, conversation.workspaceCwd, …)`

### 路径边界问题

- `/workspace/*` → FILES 区域（经 `WorkspaceFileSystem.resolvePath` 限制在 files 根下）
- 其它绝对路径 → LINUX 区域，读写边界与子代理 `allowedPathPrefixes` 不一致
- `WorkspaceShellPolicy` 是启发式（非安全边界），路径级校验分散

## Confirmed Facts

- `Assistant.kt:40`：`val workspaceId: Uuid? = null`，无 cwd 字段
- `Conversation.kt:33-34`：`val workspaceCwd: String? = null`
- `ConversationEntity.kt:31-32`：`workspaceCwd` 空串 ↔ null 映射
- `FilesPicker.kt:369-375`：换绑 workspace 时清空 `conversation.workspaceCwd`
- `WorkspaceCwdPicker.kt`：选中后回传 `/workspace[/subdir]` 格式绝对路径
- `WorkspaceTools.kt:37-53`：`createWorkspaceTools(cwd)` 将 `/workspace/...` 剥为相对路径
- `WorkspaceManager.executeCommand`：cwd 为 `files/` 下的相对路径，已有 `require(workingDir.exists())` 校验
- `WorkspaceFileSystem.resolvePath`：限制在 files 根下

## Requirements

### R1: 助手级默认 CWD

1. 在 `Assistant` 数据模型增加 `defaultWorkspaceCwd: String? = null` 字段
2. 新建会话时，`conversation.workspaceCwd` 默认取 `assistant.defaultWorkspaceCwd`（已有会话不受影响，不自动同步）
3. 会话级 cwd 仍可单独覆盖
4. UI：CWD 选择器增加「设为助手默认」选项
5. 持久化兼容：`Assistant` 新增 `defaultWorkspaceCwd` 字段（DataStore JSON，`@Serializable` 默认值兼容旧数据，无 Room 迁移）

### R2: 有效 CWD 解析

1. 解析优先级：`conversation.workspaceCwd ?: assistant.defaultWorkspaceCwd ?: "/workspace"`
2. 所有使用 cwd 的入口（`ChatService`、`WorkspaceTools`、`WorkspaceReminderTransformer`）统一使用此解析

### R3: 路径边界统一

1. 规范化路径（处理 `..` 穿越和冗余 `/`）
2. 统一 FILES 区域路径前缀为 `/workspace/`
3. v1 仅规范化+文档化 LINUX 区域边界，不硬禁止；后续可增加显式配置

## Acceptance Criteria

- [ ] `Assistant` 数据模型含 `defaultWorkspaceCwd` 字段
- [ ] 新建会话继承助手的 `defaultWorkspaceCwd`
- [ ] 已有会话 cwd 不被助手级修改自动覆盖
- [ ] 会话 cwd 优先级：conversation > assistant default > `/workspace`
- [ ] CWD 选择器提供「设为助手默认」操作
- [ ] 新增 `defaultWorkspaceCwd` 字段，DataStore JSON 反序列化兼容旧数据
- [ ] 路径含 `..` 时规范化后仍落在 `/workspace/` 内
- [ ] 换绑 workspace 时会话 cwd 清空行为不变
- [ ] 现有测试通过

## Out of Scope

- workspace 多用户权限隔离
- LINUX 区域完全禁止访问（v1 不硬禁止）
- 改动 `WorkspaceShellPolicy` 的启发式规则（单独任务处理）
- 已有会话自动同步助手级 cwd
