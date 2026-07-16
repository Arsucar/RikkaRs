# Issue 136 移除会话归档

## Goal

安全移除低频的“会话归档”功能，减少会话列表和 Chat Drawer 的概念与维护负担，同时确保升级前的所有归档会话在升级后作为普通会话完整可见。

## Requirements

- 删除 Chat Drawer 的会话归档 Tile/Badge、会话菜单的归档操作、`ArchivePage`、`ArchiveVM` 及其 DI/导航入口。
- 删除 `Conversation`、`ConversationEntity` 中的 `isArchived` / `archivedAt`，以及 DAO、Repository、FTS 和筛选器中的会话归档专用分支。
- 数据库从 schema 39 升级时保留全部会话行及 ID、消息、助手、模型、标题、时间、置顶、文件夹、标签、提示词、注入、工作区和记忆表隔离信息；旧归档标记不再具有隐藏语义。
- 普通会话、最近会话、标题搜索、消息搜索、历史、文件夹、标签、置顶和删除必须继续处理全部保留会话。
- 恢复旧备份后由同一 Room 迁移保证旧归档会话可见，不增加网络或权限依赖。
- 对升级期间保存的旧归档导航状态提供兼容回退，不允许因移除页面而崩溃。
- 只删除“会话归档”；`Assistant.isArchived`、助手归档/恢复 UI、DataStore 策略和相关资源必须完整保留。
- 清理会话归档专属字符串、图标引用和测试，不误删通用 archive 文件类型含义或助手归档文案。

## Acceptance Criteria

- [ ] schema 39 中普通与归档会话混合迁移到 schema 40 后，所有会话均存在且普通查询可访问，非归档字段与标签关系保持不变。
- [ ] `ConversationEntity` 与 `Conversation` 不再含会话归档字段，DAO/Repository/FTS/动态筛选 SQL 不再引用 `is_archived` 或 `archived_at`。
- [ ] Chat Drawer、会话菜单和导航中没有可进入的会话归档 UI，旧保存状态安全展示聊天或历史回退页面。
- [ ] 普通搜索、历史、最近会话、文件夹、标签、置顶、删除和统计查询通过回归验证。
- [ ] 旧备份数据库在恢复重启后通过 39→40 迁移，旧归档会话不会丢失或继续隐藏。
- [ ] 助手归档及其测试保持通过，证明没有把两个归档概念混淆。
- [ ] app 编译、相关测试、静态检查和可用设备上的安装验收通过；无法执行的设备测试如实记录。

## Confirmed Facts

- 当前数据库版本为 39，`ConversationEntity` 中存在 `is_archived` 与 `archived_at`。
- Room 已有使用 `@DeleteColumn` 的自动迁移惯例，可由 Room 重建表并保留剩余列。
- 会话归档与助手归档是两套独立功能；后者位于 `AssistantArchivePolicy`、`AssistantPage` 和 Settings DataStore。
- `rememberNavBackStack` 会保存可序列化 NavKey，因此需要保留旧序列化名的兼容重定向，而不是直接让旧状态无法解码。
- 本地/WebDAV/S3 备份恢复的是数据库文件并重启应用，恢复后的旧 schema 会走正常 Room migration。

## Out of Scope

- 移除或改变助手归档功能。
- 引入新的会话隐藏机制、归档设置开关或远程迁移服务。
- 清理与会话归档无关的通用 archive 文件格式/文案。
