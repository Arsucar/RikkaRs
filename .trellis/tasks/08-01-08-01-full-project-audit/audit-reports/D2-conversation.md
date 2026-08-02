# D2 审计报告：会话 / 助手管理（Conversation & Assistant Management）

**域**: D2  
**范围**: 会话 CRUD、MessageNode 分支、助手配置 CRUD、切换/导航、状态恢复、数据流、隔离与分页  
**方式**: 只读静态分析（无 Gradle / 无运行时）  
**基线工作区**: `bionic-dingo` worktree  
**日期**: 2026-08-01  

---

## 1. 链路梳理

### 1.1 会话（Conversation）主链路

```
UI (ChatPage / ChatDrawer / HistoryPage)
  → ChatVM / ChatDrawerVM / HistoryVM
    → ChatService (内存 ConversationSession + 生成编排)
      → ConversationRepository
        → ConversationDAO / MessageNodeDAO (+ FavoriteDAO / FTS / MessageStats)
          → Room (ConversationEntity + message_node CASCADE)
```

| 操作 | 入口 | 持久化路径 | 备注 |
|------|------|------------|------|
| 新建 | `navigateToChatPage()` 随机 Uuid → `ChatService.initializeConversation` | 空会话**不落库**（`saveConversation` 早退） | 首条消息 / 有实质字段后才 insert |
| 打开/恢复 | `RouteActivity` `lastConversationId` 或新建 Uuid | `getConversationById` + `hydrateConversationFromDb` | 生成中 skip 覆盖 |
| 发送 | `ChatVM.handleMessageSend` → `ChatService.sendMessage` | `saveConversation` 后生成 | MessageNode 追加 USER |
| 分支切换 | `ChatMessageBranchSelector` → `onUpdateMessage` → `updateConversation` + `saveConversationAsync` | 整对象 save | 未走 `selectMessageNode` API |
| 编辑消息 | `editMessage` | 同 node 追加版本 + `selectIndex = messages.size` | 分支增长 |
| 删除消息 | `deleteMessage` | `buildConversationAfterMessageDelete` | **selectIndex 调整有缺陷** |
| 置顶 | `ChatVM.updatePinnedStatus` → `togglePinStatus` | **仅 UPDATE is_pinned 列** | 与活跃 session 整对象 save 冲突 |
| 重命名/标题 | `updateTitle` / `generateTitle` | `saveConversation` | 标题生成从 DB 重载再整存 |
| 删除会话 | `deleteConversation` | 事务删实体 + CASCADE nodes + FTS + 记忆表 + 文件 | 当前会话会 `navigateToChatPage` 新 Uuid |
| 移助手 | `moveConversationToAssistant` | 改 assistantId、清空 folderId、relink 记忆表 | 当前会话同步 `updateAssistant` |
| 移文件夹 | `moveConversationToFolder` | 先同步 session 内存再写 folder_id | 已修「整对象覆盖」类问题 |
| Fork | `forkConversationAtMessage` | `insertForkConversation` + 复制 tag 关系 | 复制对话级记忆表 best-effort |

### 1.2 助手（Assistant）主链路

```
UI (AssistantPage / AssistantDetail* / AssistantPicker)
  → AssistantVM / AssistantDetailVM / ChatVM.switchAssistant
    → SettingsStore (DataStore JSON: ASSISTANTS / SELECT_ASSISTANT)
      旁路: MemoryRepository / MemoryTableRepository / ConversationRepository / FilesManager
```

| 操作 | 入口 | 持久化 | 备注 |
|------|------|--------|------|
| 创建 | `AssistantVM.addAssistant` | `settings.copy(assistants + …)` 全量 `update` | `isArchived=false` |
| 编辑 | `AssistantDetailVM.update` → `updateAssistantConfig` | 单助手原子写 ASSISTANTS | 较全量 update 安全 |
| 复制 | `copyAsActiveClone` | 新 id、`(Clone)` 名、Dummy 图头像 | 不复制 Image avatar 文件 |
| 归档 | `setAssistantArchived` | `writeAssistantArchiveState` | 禁止归档最后一个 active |
| 删除 | `removeAssistant` | 从 list 移除 + 删记忆/记忆表/全部会话/文件 | **未重选 assistantId** |
| 切换 | `AssistantSwitchCoordinator` | `updateAssistant` + 导航最新会话或新 Uuid | generation 防竞态，有单测 |

### 1.3 导航 / 状态恢复

- 冷启动：`create_new_conversation_on_start` → 新 Uuid；否则 `lastConversationId` SharedPreferences（`ChatVM.init` 写入）。
- 导航：`Navigator.clearAndNavigate(Screen.Chat)`，单栈 Chat。
- ChatDrawer：Paging + 助手隔离；`SavedStateHandle` 保存 tag 筛选与滚动位置；文件夹筛选**不在** SavedState（进程死后丢）。
- 会话详情在 `ChatService` 的 `ConversationSession`（进程级）；ViewModel 仅持 reference count。

### 1.4 隔离 / 排序 / 分页

- **助手隔离**：会话按 `assistant_id`；抽屉 `ConversationFilter.assistantId`；切换助手重置文件夹。
- **排序**：`is_pinned DESC, update_at DESC`（filter 查询额外 `id DESC`）。
- **分页**：抽屉 `PagingConfig(pageSize=20)` + light entity；History 用**全量** `Flow<List>`。
- **MessageNode**：分页读 64、写 batch 64；过大 blob 单行 fallback。

---

## 2. 问题清单

### F2-1 — 删除消息时 selectIndex 未按被删下标回退

- **文件:行**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt:2884-2893`
- **严重度**: **HIGH**
- **描述**: 从分支节点删除某条非当前选中消息时，仅用 `coerceAtMost(lastIndex)`，未在「被删 index < 原 selectIndex」时减 1，导致选中跳到错误分支版本。
- **证据**:
```kotlin
val nextMessages = node.messages.filterNot { it.id == messageId }
// ...
val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
node.copy(messages = nextMessages, selectIndex = nextSelectIndex)
```
例：`[A,B,C]` 选中 B(index=1)，删 A → 应变为选中 B(index=0)，实际仍为 index=1 → 选中 C。
- **建议修复**: 记录被删在原 list 的 index；若 `deletedIndex < selectIndex` 则 `selectIndex - 1`，再 `coerceIn`；若删的是当前选中则 `coerceAtMost`。

---

### F2-2 — 置顶仅写 DB 列，活跃 session 整对象 save 可覆盖

- **文件:行**: `ConversationRepository.kt:456-460`；`ChatVM.kt:590-593`；对比已修文件夹路径 `ChatService.kt:2546-2550`
- **严重度**: **HIGH**
- **描述**: `togglePinStatus` 只 `UPDATE is_pinned`。活跃会话内存 `Conversation.isPinned` 仍为旧值；之后任意 `saveConversation(session.state)`（流式结束、分支切换、改标题等）会把旧 pin 写回。Web 路径 `ConversationRoutes` 用 `saveConversation(...copy(isPinned=...))` 正确。
- **证据**:
```kotlin
// Chat 抽屉
conversationRepo.togglePinStatus(conversation.id) // 仅列更新

// Web API（正确）
chatService.saveConversation(uuid, conversation.copy(isPinned = !conversation.isPinned))
```
- **建议修复**: 与 folder 一致：经 `ChatService` 更新 session `isPinned` 再落库，或 `togglePin` 后若 session 存在则 `updateConversationState`。

---

### F2-3 — generateTitle 从 DB 整对象回写，可冲掉并发内存变更

- **文件:行**: `ChatService.kt:2084-2089`
- **严重度**: **HIGH**
- **描述**: 标题生成结束后 `getConversationById` 再 `saveConversation(it.copy(title=...))`。`saveConversation` 会 `updateConversation` **整表替换** session。若标题请求进行期间用户已改分支/编辑/隐藏消息且仅在内存或尚未与该 DB 快照一致，标题写回会丢失这些变更。
- **证据**:
```kotlin
conversationRepo.getConversationById(conversation.id)?.let {
    saveConversation(conversationId, it.copy(title = result.choices[0].message?.toText()?.trim() ?: ""))
}
```
- **建议修复**: 只更新 title 字段：session `updateState { copy(title=...) }` + DAO 单列 update；或 CAS 合并 title 到最新 session 后再 persist。

---

### F2-4 — 删除当前选中助手后 assistantId 悬空，抽屉列表变空

- **文件:行**: `AssistantVM.kt:57-70`；`PreferencesStore.kt:1365-1367`；`ChatDrawerVM.kt:51-53,88-95`
- **严重度**: **HIGH**
- **描述**: `removeAssistant` 只从 `assistants` 列表移除，不更新 `Settings.assistantId`。DataStore 仍指向已删 id。`getCurrentAssistant()` UI 会 fallback，但抽屉 `assistantIdFlow` 用原始 `settings.assistantId` 过滤会话 → **空列表**，直到用户再选助手。
- **证据**:
```kotlin
settingsStore.update(settings.copy(assistants = settings.assistants.filter { it.id != assistant.id }))
// 无: updateAssistant(fallbackId) 或 normalizeAssistantLifecycle
```
- **建议修复**: 删除后若 `assistantId == removed`，写入下一个 active（复用 `normalizeAssistantLifecycle` / archive 逻辑）；可选导航到该助手最新会话。

---

### F2-5 — SettingsStore.update 非原子 RMW，并发易丢配置

- **文件:行**: `PreferencesStore.kt:586-690`
- **严重度**: **HIGH**
- **描述**: `update(fn)` 读 `settingsFlow.value` 后全量重写几乎所有 preference keys。`AssistantVM.addAssistant` / `removeAssistant` / 多处 detail 开关与 `updateAssistantConfig` 等并发时，后写覆盖先写 → **助手配置/全局设置丢失**。`updateAssistantConfig` / `updateAssistant` 已走 `dataStore.edit` 局部写，但大量路径仍用全量 `update`。
- **证据**:
```kotlin
suspend fun update(fn: (Settings) -> Settings) {
    update(fn(settingsFlow.value)) // 非 DataStore transform 原子读改写
}
suspend fun update(settings: Settings) {
    settingsFlow.value = settings
    dataStore.edit { /* 写满 ASSISTANTS、PROVIDERS、… */ }
}
```
- **建议修复**: 助手 list 变更用 `edit { decode → transform → encode }`；全量 `update` 仅用于明确「整包导入」；或合并队列串行化 Settings 写。

---

### F2-6 — MessageNode.currentMessage / UI 下标未防护可崩溃

- **文件:行**: `Conversation.kt:126-130`；`ChatMessage.kt:135`
- **严重度**: **CRITICAL**（损坏/迁移异常数据时）
- **描述**: `currentMessage` 在 `selectIndex` 越界或空 messages 时 **直接 throw**。UI `node.messages[node.selectIndex]` 同样无防护。损坏 JSON、半截 sync、手工改库均可导致打开会话崩溃。`sanitizeInvalidMessages` 仅部分 hydrate 路径调用。
- **证据**:
```kotlin
val currentMessage get() = if (messages.isEmpty() || selectIndex !in messages.indices) {
    throw IllegalStateException("MessageNode has no valid current message: ...")
} else messages[selectIndex]
// ChatMessage:
val message = node.messages[node.selectIndex]
```
- **建议修复**: getter 返回可空或安全 coerce；UI 用 `getOrNull`；加载时统一 `sanitizeInvalidMessages`；持久化前校验。

---

### F2-7 — 空新会话 save 早退导致进程死亡丢失会话级设置

- **文件:行**: `ChatService.kt:2621-2628`；`ChatVM.kt:744-749`；`ChatPage.kt:797-799`
- **严重度**: **MEDIUM**
- **描述**: 不存在且 title 空、nodes 空、`chatModelId==null` 时不 insert。内存可改 `memoryTableIsolation` / `customSystemPrompt` / injections，但杀进程后丢失（注释承认依赖首条消息）。用户若只改会话设定不发消息，体验为「设置没了」。
- **证据**:
```kotlin
if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()
    && conversation.chatModelId == null) {
    return@withLock // 新会话且为空时不保存
}
```
- **建议修复**: 若存在非默认会话级字段（isolation、custom prompt、injections、folder…）则强制 insert；或显式「草稿会话」表。

---

### F2-8 — deleteConversationOfAssistant / History 全量加载

- **文件:行**: `ConversationRepository.kt:396-399`；`HistoryVM.kt:29-33`
- **严重度**: **HIGH**（大量会话时性能/OOM）
- **描述**: 删助手时 `getConversationsOfAssistant().first()` 拉全表实体（虽 nodes 为空 list，仍一次载入全部 light 行）再逐条 delete（每条可能再 load 全 nodes 清文件）。History 页对当前助手 **不分页** 订阅全列表。
- **证据**:
```kotlin
getConversationsOfAssistant(assistantId).first().forEach { deleteConversation(it) }
// HistoryVM
conversationRepo.getConversationsOfAssistant(assistant?.id ?: Uuid.random())
```
- **建议修复**: 按 id 分页批量删；文件清理可异步；History 改用 Paging。

---

### F2-9 — 置顶查询 getLatestActive 忽略 pin，切换助手落点可能非预期

- **文件:行**: `ConversationDAO.kt:30-35`；`AssistantSwitchCoordinator.kt:63-64`
- **严重度**: **MEDIUM**
- **描述**: 切换助手导航到 `ORDER BY update_at DESC LIMIT 1`，**不优先 pin**。用户期望「回到置顶重要会话」时可能落到最近更新的未置顶会话。
- **建议修复**: 与列表一致：`ORDER BY is_pinned DESC, update_at DESC LIMIT 1`。

---

### F2-10 — AssistantDetailVM 找不到助手时暴露空 Assistant()

- **文件:行**: `AssistantDetailVM.kt:109-115`
- **严重度**: **MEDIUM**
- **描述**: id 无效时 `Assistant()`（随机新 id）。UI 仍可编辑；`updateAssistantConfig` 因 id 不在 list 静默 no-op，用户以为保存成功。
- **建议修复**: 显式 NotFound UI / 返回上一页；禁止对占位对象调用 update。

---

### F2-11 — 分支切换双路径，未统一 selectMessageNode

- **文件:行**: `ChatPage.kt:759-770`；`ChatService.kt:2799-2824`
- **严重度**: **LOW**
- **描述**: UI 本地改 node + `saveConversationAsync`；另有校验完备的 `selectMessageNode`。行为大体等价，但校验/错误处理不一致，后续易分叉。
- **建议修复**: UI 调用 `chatService.selectMessageNode`。

---

### F2-12 — ChatDrawer 文件夹筛选未进 SavedStateHandle

- **文件:行**: `ChatDrawerVM.kt:56-57,175-178`
- **严重度**: **LOW**
- **描述**: tag 与滚动有 SavedState；`_selectedFolderId` 仅内存。配置变更/进程死后回到「未归类」。
- **建议修复**: 与 tag 一样写入 SavedStateHandle。

---

### F2-13 — copyAssistant 丢弃 Image 头像且不复制关联资源深层状态

- **文件:行**: `AssistantVM.kt:100-104`
- **严重度**: **LOW**
- **描述**: Image avatar → Dummy，避免共享文件被删；用户感知为「克隆没头像」。记忆/记忆表/会话有意不复制（正确）。
- **建议修复**: 可选复制头像文件到新 URI；文档化行为。

---

### F2-14 — initializeConversation 打开会话强制 updateAssistant

- **文件:行**: `ChatService.kt:553`
- **严重度**: **LOW**（偏产品）
- **描述**: 打开任意会话会把全局 selected assistant 设为会话所属助手。从搜索/收藏跨助手打开会改全局选择与抽屉列表范围——多半是有意隔离，但可能让用户困惑。
- **建议修复**: 保持或增加「仅查看不切换助手」模式。

---

### F2-15 — Settings 全量 update 与 updateAssistantConfig 混用导致短暂 UI 不一致

- **文件:行**: 多处 `AssistantDetailVM` / `AssistantVM`
- **严重度**: **MEDIUM**
- **描述**: 部分字段走 `updateAssistantConfig`（仅 ASSISTANTS key），部分走 `settingsStore.update { map assistants }`。与 F2-5 叠加时，内存 `settingsFlow` 乐观值与 DataStore 回流可能短暂分叉。
- **建议修复**: 统一助手写路径为 `updateAssistantConfig` 或原子 edit。

---

## 3. 亮点 / 可复用

1. **ConversationSession**：refCount + idle 回收、`persistenceMutex` 与内存流式更新分离、revision/CAS（压缩路径）——高质量会话运行时模型。
2. **AssistantSwitchCoordinator**：generation + mutex，防快速切换错导航；单测覆盖充分。
3. **MessageNode 仓储**：分页读、blob 过大单行 fallback、诊断日志阈值、`computeNodeSyncOps` 纯函数可测。
4. **LightConversationEntity + Paging**：抽屉列表不加载 messages，扩展性好。
5. **文件夹移动/删除**：明确记录并修复「内存 folderId 被整对象 save 覆盖」——同模式应推广到 pin（F2-2）。
6. **助手归档策略** `AssistantArchivePolicy`：最后 active 保护、选中迁移、reorder 只动 active——清晰可测。
7. **updateAssistantConfig / selectActiveAssistant**：DataStore 局部写，优于全量 Settings dump。
8. **Fork 事务内复制 tag relations** + 记忆表 best-effort 复制。
9. **sanitizeInvalidMessages / cleanStaleSubagentStreaming**：加载与生成边界的数据自愈意识。
10. **会话级 vs 助手级配置**：web search 绑 `conversation.assistantId` 而非全局 selected——隔离契约正确。

---

## 4. 遗漏与风险

| 风险 | 说明 |
|------|------|
| 无 DB 层 assistant 外键 | 助手在 DataStore，会话在 Room；删助手依赖应用层级联，中断会留孤儿会话 |
| DEFAULT_ASSISTANT 复活 | settings 映射会把缺失的 DEFAULT_ASSISTANTS 加回；UI 禁止删默认，一致 |
| 标题/建议并发 | 生成结束后并行 title+suggestion+semantic memory，共享 save 路径，放大 F2-3 |
| 未审计 Web 全量 CRUD | Web pin/title 部分正确；与 App 路径行为不一致（pin） |
| 测试缺口 | 缺：delete 调整 selectIndex、pin+活跃 session、删助手后 assistantId、空会话设定持久化 |
| History 可扩展性 | 与抽屉分页能力不对称 |
| `getPinnedConversations` 全局 | 跨助手置顶列表，History 若混用需注意隔离 |
| 进程级 ChatService sessions | 多 Activity/进程假设下需再评估；当前单进程 OK |

---

## 5. 严重度汇总

| ID | 严重度 | 一句话 |
|----|--------|--------|
| F2-6 | CRITICAL | 非法 selectIndex / 空 node 可崩溃 |
| F2-1 | HIGH | 删分支消息 selectIndex 错位 |
| F2-2 | HIGH | 置顶被 session 整存覆盖 |
| F2-3 | HIGH | 标题生成整存冲掉并发编辑 |
| F2-4 | HIGH | 删当前助手后抽屉空列表 |
| F2-5 | HIGH | Settings 全量 RMW 丢更新 |
| F2-8 | HIGH | 删助手/History 全量加载 |
| F2-7 | MEDIUM | 空新会话设定不落库 |
| F2-9 | MEDIUM | 切换助手忽略 pin 排序 |
| F2-10 | MEDIUM | 详情页幽灵 Assistant() |
| F2-15 | MEDIUM | 助手写路径不统一 |
| F2-11 | LOW | 分支 API 双路径 |
| F2-12 | LOW | 文件夹筛选无 SavedState |
| F2-13 | LOW | 克隆丢头像 |
| F2-14 | LOW | 打开会话强制切助手 |

**修复优先级建议**: F2-6 → F2-1 → F2-2 → F2-4 → F2-3 → F2-5 → F2-8 → 其余 MEDIUM/LOW。

---

## 6. 关键文件索引

- Models: `data/model/Conversation.kt`, `Assistant.kt`
- Repo/DAO: `data/repository/ConversationRepository.kt`, `data/db/dao/ConversationDAO.kt`, `MessageNodeDAO.kt`, entities
- Runtime: `service/ChatService.kt`, `ConversationSession.kt`
- UI: `ui/pages/chat/ChatVM.kt`, `ChatDrawerVM.kt`, `ChatPage.kt`, `ChatList.kt`, `AssistantSwitchCoordinator.kt`
- Assistant UI: `ui/pages/assistant/AssistantVM.kt`, `AssistantPage.kt`, `detail/AssistantDetailVM.kt`
- Settings: `data/datastore/PreferencesStore.kt`, `AssistantArchivePolicy.kt`
- Nav: `RouteActivity.kt`, `utils/ChatUtil.kt`

---

*本报告为只读审计产出，未修改生产代码、未执行编译。*
