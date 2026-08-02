# issue-220 design

## 边界

| 层 | 改动 |
|----|------|
| Settings / PreferencesStore | 两字段 + partial update |
| GenerationHandler / GenerationChunk | 可选暴露 stepIndex |
| ChatService | 检查点触发 + lastCheckpointStep 会话态 |
| Conversation / Entity | 检查点元数据 |
| ConversationRepository | 可选精简写路径（跳过 FTS） |
| UI 设置页 + Chat 进入提示 | Switch/间隔/Toast 或横幅 |

## 契约

### Settings
```kotlin
enableCheckpointCache: Boolean = false
checkpointStepInterval: Int = 8 // 仅允许 4|8|16|32，写时 coerce
```

### 检查点触发（ChatService）
```
on Messages chunk / 工具步完成:
  if (!settings.enableCheckpointCache) return
  if (stepIndex - lastCheckpointStep < N) return
  launch(persistenceMutex) {
    saveCheckpoint(conversation) // 标记 isCheckpoint=true, checkpointStep=stepIndex
    lastCheckpointStep = stepIndex
  } // 失败 log only
```

### 元数据（推荐）
- `Conversation` 增加：
  - `checkpointStep: Int? = null`（最近检查点步；完成态写 null 或另字段）
  - `snapshotKind: enum { Final, Checkpoint } = Final` 或 `isFinalSnapshot: Boolean = true`
- Room：JSON 列旁路或独立 column；旧数据缺省 = Final，零迁移崩溃。

### FTS
- 实现时测一次全量 FTS 耗时；若 > 可接受阈值，检查点路径 `updateConversation(skipFts=true)`，生成成功/取消时再全量 FTS。
- 默认先尝试复用 `saveConversation`；热路径再精简。

### 步数
- **推荐**：`GenerationChunk.Messages` 增加 `stepIndex: Int`（Handler 已有循环变量），ChatService 直接读。改动面小、计数准。

### 恢复提示
- ChatPage/ChatVM 在 conversation 首次 hydrate 后若 `snapshotKind==Checkpoint`，Snackbar/横幅一次；标记已提示避免旋转重复。

## 数据流

见 issue body 流程图；实现与之一致。

## 取舍

| 方案 | 结论 |
|------|------|
| 每步写 | 拒绝 |
| 仅内存 | 现状，拒绝 |
| N 步全量检查点 | **采用** |
| 复用 SubagentContextCache 表 | 不直接复用表；复用异步+revision 思想 |

## 兼容 / 回滚

- 默认关；开才写新字段。
- 回滚：关开关 + 忽略新字段读。

## 风险

- FTS 跳过导致搜索暂时旧：可接受实验性；结束时重建。
- 与 onSuccess 落盘竞态：mutex + revision。
- 多对话：每会话 lastCheckpointStep 独立。
