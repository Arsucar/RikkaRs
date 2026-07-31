# Design: 统计页面增量统计表与缓存 (#193)

## Boundaries

| Layer | Change |
|-------|--------|
| Data | 新表 `message_stats` / `message_stats_daily`；DAO；Migration 45→46；写路径挂钩 |
| Domain/Repo | `ConversationRepository` 在 insert/update 后按会话重算汇总；删除靠 CASCADE |
| Presentation | `StatsVM` 读汇总 + 缓存 + 并行 + 手动刷新；`StatsPage` 刷新入口与上次更新 |
| DI | `AppDatabase` DAO；Koin 绑定 `MessageStatsDAO`（若 StatsVM 直接依赖） |

不改：`message_node.messages` JSON 结构；FTS；#191 API 健康度。

## Data model

### message_stats（每会话一行）

```kotlin
@Entity(
  tableName = "message_stats",
  foreignKeys = [ForeignKey(
    entity = ConversationEntity::class,
    parentColumns = ["id"],
    childColumns = ["conversation_id"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("conversation_id")],
)
data class MessageStatsEntity(
  @PrimaryKey @ColumnInfo("conversation_id") val conversationId: String,
  @ColumnInfo("message_count") val messageCount: Int,
  @ColumnInfo("user_message_count") val userMessageCount: Int,
  @ColumnInfo("token_input") val tokenInput: Long,
  @ColumnInfo("token_output") val tokenOutput: Long,
  @ColumnInfo("token_cached") val tokenCached: Long,
  @ColumnInfo("updated_at") val updatedAt: Long,
)
```

聚合语义与现状 `getTokenStats` 对齐：**每个 node 的全部 messages 都计入**（`json_each` 全展开），非仅 `selectIndex`。

### message_stats_daily（每会话每天一行，支撑热力图）

```kotlin
@Entity(
  tableName = "message_stats_daily",
  primaryKeys = ["conversation_id", "day"],
  foreignKeys = [ForeignKey(
    entity = ConversationEntity::class,
    parentColumns = ["id"],
    childColumns = ["conversation_id"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("conversation_id"), Index("day")],
)
data class MessageStatsDailyEntity(
  @ColumnInfo("conversation_id") val conversationId: String,
  @ColumnInfo("day") val day: String, // yyyy-MM-dd
  @ColumnInfo("user_message_count") val userMessageCount: Int,
)
```

热力图：`SELECT day, SUM(user_message_count) FROM message_stats_daily WHERE day >= ? GROUP BY day`。

全局 token/消息：`SELECT SUM(...) FROM message_stats`。

## Write path

在已有 `database.withTransaction` 内，于 `saveMessageNodes` / `syncMessageNodes` **成功写完 nodes 后**：

1. 用内存中的 `List<MessageNode>` 纯函数 `computeMessageStats(conversationId, nodes)` 算出一行 stats + 若干 daily。
2. `messageStatsDao.upsert(stats)`。
3. `messageStatsDao.deleteDailyByConversation(conversationId)` + `insertDailyAll(dailyRows)`（会话级全量替换，避免 diff 复杂度；节点规模有限）。

删除会话：现有 `conversationDAO.delete` + `message_node` CASCADE；新表同样 CASCADE，无需手写 delete（仍可在 delete 事务内显式删作保险）。

## Read path (StatsVM)

```
loadStats(force: Boolean = false)
  ensureBackfilled()           // 若汇总空且 message_node 非空，一次性 json_each 回填
  parallel {
    conversationDAO.countAll()
    messageStatsDao.sumTokenStats()
    messageStatsDao.getUserMessageCountPerDay(startDate)
  }
  settings.launchCount
  update AppStats(isLoading=false, lastUpdatedAt=now, ...)
```

- 去掉 `delay(50)`。
- 并行：`coroutineScope { async/await }` 或 `awaitAll`。
- 缓存：`_stats` 即 VM 缓存；再次进入同一 VM 已有数据；`refresh()` 强制重载。
- 首次无缓存：`isLoading=true`；刷新中：`isRefreshing=true`，保留旧数据。

## Migration 45 → 46

- 手写 `Migration_45_46`：`CREATE TABLE` 两张表 + 索引（与 Entity 一致）。
- **不在 Migration 内全量回填**（避免升级卡死）。
- 回填：`MessageStatsBackfill.ensure()`  
  - 条件：`message_stats` 行数为 0 且 `message_node` 有数据（或 meta flag `stats_backfill_done` 未置位）。  
  - 实现：复用现有 json_each 一次写入汇总，或按 conversation 分页 decode 后 `computeMessageStats`。  
  - 优先 **SQL 一次回填**（与旧查询同语义，无需 Kotlin 解码全库）：

```sql
INSERT INTO message_stats (...)
SELECT conversation_id, COUNT(*), SUM(role=user), SUM(prompt), ...
FROM message_node, json_each(messages) GROUP BY conversation_id;

INSERT INTO message_stats_daily (...)
SELECT conversation_id, day, COUNT(*) FROM ... WHERE role=user GROUP BY conversation_id, day;
```

- 手动刷新：删除全部汇总行后重新 backfill，或重新扫全库。

## UI

- `LargeFlexibleTopAppBar` `actions`：刷新 `IconButton` + 可选「上次更新」`Text`。
- 刷新中：图标旋转或 small progress，不整页 Loading。
- 字符串：`stats_page_refresh` / `stats_page_last_updated`（en + zh 至少）。

## Compatibility / Rollback

- 新表可丢：回退版本需 drop 表；应用侧可读旧 json_each 作 fallback（可选，第一版可不留 fallback，强制 backfill）。
- 备份：Room DB 整体备份已含新表。

## Tradeoffs

| 选项 | 结论 |
|------|------|
| 写路径增量 diff vs 会话重算 | **会话重算**：节点列表已在内存，正确性高，代码短 |
| daily 全局表 vs per-conversation | **per-conversation + CASCADE**，删会话不脏数据 |
| Migration 内回填 vs 首次读回填 | **首次读/ensure**，不阻塞升级 |
| 保留 json_each 作 fallback | 手动全量重算可走同一 backfill SQL |

## #192 关系

本设计直接消灭全表 json_each 读路径，#192 在验证通过后关单，不另做半吊子缓存。
