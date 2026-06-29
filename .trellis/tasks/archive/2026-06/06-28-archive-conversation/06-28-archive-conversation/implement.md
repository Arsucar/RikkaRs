# Implementation Plan: Conversation Archive

> Active task: `.trellis/tasks/06-28-archive-conversation/`
> 上下文顺序：implement.jsonl → prd.md → design.md → 本文件

## Execution Checklist（有序执行）

### 阶段 A：数据层（基础设施，其他阶段的前置）

- [ ] **A1 Conversation 模型加字段**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`
  - 加 `val isArchived: Boolean = false` + `val archivedAt: Instant? = null`（带 `@Serializable(with = InstantSerializer::class)`）

- [ ] **A2 ConversationEntity 加列**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt`
  - 加 `@ColumnInfo("is_archived", defaultValue = "0") val isArchived: Boolean = false`
  - 加 `@ColumnInfo("archived_at", defaultValue = "0") val archivedAt: Long = 0`
  - **核对**：Entity ↔ Model Mapper（找 `conversationToConversationEntity` / `conversationEntityToConversation`）双向加字段映射，`Instant?` ↔ `Long`(0=null)

- [ ] **A3 DB 版本与 AutoMigration**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
  - `version = 23` → `24`
  - `@Database(... autoMigrations = [AutoMigration(from=23, to=24)])`
  - **验证**：build 一次生成 `app/schemas/.../24.json`

- [ ] **A4 ConversationDAO 批量改查询**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt`
  - 活跃视图查询全部加 `is_archived = 0`（见 design 1.3 表格）
  - 新增：`getArchivedConversations(): Flow<List<...>>`、`getArchivedCount(): Flow<Int>`、`searchArchivedConversations(query): Flow<List<...>>`
  - 扩展 `updateArchiveStatus(id, archived, archivedAt)`
  - 新增 `unarchiveAll()`、`deleteAllArchived()`
  - **保留**旧 `getArchivedConversationsOfAssistant*`（不删，向后兼容）

- [ ] **A5 MessageFtsManager 搜索过滤**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt`
  - `search()` SQL 加 `INNER JOIN conversationentity c ON c.id = m.conversation_id` + `AND c.is_archived = 0`
  - 新增 `searchArchived(keyword, sort)`：同上但 `c.is_archived = 1`

**Gate A**：`./gradlew :app:compileDebugKotlin --no-daemon` 通过（不编译 app 模块外，避免内存峰值）。

### 阶段 B：领域层

- [ ] **B1 ConversationRepository 新增方法**
  - 文件：`app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`
  - 加 `getArchivedConversations()`、`getArchivedCount()`、`searchArchivedConversations(query)`、`searchArchivedMessages(query, sort)`（转发 FTS）
  - 加 `archiveConversation(id: Uuid)`：`updateArchiveStatus(id, true, now)`
  - 加 `unarchiveConversation(id: Uuid)`：`updateArchiveStatus(id, false, 0)`
  - 加 `unarchiveAll()`、`deleteAllArchived()`（遍历清 FTS + 文件后调 DAO）

- [ ] **B2 ⚠️ 自动还原契约**
  - 文件：同上，`updateConversation(conversation)` 方法内
  - 读 DB 现有会话 → 若 `existing.isArchived && contentChanged(existing, new)` → `new = new.copy(isArchived=false, archivedAt=null)`
  - `contentChanged` 判定：`existing.messageNodes != new.messageNodes`（含 size + 内容）
  - **核对**：`renameConversation`、`togglePinStatus`、`updateArchiveStatus` 等不走此方法，不触发自动还原

**Gate B**：`./gradlew :app:compileDebugKotlin --no-daemon` 通过。

### 阶段 C：表现层 - 归档区 UI（独立可验证）

- [ ] **C1 Screen.Archive 路由**
  - 文件：`app/src/main/java/me/rerere/rikkahub/ui/RouteActivity.kt`
  - `Screen` 加 `@Serializable data object Archive : Screen`
  - `entryProvider` 加 `entry<Screen.Archive> { ArchivePage() }`

- [ ] **C2 ArchiveVM**
  - 新建：`app/src/main/java/me/rerere/rikkahub/ui/pages/archive/ArchiveVM.kt`
  - 注入 `ConversationRepository`；暴露 `archivedConversations: StateFlow<List<Conversation>>`、`unarchive`、`deletePermanently`、`unarchiveAll`、`deleteAll`、`search`
  - Koin 注册（找现有 VM module，如 `di/ViewModelModule.kt`）

- [ ] **C3 ArchivePage + ArchivedConversationItem**
  - 新建：`app/src/main/java/me/rerere/rikkahub/ui/pages/archive/ArchivePage.kt`
  - TopAppBar：标题 + 「全部还原」+「清空」overflow；清空触发确认 dialog
  - 搜索框（可选，复用 `searchArchivedConversations`）
  - LazyColumn 渲染 `ArchivedConversationItem`：项点击打开（`navigate(Screen.Chat/{id})`）；长按/菜单：还原、彻底删除
  - 空状态：`R.string.archive_empty`

### 阶段 D：表现层 - 主列表归档动作 + Drawer 入口

- [ ] **D1 ConversationList 加 onArchive**
  - 文件：`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationList.kt`
  - `ConversationList` 签名 + `ConversationItem` 参数加 `onArchive: (Conversation) -> Unit = {}`
  - `DropdownMenu` 在删除项前加「归档」项（图标 `HugeIcons.Archive`，文案 `R.string.conversation_archive`）

- [ ] **D2 ChatVM.archiveConversation**
  - 文件：`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`
  - 加 `fun archiveConversation(c: Conversation) { viewModelScope.launch { conversationRepo.archiveConversation(c.id) } }`

- [ ] **D3 ChatDrawer 接 onArchive**
  - 文件：`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt`
  - `ConversationList(...)` 调用处加 `onArchive = { chatVM.archiveConversation(it) }`

- [ ] **D4 ChatDrawer 归档入口 + 角标**
  - 文件：同上，`DrawerActions`（457–519）
  - 仿 History（490–518）加 `Surface` → `navigate(Screen.Archive)`，图标 `HugeIcons.Archive`，文案 `R.string.archive_title`
  - 用 `BadgedBox`+`Badge` 包裹，count==0 时隐藏 Badge；DrawerVM 注入 repo observe `getArchivedCount()`

### 阶段 E：本地化 + Web API

- [ ] **E1 strings 新增**
  - `app/src/main/res/values/strings.xml` + `values-zh-rCN/strings.xml`
  - 9 个 key（见 design 第 5 节）

- [ ] **E2 Web API list 过滤**
  - 文件：`web/routes/ConversationRoutes.kt`
  - list 端点确认用过滤后的 Repository 查询（改 DAO 后自然生效，核对即可）

### 阶段 F：测试

- [ ] **F1 Migration_23_24Test**
  - `app/src/test/java/.../Migration_23_24Test.kt`（Room `MigrationTestHelper`）
  - 插旧结构会话 → 跑 AutoMigration → 断言 `isArchived=false`、`archivedAt=0`

- [ ] **F2 ConversationDAOTest**
  - 归档查询返回正确集合；活跃查询过滤归档；`archivedAt DESC` 排序；`updateArchiveStatus` 翻转

- [ ] **F3 ConversationRepositoryTest**
  - 自动还原契约：归档会话 → `updateConversation(contentChanged)` → 断言 `isArchived=false`
  - `archiveConversation` / `unarchiveConversation` / `unarchiveAll` / `deleteAllArchived`

### Gate G：最终验证

- [ ] **G1 编译**：`./gradlew :app:assembleDebug --no-daemon`
- [ ] **G2 单测**：`./gradlew :app:testDebugUnitTest --no-daemon`
- [ ] **G3 Lint**：`./gradlew :app:lintDebug --no-daemon`
- [ ] **G4 设备安装验证**（按 AGENTS.md）
  - `adb devices`（至少一台 device）
  - `./gradlew :app:installDebug --no-daemon`
  - 手动验证 7 条 AC

## Validation Commands

```bash
# 快速编译检查（阶段 Gate）
./gradlew :app:compileDebugKotlin --no-daemon

# 完整构建 + 单测 + lint
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon

# 装机验证
adb devices
./gradlew :app:installDebug --no-daemon
```

## Risky Areas

- **DB AutoMigration 24.json 未生成**：首次 build 前确保 `app/schemas/` 目录可写；若 CI 失败，本地 `./gradlew :app:assembleDebug` 一次生成 schema。
- **自动还原契约漏改**：`updateConversation` 有多条调用路径，必须在 Repository 单一入口统一处理，单测覆盖。
- **FTS JOIN 性能**：`message_fts INNER JOIN conversationentity` 可能影响搜索延迟；保持 `LIMIT 50`、确保 `conversationentity.id` 有索引（PrimaryKey 自带）。
- **ConversationList 复用陷阱**：归档区**不复用** `ConversationList`，避免 Paging/Pin 耦合污染。

## Rollback Points

- **RP1（阶段 A 后）**：DB schema 已改但未上线——revert 代码即可（未影响老用户）。
- **RP2（阶段 B 后）**：领域层已改——revert A+B。
- **RP3（上线后）**：DB 已升级到 24 的用户，revert 会触发版本回退崩溃——**不可回滚 DB 部分**，只能前进式修复。建议 DB 迁移独立 commit，PR 拆分为「迁移 + 数据层」与「UI + 功能」两段，降低 rollback 难度。

## Sub-agent Dispatch Hints

- 阶段 A/B（数据/领域层）：派给 `trellis-implement`，上下文含本文件 + design.md 第 1-2 节 + design.md 的"现状偏离"标记。
- 阶段 C/D（UI）：可并行派两个 `trellis-implement`（归档区 UI / 主列表+Drawer），因它们文件不重叠。**注意：只有最后一个 check 的 agent 允许全量编译**，前一个只做 `compileDebugKotlin`。
- 阶段 F（测试）：派给 `trellis-implement`，附 design.md 的契约定义。
- 阶段 G：主代理亲自装机验证。
