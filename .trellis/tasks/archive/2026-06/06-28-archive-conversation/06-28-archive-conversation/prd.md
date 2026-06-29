# Product Requirements Document

## Goal and User Value

实现「对话归档区」：用户可把不再活跃但想保留的会话归档（可逆冷藏），让主列表聚焦当前讨论；归档会话集中存放于独立归档区，可随时查看、搜索、还原或彻底删除，且归档后仍可继续聊天（聊则自动还原）。

核心语义：**归档 = 可逆冷藏**。与置顶（`isPinned`）正交，与删除（硬删）解耦。归档只是状态变化，不丢数据、不动 FTS 索引、不改会话文件。

## Background

- 现状：`Conversation` 仅 `isPinned` 一个列表状态字段；删除是硬删除（FTS + Room + 文件全清），UI 仅 Snackbar 临时撤销。
- 痛点：用户想"暂时收起但不想丢"的会话无处安放——留在主列表污染视野，删除则永久丢失。
- 数据库当前 v23，迁移到 v24。代码层已有部分遗留实现（`updateArchiveStatus`、`getArchivedConversationsOfAssistant*`），但方向是"按助手过滤"，与本次需求"跨助手归档区"冲突，需对齐。

## Key Decisions（Grill 结论）

| # | 决策点 | 选择 |
|---|---|---|
| 1 | 归档语义 | **A. 可逆冷藏**：保留数据、可还原、可彻底删 |
| 2 | 持久化字段 | `isArchived:Boolean` + `archivedAt:Long` |
| 3 | 排序/置顶 | 主列表 `is_archived=0` + 原 SQL（`is_pinned DESC, update_at DESC`）；归档区 `is_archived=1` 按 `archivedAt DESC`，忽略 pin |
| 4 | UI 入口 | Drawer 新入口 + 独立 `ArchiveScreen` |
| 5 | 数据源 | 新增跨助手 `getArchivedConversations(): Flow`，不 Paging、不过滤 assistantId |
| 6 | 归档区项交互 | 点击 = 打开会话；长按/滑动菜单：还原、彻底删除 |
| 6.1 | 可编辑性 | 可聊天/编辑/重生成；**内容更新自动还原**（`isArchived=false` + `archivedAt=0`） |
| 7 | 归档触发 | **仅主列表项长按菜单**加"归档"；HistoryPage / 聊天页菜单不加 |
| 7.1 | HistoryPage | 过滤归档（与主列表一致，`is_archived=0`） |
| 8 | 搜索 | FTS 索引保留；活跃搜索 SQL 加 `is_archived=0`（JOIN conversationentity）；归档区独立"搜归档" `is_archived=1` |
| 9 | Web API | 仅 list 端点过滤归档，不新增 archive/unarchive 端点 |
| 10 | DB 迁移 | AutoMigration 23→24，两列带 defaultValue |
| 11 | 还原后位置 | 不改 `updateAt`，回原时间位 |
| 12 | 批量操作 | 归档区顶部"全部还原"+"清空"（清空二次确认） |
| 13 | 入口可见性 | Drawer 始终显示 + 数量角标（observe `archivedCount: Flow<Int>`） |
| 14 | 返回行为 | 归档区打开会话后返回回**归档区**（nav 默认 back stack） |
| 15 | 本地化 | `values/`（英文 default）+ `values-zh-rCN/`（中文）；其余 locale 后续用 locale-tui 补 |
| 16 | 测试 | Migration_23_24 + DAO（过滤/排序）+ Repository（自动还原契约）单测 |
| 17 | 验收 | 端到端闭环 7 条（见下） |

## Requirements

### 功能需求

1. **数据模型**：`Conversation` 与 `ConversationEntity` 新增 `isArchived`（默认 false）+ `archivedAt`（默认 0）。
2. **持久化迁移**：AutoMigration v23→v24，两列带 `defaultValue`。
3. **归档动作**：主列表项长按菜单新增"归档"，调用 `archiveConversation(id)`，置 `isArchived=true` + `archivedAt=now()`，不改 `updateAt`、不动 FTS、不动会话文件。
4. **活跃视图过滤**：所有"活跃视图"查询统一 `WHERE is_archived=0`——含主列表、HistoryPage、`getConversationsOfAssistant*`、`searchConversations*`、`getPinnedConversations`、`getRecentConversations`、`searchMessages`（JOIN conversationentity）。
5. **归档区**：独立 `ArchiveScreen`，跨助手 `Flow<List<Conversation>>`，按 `archivedAt DESC`；不显示 pin 分组头；不 Paging。
6. **归档区交互**：
   - 项点击 → 打开会话（与主列表一致，可继续聊天）
   - 项菜单：还原、彻底删除
   - 顶部："全部还原"、"清空"（清空二次确认 dialog）
   - 归档区支持标题搜索（复用 `searchConversations`，`is_archived=1`）
7. **Drawer 入口**：`ChatDrawer` 始终显示"归档"项 + 数量角标（`BadgedBox`），点击 `navigate(Screen.Archive)`。
8. **自动还原契约**：`ConversationRepository.updateConversation` 检测到 `messageNodes` 变化时，自动置 `isArchived=false` + `archivedAt=0`（"归档=不活跃，再活跃则解档"）。
9. **Web API**：`ConversationRoutes` 的 list 端点默认过滤 `is_archived=0`，不新增端点。

### 非功能需求

- DB 迁移不可破坏现有数据（AutoMigration + 带 defaultValue）。
- 归档/还原是 O(1) 状态翻转，不复制会话数据。
- 归档区列表性能：预期单用户归档量级 < 1000，Flow 足够，无需 Paging。

## Acceptance Criteria

MVP 端到端闭环，以下 7 条全部满足才算完：

- [ ] **AC1 数据模型与迁移**：`Conversation`/`ConversationEntity` 含 `isArchived`+`archivedAt`；AutoMigration 23→24 成功，老用户升级不丢数据；`Migration_23_24Test` 通过。
- [ ] **AC2 活跃视图过滤**：归档后的会话从主列表、HistoryPage、活跃搜索结果中消失；`ConversationDAOTest` 覆盖。
- [ ] **AC3 主列表归档动作**：主列表项长按菜单含"归档"，点击后会话移出主列表、进入归档区；HistoryPage / 聊天页菜单无此动作。
- [ ] **AC4 Drawer 归档入口**：Drawer 始终显示"归档"+数量角标（空时为 0 或隐藏角标）；点击进入 `ArchiveScreen`。
- [ ] **AC5 归档区交互**：归档区列表按 `archivedAt DESC`、无 pin 分组；项可点击打开、菜单可还原/彻底删、顶部可"全部还原"/"清空"（清空二次确认）；标题搜索可用。
- [ ] **AC6 自动还原契约**：归档会话在归档区打开并继续聊天（发消息/编辑/重生成）后，`isArchived` 自动翻 false 并回到主列表；返回回归档区时该会话已消失（Flow 实时）；`ConversationRepositoryTest` 覆盖契约。
- [ ] **AC7 Web API 与搜索**：Web API list 过滤归档；活跃消息搜索不命中归档会话；归档区"搜归档"命中归档会话。

## Out of Scope

- 自动定时归档（如 N 天未活跃自动归档）
- 归档会话数量上限 / 配额
- 归档区导出
- 多选模式批量操作（仅做顶部"全部还原/清空"两个动作）
- Web API 的 archive/unarchive 端点
- 其余 locale（日/韩等）的本地化（后续 locale-tui 补）

## Risks

- **DB 迁移破坏性**：AutoMigration 失败会导致老用户升级崩溃。缓解：带 defaultValue、写 Migration_23_24Test。
- **自动还原契约漏改**：`updateConversation` 有多处调用路径，若漏掉某条路径则"聊了不还原"。缓解：在 Repository 单一入口处统一处理 + 单测覆盖。
- **FTS 搜索过滤**：`searchMessages` 当前不 JOIN conversationentity，改为 JOIN 可能影响性能。缓解：加索引、LIMIT 50 不变。
