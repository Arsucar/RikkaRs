# Technical Design: Conversation Archive

## Architecture Overview

三层改动：**数据层（Entity/DAO/Migration）→ 领域层（Model/Repository）→ 表现层（UI/VM/Navigation）**。核心是给会话加一个"可逆冷藏"状态位，所有活跃视图过滤它，归档区单独展示它，内容更新自动翻转它。

## 1. Data Layer

### 1.1 ConversationEntity

`app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt`

新增两列（带 defaultValue，满足 AutoMigration 加列非空约束）：

```kotlin
@ColumnInfo("is_archived", defaultValue = "0")
val isArchived: Boolean = false,

@ColumnInfo("archived_at", defaultValue = "0")
val archivedAt: Long = 0,
```

### 1.2 Migration（AutoMigration 23→24）

`AppDatabase.kt` version 23 → 24。

- 方案：`AutoMigration(from=23, to=24)`，因两列均带 defaultValue，Room 自动生成 `ALTER TABLE conversationentity ADD COLUMN ...`。
- **前置**：确认 `app/schemas/.../23.json` 已存在（CI schema 导出）；build 后生成 `24.json`。
- 验证：`Migration_23_24Test`（Room `MigrationTestHelper`），插一条旧结构会话 → 跑迁移 → 断言 `isArchived=false`、`archivedAt=0`。

### 1.3 ConversationDAO 批量改动

`app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt`

**A. 活跃视图查询统一加 `WHERE is_archived = 0`：**

| 方法 | 当前 SQL | 改后 |
|---|---|---|
| `getAll()` | `SELECT * ... ORDER BY is_pinned DESC, update_at DESC` | `+ WHERE is_archived = 0` |
| `getConversationsOfAssistant(id)` | `WHERE assistant_id = :id ORDER BY ...` | `+ AND is_archived = 0` |
| `getConversationsOfAssistantPaging(id)` | 同上 | `+ AND is_archived = 0` |
| `searchConversations*`（全部变体） | `WHERE title LIKE ...` | `+ AND is_archived = 0` |
| `getPinnedConversations()` | `WHERE is_pinned = 1` | `+ AND is_archived = 0` |
| `getRecentConversations()` | （查最近） | `+ AND is_archived = 0` |
| `countAll()` / `countConversations()` | `COUNT(*)` | `+ WHERE is_archived = 0` |

**B. 新增归档区查询（跨助手）：**

```kotlin
@Query("SELECT * FROM conversationentity WHERE is_archived = 1 ORDER BY archived_at DESC")
fun getArchivedConversations(): Flow<List<ConversationEntity>>

@Query("SELECT COUNT(*) FROM conversationentity WHERE is_archived = 1")
fun getArchivedCount(): Flow<Int>

@Query("SELECT * FROM conversationentity WHERE is_archived = 1 AND title LIKE :query ORDER BY archived_at DESC")
fun searchArchivedConversations(query: String): Flow<List<ConversationEntity>>
```

> **现状偏离**：代码已有 `getArchivedConversationsOfAssistant(assistantId)` / `...Paging(assistantId)`（按助手过滤），与本次需求"跨助手归档区"冲突。**保留旧方法不动（向后兼容），新增上面三个跨助手方法供归档区使用。** 在 design review 时标红这一点。

**C. 归档状态翻转：**

已有 `updateArchiveStatus(id, isArchived)`（仅翻转状态），**需扩展为同时更新 `archivedAt`**：

```kotlin
@Query("UPDATE conversationentity SET is_archived = :archived, archived_at = :archivedAt WHERE id = :id")
suspend fun updateArchiveStatus(id: String, archived: Boolean, archivedAt: Long)

@Query("UPDATE conversationentity SET is_archived = 0, archived_at = 0")
suspend fun unarchiveAll()

@Query("DELETE FROM conversationentity WHERE is_archived = 1")
suspend fun deleteAllArchived()
```

> `deleteAllArchived` 会触发 `message_node` CASCADE，但**不会**清 FTS 与会话文件——Repository 层需在调用前遍历清 FTS/文件。

### 1.4 FTS 搜索过滤（searchMessages）

`app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt` `search()` 方法。

当前 SQL（65–97）：
```sql
SELECT node_id, message_id, conversation_id, title, update_at,
       simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
FROM message_fts
WHERE text MATCH jieba_query(?)
ORDER BY ${sort.orderBy}
LIMIT 50
```

**改后（活跃搜索，过滤归档）**：
```sql
SELECT m.node_id, m.message_id, m.conversation_id, m.title, m.update_at,
       simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
FROM message_fts m
INNER JOIN conversationentity c ON c.id = m.conversation_id
WHERE m.text MATCH jieba_query(?) AND c.is_archived = 0
ORDER BY ${sort.orderBy}
LIMIT 50
```

**新增归档区搜索**（`searchArchived`）：
```sql
... WHERE m.text MATCH jieba_query(?) AND c.is_archived = 1 ...
```

> FTS 索引本身**保留**（归档不 `deleteConversation`），只是查询时按 `is_archived` 过滤。

## 2. Domain Layer

### 2.1 Conversation Model

`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`

```kotlin
val isArchived: Boolean = false,
@Serializable(with = InstantSerializer::class)
val archivedAt: Instant? = null,  // null 表示未归档
```

> `archivedAt` 用 `Instant?`（领域层），Entity 用 `Long`（DB 层 0 表示未归档）。Mapper 负责转换。

### 2.2 ConversationRepository

`app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`

**新增方法：**
```kotlin
fun getArchivedConversations(): Flow<List<Conversation>>   // 跨助手
fun getArchivedCount(): Flow<Int>
fun searchArchivedConversations(query: String): Flow<List<Conversation>>
suspend fun archiveConversation(id: Uuid)                  // isArchived=true, archivedAt=now
suspend fun unarchiveConversation(id: Uuid)                // isArchived=false, archivedAt=0, 不改 updateAt
suspend fun unarchiveAll()
suspend fun deleteAllArchived()                            // 遍历清 FTS + 文件 + DAO.deleteAllArchived
```

**⚠️ 自动还原契约（核心）：**

`updateConversation(conversation: Conversation)` 是会话内容的唯一写入入口（约 217–227 行）。在此方法内增加判断：

```kotlin
suspend fun updateConversation(conversation: Conversation) {
    val effective = if (conversation.isArchived && isContentChanged(conversation)) {
        // 内容变化且当前已归档 → 自动解档
        conversation.copy(isArchived = false, archivedAt = null)
    } else {
        conversation
    }
    // 原有写入逻辑（DAO.update + FTS.indexConversation）
}
```

`isContentChanged` 判定：对比 DB 中现有会话的 `messageNodes`（数量或 hash）与传入的 `messageNodes`。简单实现：`existing.messageNodes.size != new.messageNodes.size || existing.messageNodes != new.messageNodes`。

> **注意**：仅"内容变化"触发自动还原，纯标题修改（`renameConversation`）、置顶（`togglePinStatus`）等**不触发**——它们不走 `updateConversation` 或走独立 DAO 方法。

## 3. Presentation Layer

### 3.1 路由

`app/src/main/java/me/rerere/rikkahub/ui/RouteActivity.kt`

- `Screen` sealed interface（约 592 行起）新增：`@Serializable data object Archive : Screen`（参考 `History` 604–605）。
- `entryProvider`（约 345–347 后）新增：`entry<Screen.Archive> { ArchivePage() }`。

### 3.2 ArchivePage + ArchiveVM（新建）

- `app/src/main/java/me/rerere/rikkahub/ui/pages/archive/ArchivePage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/archive/ArchiveVM.kt`

**ArchiveVM**：
```kotlin
class ArchiveVM(
    private val conversationRepo: ConversationRepository,
) : ViewModel() {
    val archivedConversations: StateFlow<List<Conversation>> =
        conversationRepo.getArchivedConversations().stateIn(...)
    val archivedCount: StateFlow<Int> = ...  // Drawer 角标也用（或在 DrawerVM 单独 observe）

    fun unarchive(c: Conversation)
    fun deletePermanently(c: Conversation)
    fun unarchiveAll()
    fun deleteAll()  // 触发二次确认 UI
    fun search(query: String): Flow<List<Conversation>>
}
```

**ArchivePage**：LazyColumn 直接渲染 `List<Conversation>`（**不复用** `ConversationList`，因其 Paging + Pin 分组头耦合 `ChatDrawerVM`）。新建 `ArchivedConversationItem`，项菜单含：还原、彻底删除。顶部 TopAppBar 含"全部还原"、"清空"动作 + 搜索框。

### 3.3 主列表长按菜单加"归档"

`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationList.kt`：

1. `ConversationList` 签名（74–84）增 `onArchive: (Conversation) -> Unit = {}`。
2. `ConversationItem` 参数（221–230）增 `onArchive`。
3. `DropdownMenu`（287–346）在"删除"前新增：
   ```kotlin
   DropdownMenuItem(
       text = { Text(stringResource(R.string.conversation_archive)) },
       leadingIcon = { Icon(HugeIcons.Archive, null) },
       onClick = { onArchive(conversation); showDropdownMenu = false }
   )
   ```
4. `ChatDrawer.kt`（218–248）调用处：`onArchive = { chatVM.archiveConversation(it) }`，`ChatVM` 增 `archiveConversation` 方法。

### 3.4 ChatDrawer 归档入口 + 角标

`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt` 的 `DrawerActions`（457–519）：

仿 History（490–518）新增 `Surface`，导航 `Screen.Archive`，图标 `HugeIcons.Archive`，文案 `R.string.archive_title`。

**角标**：用 `BadgedBox` + `Badge`（参考 `McpPicker.kt` 94–103）。数据源 `conversationRepo.getArchivedCount(): Flow<Int>`。当 count==0 时隐藏 Badge。DrawerVM 注入 `conversationRepo`，observe count。

### 3.5 ChatVM 自动还原的触发路径

用户在归档区点开会话 → 进入 `ChatVM`（与主列表一致）→ 发消息/编辑/重生成 → `ChatVM` 调 `conversationRepo.updateConversation(...)` → Repository 内部自动解档（见 2.2）→ Flow 推送 → 归档区列表实时移除该项、主列表实时出现该项。

**无需 ChatVM 感知归档状态**——Repository 是单一真相源。

## 4. Data Flow（端到端）

### 4.1 归档
```
主列表项长按 → DropdownMenu「归档」→ ConversationList.onArchive(c)
  → ChatVM.archiveConversation(c) → Repo.archiveConversation(id)
  → DAO.updateArchiveStatus(id, archived=true, archivedAt=now())
  → Flow 推送：主列表移除、归档区新增、Drawer 角标+1
```

### 4.2 归档区打开会话并聊天 → 自动还原
```
归档区点会话 → navigate(Screen.Chat/{id})（与主列表同一路由）
  → ChatVM 加载会话（isArchived=true，但 UI 不阻止）
  → 用户发消息 → ChatVM.generate → Repo.updateConversation(updated)
  → Repo 检测 isContentChanged → updated.copy(isArchived=false, archivedAt=null)
  → DAO.update + FTS.indexConversation
  → Flow 推送：归档区移除、主列表新增、Drawer 角标-1
  → 用户返回 → 回到归档区 → 该会话已消失
```

### 4.3 还原
```
归档区项菜单「还原」→ ArchiveVM.unarchive(c) → Repo.unarchiveConversation(id)
  → DAO.updateArchiveStatus(id, archived=false, archivedAt=0)
  → 不改 updateAt → Flow 推送：归档区移除、主列表按 updateAt 回到原位
```

### 4.4 彻底删除
```
归档区项菜单「彻底删除」→ ArchiveVM.deletePermanently(c) → Repo.deleteConversation(c)
  → （复用现有硬删除：FTS.deleteConversation + DAO.delete + filesManager.deleteChatFiles）
```

## 5. Localisation

新增 strings（`values/strings.xml` + `values-zh-rCN/strings.xml`）：

| key | en | zh-rCN |
|---|---|---|
| `archive_title` | Archive | 归档 |
| `conversation_archive` | Archive | 归档 |
| `archive_unarchive` | Unarchive | 还原 |
| `archive_delete_permanently` | Delete permanently | 彻底删除 |
| `archive_unarchive_all` | Unarchive all | 全部还原 |
| `archive_clear_all` | Clear archive | 清空归档 |
| `archive_clear_all_confirm` | Clear all archived conversations? This cannot be undone. | 清空所有归档会话？此操作不可撤销。 |
| `archive_empty` | No archived conversations | 没有归档会话 |
| `archive_search_placeholder` | Search archived | 搜索归档 |

## 6. Web API

`web/routes/ConversationRoutes.kt`：list 端点的 Repository 调用切换到"已过滤归档"的查询（即 `getConversationsOfAssistant*` 改后版本，自然过滤）。**不新增端点。**

## 7. Trade-offs

- **复用 `ConversationList` vs 新建 `ArchivedConversationItem`**：选后者。前者强耦合 Paging + Pin 分组头，强行加开关会污染主列表逻辑。
- **AutoMigration vs 手写 Migration**：选 AutoMigration，因两列带 defaultValue、无复杂 schema 变换。风险点：若 24.json 未生成会导致 CI 失败，需本地先 build 一次。
- **跨助手归档 vs 按助手归档**：选跨助手。归档语义是"全局冷藏柜"，不属任何助手；但保留旧的按助手查询方法（不删，向后兼容）。
- **自动还原判定基于 `messageNodes` 变化**：简单但可能漏边界（如仅 metadata 变）。MVP 接受，后续可基于 `updateConversation` 调用栈细分。

## 8. Compatibility

- 老用户升级：AutoMigration 加列，默认值安全。
- Web/同步：不破坏现有 API 契约，仅 list 行为变化（过滤归档）。
- FTS：索引结构不变，仅查询 SQL 加 JOIN。

## 9. Rollout / Rollback Shape

- 单次 PR 上线，无灰度。
- Rollback：revert PR 即可（DB 已升级的用户 revert 后版本号回退，AutoMigration 24→23 不可逆——需 fallback 或不 revert DB 部分）。**建议：DB 迁移部分单独 commit，作为 rollback point。**
