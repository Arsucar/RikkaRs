# Implement: 助手级持久化工作区目录与路径校验

## 前置条件

- PRD 已收敛 ✅
- design.md 已编写 ✅
- 任务状态: planning → 需 `task.py start` 后开始

## 实现清单

### Step 1: 添加 `normalizeWorkspaceCwd` / `resolveEffectiveWorkspaceCwd` 工具函数

**文件**: `app/src/main/java/me/rerere/rikkahub/data/model/WorkspaceCwdUtils.kt`（新建）

- [ ] 创建 `fun normalizeWorkspaceCwd(path: String): String`
  - 反斜杠→正斜杠、trim、合并连续 `/`
  - 逐段解析 `..`
  - 前缀约束 `/workspace`，越界回落 `/workspace`
- [ ] 创建 `fun resolveEffectiveWorkspaceCwd(conversation: Conversation, assistant: Assistant): String`
  - `conversation.workspaceCwd ?: assistant.defaultWorkspaceCwd ?: "/workspace"` → 经 normalize
- [ ] 单元测试: `WorkspaceCwdUtilsTest.kt`
  - 正常路径、`..` 穿越、越界回落、null 各级 fallback、多余斜杠

### Step 2: `Assistant` 模型新增字段

**文件**: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`

- [ ] 添加 `val defaultWorkspaceCwd: String? = null`
- [ ] 确认 `@Serializable` 默认值反序列化兼容（无需手动 JSON migrator）

### Step 3: `ChatService` — 统一有效 CWD 消费

**文件**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

- [ ] L653: `GenerationHandler` — 使用 `resolveEffectiveWorkspaceCwd(conversation, assistant)`
- [ ] L683-687: `createWorkspaceToolsIfReady` 调用 — 使用有效 cwd
- [ ] L737: 子代理 tools — 使用有效 cwd
- [ ] L806-821: `createWorkspaceToolsIfReady` 内部实现 — 入参 `cwd` 已是有效值
- [ ] 新建会话 (L419-423): 无需修改，运行时自动 resolve

### Step 4: `TransformerContext` — 传有效 cwd

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt`
**调用处**: `ChatService.kt` 构造 `TransformerContext` 处

- [ ] `TransformerContext.workspaceCwd` 改为有效值 (或新增 `effectiveWorkspaceCwd` 字段)
- [ ] 确保 `WorkspaceReminderTransformer` 使用有效值

### Step 5: 子代理 CWD 传递

**文件**: `SubagentPermissionBuilder.kt`, `SubagentHost.kt`

- [ ] 传入有效 cwd 而非 `conversation.workspaceCwd`

### Step 6: CWD 选择器 UI — 「设为助手默认」

**文件**: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/WorkspaceCwdPicker.kt`

- [ ] 新增参数 `onSetAssistantDefault: ((String?) -> Unit)? = null`
- [ ] 底部增加 Action Row: "设为助手默认"
  - 仅 `onSetAssistantDefault != null` 时显示
  - 点击: `onSetAssistantDefault(toAbsolutePath(browsePath))`
- [ ] 现有「重置」按钮: 语义不变（清空会话级 cwd）

### Step 7: FilesPicker 集成

**文件**: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt`

- [ ] L261-262 展示: 使用 `resolveEffectiveWorkspaceCwd` 展示有效 cwd
  - 若 `conversation.workspaceCwd != null` → 无标注
  - 若 `conversation.workspaceCwd == null && assistant.defaultWorkspaceCwd != null` → 小字标注 `(default)`
- [ ] L267-275: 传递 `onSetAssistantDefault` 回调
  - `onSetAssistantDefault = { newDefault -> onUpdateAssistant(assistant.copy(defaultWorkspaceCwd = newDefault)) }`
- [ ] L369-375 换绑 workspace: 保持清空会话 cwd，**不**清空 `assistant.defaultWorkspaceCwd`

### Step 8: 存储验证

- [ ] 确认 `PreferencesStore` 序列化/反序列化 `Assistant` 含新字段无报错
- [ ] 确认旧 DataStore JSON（无 `defaultWorkspaceCwd` key）反序列化 → `null`

## 验证命令

```bash
.\gradlew :app:compileDebugKotlin --no-daemon   # 编译验证
.\gradlew test                                  # 单元测试（含新的 WorkspaceCwdUtilsTest）
.\gradlew lint                                  # Lint 检查
```

## 回滚点

| Step | 回滚方式 |
|------|---------|
| 1 (工具函数) | 删除 `WorkspaceCwdUtils.kt` + 测试 |
| 2 (模型字段) | 移除 `defaultWorkspaceCwd`，JSON 兼容无损 |
| 3-5 (消费点替换) | 恢复原始 `conversation.workspaceCwd` |
| 6-7 (UI) | 移除 `onSetAssistantDefault` 回调 + UI 元素 |

## PRD Acceptance Criteria 对照

| AC | 覆盖步骤 |
|----|---------|
| `Assistant` 含 `defaultWorkspaceCwd` 字段 | Step 2 |
| 新建会话继承 `defaultWorkspaceCwd` | Step 3 (运行时 resolve) |
| 已有会话 cwd 不被助手级修改自动覆盖 | Step 3 (仅 resolve, 不写 DB) |
| 优先级 conversation > assistant > /workspace | Step 1 (`resolveEffectiveWorkspaceCwd`) |
| CWD 选择器「设为助手默认」 | Step 6, 7 |
| DB 迁移脚本增加列 | **不适用** — DataStore JSON, 非 Room (PRD 修正) |
| 路径 `..` 规范化后落在 `/workspace/` 内 | Step 1 (`normalizeWorkspaceCwd`) |
| 换绑 workspace 清空行为不变 | Step 7 |
| 现有测试通过 | 各 Step 验证命令 |
