# Design: 助手级持久化工作区目录与路径校验

## 关键发现（PRD 修正）

| PRD 原表述 | 代码实际情况 | 设计调整 |
|-----------|-------------|---------|
| `AssistantEntity` 增加 `default_workspace_cwd` 列 | **无 `AssistantEntity`**；`Assistant` 存于 DataStore JSON | `Assistant` 数据类加字段 + `@Serializable` 默认值，无需 Room 迁移 |
| 数据库迁移脚本 | `Assistant` 不在 Room 中 | 仅 JSON 反序列化兼容（`@Serializable` 默认值即可） |
| `ConversationEntity` 受影响 | `workspace_cwd` 列自 DB v22 已存在 | **无需新增 Room 迁移**；仅运行时解析逻辑变更 |

## 模型变更

### `Assistant.kt` — 新增字段

```kotlin
@Serializable
data class Assistant(
    // ... existing fields ...
    val defaultWorkspaceCwd: String? = null,  // R1: 助手级默认 cwd
)
```

- **序列化兼容**：旧 JSON 无此 key → kotlinx.serialization 使用 `null` 默认值
- **无需** `SettingsJsonMigrator` 版本步（除非后续要迁移旧数据语义）

### `Conversation` — 无模型变更

`workspaceCwd: String? = null` 保持不变。运行时继承通过解析函数实现，不修改存储值。

## 核心设计：有效 CWD 解析

### 单一解析函数

```kotlin
// 新文件: app/.../data/model/WorkspaceCwdResolver.kt (或放入 Assistant.kt 伴生)
fun resolveEffectiveWorkspaceCwd(
    conversation: Conversation,
    assistant: Assistant,
): String {
    val stored = conversation.workspaceCwd ?: assistant.defaultWorkspaceCwd
    return normalizeWorkspaceCwd(stored ?: "/workspace")
}

fun normalizeWorkspaceCwd(path: String): String {
    // 1. 反斜杠 → 正斜杠
    // 2. 规范化: 去多余斜杠、解析 ".." 穿越
    // 3. 约束: 必须以 /workspace 或 /workspace/ 开头（R3: FILES 区域）
    // 4. 越界回落: 不满足前缀约束 → "/workspace"
}
```

### R2 消费点替换

替换所有直接读取 `conversation.workspaceCwd` 为 `resolveEffectiveWorkspaceCwd(conversation, assistant)`:

| 消费点 | 文件:行 | 变更 |
|--------|---------|------|
| GenerationHandler | `ChatService.kt:653` | 传有效 cwd |
| createWorkspaceToolsIfReady | `ChatService.kt:683-687` | 传有效 cwd |
| 子代理 tools | `ChatService.kt:737` | 传有效 cwd |
| TransformerContext | `Transformer.kt:19` → `ChatService` 构造处 | 传有效 cwd |
| FilesPicker 展示 | `FilesPicker.kt:261-262` | 展示有效 cwd |
| SubagentPermissionBuilder | `SubagentPermissionBuilder.kt:102-107` | 传有效 cwd |
| SubagentHost | 对应调用处 | 传有效 cwd |

### 存储值 vs 运行时值 分离

- **存储值**（DB / DataStore）：`conversation.workspaceCwd`、`assistant.defaultWorkspaceCwd` — 可 `null`
- **运行时有效值**：`resolveEffectiveWorkspaceCwd(...)` — 始终非 null，默认 `"/workspace"`

UI 和工具链只消费运行时有效值；仅在用户主动设置/修改 cwd 时写存储值。

## UI 变更

### CWD 选择器增加「设为助手默认」

**位置**: `WorkspaceCwdPicker.kt`

- 在 Sheet 底部增加 Action: 「设为助手默认」（仅在 assistant 绑定 workspace 时显示）
- 点击后调用 `onSetAssistantDefault(currentBrowsePath)`
- 回调签名变更: `onSelectCwd: (String?) -> Unit` → 增加 `onSetAssistantDefault: ((String?) -> Unit)? = null`
- 写入: `assistant.copy(defaultWorkspaceCwd = normalizedPath)` → `onUpdateAssistant(...)`
- 现有「重置」按钮仍清空**会话级** cwd（`onSelectCwd(null)`），不清空助手默认

### FilesPicker 集成

**位置**: `FilesPicker.kt:267-275`

- 传递 `onSetAssistantDefault` 回调给 `WorkspaceCwdPickerSheet`
- 展示区标注来源：会话级 → 无标注；助手默认级 → 小字 `(default)`

### 换绑 workspace 行为

**`FilesPicker.kt:369-375`**: 现有逻辑清空会话 `workspaceCwd` — **保持不变**。

是否清空 `assistant.defaultWorkspaceCwd`？**不清空**：助手默认 cwd 是助手配置的一部分，与具体 workspace 实例无依赖（同 workspace 下子目录可复用）。

## 路径规范化（R3）

### 规则

1. `\\` → `/`，去除首尾空白
2. 路径连续 `/` → 单 `/`
3. 逐段解析 `..` 穿越
4. **约束**: 规范化后必须前缀匹配 `/workspace` 或 `/workspace/`
5. 越界 → 回落 `/workspace`（不抛异常，因为 picker 无法产生越界路径，此为安全兜底）

### 规范化入口点

- `normalizeWorkspaceCwd(path)` — 上层统一入口
- `WorkspaceCwdPicker` 返回值已是绝对路径格式（`toAbsolutePath`），仅需 `normalizeWorkspaceCwd` 后写入
- `WorkspaceTools.createWorkspaceTools(cwd)`：传入已规范化的 cwd

### LINUX 区域（v1）

- 不硬禁止，仅文档化 LINUX 区域的路径语义
- `WorkspaceReminderTransformer` 提示词中可标注当前 cwd 是否在 FILES 区域内

## 数据流图

```
新建会话:
  Conversation.ofId → workspaceCwd=null
  → resolveEffectiveWorkspaceCwd(conv, assistant) → assistant.defaultWorkspaceCwd ?: "/workspace"
  → 工具链使用有效值

已有会话:
  conv.workspaceCwd != null → 直接使用
  conv.workspaceCwd == null → 同上回退

CWD 变更:
  Picker 选中 → conv.copy(workspaceCwd = newCwd) → onUpdateConversation
  "设为助手默认" → assistant.copy(defaultWorkspaceCwd = newCwd) → onUpdateAssistant
  重置 → onSelectCwd(null) → conv.workspaceCwd = null → 回退到助手默认
```

## 迁移与兼容

| 存储 | 变更 | 兼容策略 |
|------|------|---------|
| DataStore `Assistant` JSON | 新增 `defaultWorkspaceCwd` key | `@Serializable` 默认值 `null`，旧数据自动兼容 |
| Room `ConversationEntity` | **无变更** | `workspace_cwd` 列自 v22 已存在 |
| Room 版本 | **不 bump**（无 schema 变更） | — |

无破坏性变更，无需数据迁移脚本。

## 兼容性与回滚

- **前向兼容**：旧版本忽略 `defaultWorkspaceCwd` key（DataStore 中未知 key 被跳过 by kotlinx.serialization）
- **后向兼容**：新版本读旧数据 → `defaultWorkspaceCwd = null` → 行为等同现状
- **回滚**：删除新字段即可；旧版本无 `defaultWorkspaceCwd` = 无助手默认cwd = 现状
