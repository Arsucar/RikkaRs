# Issue #136 实施计划

- [x] 加载 `trellis-before-dev`、app 规范、跨层/复用指南与本任务产物。
- [x] 将 AppDatabase 升至 40，添加删除两列的 39→40 AutoMigrationSpec 并生成 schema 40。
- [x] 删除 Conversation domain/entity 归档字段及 Repository mapper/自动取消归档/归档 API。
- [x] 简化 ConversationDAO、ConversationFilter 和 MessageFtsManager，使普通查询覆盖全部会话。
- [x] 删除 ArchivePage/ArchiveVM、DI 注册、Drawer Tile/Badge、会话菜单回调和相关 imports。
- [x] 添加旧 NavKey 序列化兼容重定向，确保没有新入口可进入归档页面。
- [x] 删除会话归档专属资源与归档专属测试；更新 DAO/Repository 回归测试。
- [x] 新增 Migration_39_40 仪器测试，验证普通/旧归档混合数据、组织信息及外键关系。
- [x] 使用 `locale-tui-localization` 处理资源删除并核查所有 values* 目录，不误删助手归档翻译。
- [x] 搜索残留：`is_archived|archived_at|Screen.Archive|ArchivePage|conversation_archive|getArchived`，只允许历史 schema/迁移夹具或助手归档的明确例外。
- [x] 运行 `git diff --check`。
- [x] 运行相关 JVM/编译验证：`Conversation*Test`、`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`，全部带 `--no-daemon`。
- [ ] 有设备时运行定向 Migration_39_40 instrumentation；不默认运行完整 connected 测试套件。
- [ ] 按 app 功能改动流程执行 `adb devices` 与 `:app:installDebug --no-daemon`。
- [ ] 运行 `:app:lintDebug --no-daemon`，区分既有基线与本任务新增问题。
- [ ] 最终人工核查 Chat Drawer/会话菜单布局、普通搜索与助手归档保护面。

验证记录：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin` 均通过；新增 `ConversationFilterQueryTest` 通过。定向 `Migration_39_40_Test` 已完成测试 APK 构建，但无线设备在 connected 测试安装/执行阶段无响应，未声称仪器测试通过。`lintDebug` 尚未针对 #136 重跑；仓库既有 lint 基线问题已在 #137 验证中记录。
