# Issue #136 技术设计

## Data Migration

数据库版本从 39 升至 40。使用 Room AutoMigrationSpec 对 `ConversationEntity.is_archived` 和 `archived_at` 声明删除列；Room 在迁移事务内重建表并复制其余列。归档行不会被删除，列移除后所有保留行天然成为普通会话，无需依赖归档时间或先执行非幂等业务调用。

迁移测试从 schema 39 创建普通与归档行，并附带文件夹、标签关系、置顶、提示词/注入、工作区与记忆表隔离数据；迁移后验证 schema、行数、ID 和所有非归档字段。备份恢复沿用数据库重新打开时的同一迁移路径。

## Domain and Repository

- 从 `Conversation` / `ConversationEntity` 及双向 mapper 删除归档字段。
- `ConversationDAO` 的普通查询移除 `is_archived = 0`；删除归档专属查询、计数、状态更新和批量操作。
- `ConversationRepository` 删除归档 API、自动取消归档逻辑和归档映射；`ConversationFilter` 删除 `archived` 字段，动态 SQL 始终使用普通排序。
- `MessageFtsManager.search` 直接连接会话表但不再加归档条件；删除 `searchArchived` 和双分支 helper。
- `ChatDrawerVM` 的分页筛选不再传归档参数，删除归档数量 Flow。

## UI and Navigation

- 删除 `ui/pages/archive/`、ViewModel 注册、Drawer Tile/Badge、会话菜单项和回调。
- 删除会话归档专属字符串；保留助手归档资源。
- 为升级兼容，将旧 `Screen.Archive` 的序列化名称映射到一个不可导航到的新 legacy key，entry 直接展示安全的历史/聊天回退内容；不保留 ArchivePage 或业务逻辑。新代码没有任何入口会产生该 key。

## Compatibility and Boundaries

- schema 39 的归档值只影响被删除的列，其他数据逐列复制。
- 外键关系由 Room 生成的迁移保持；迁移测试至少验证 conversation tag 关系和一个依赖会话 ID 的实体不被破坏。
- 历史旧迁移测试可继续使用 schema 24 等旧实体结构，不应为满足当前模型而篡改历史 schema 文件。
- 助手归档相关代码、测试和字符串作为明确保护面加入最终搜索审计。

## Rollback

在正式发布前可回滚提交并继续使用 schema 39。schema 40 一旦被用户运行，回退到只认识 schema 39 的旧 APK 不受支持，因此发布前迁移测试与备份恢复验证是强制门槛。
