# issue-217+216 design

## 边界

| 层 | 改动 |
|----|------|
| transformers | VariableMacroTransformer + UpdateVariableOutputTransformer |
| Conversation / MessageNode | variables + 分支快照 |
| Room / Repository | JSON 编解码 + 原子更新 |
| Assistant | 实验开关过渡字段 |
| GenerationHandler 管线注册 | input/output 顺序 |
| ConversationDrawerContent | 变量节 UI |
| （可选）Tools | VariableTools |

## 契约

### 管线顺序（input）
```
… → PromptInjectionTransformer → VariableMacroTransformer → PlaceholderTransformer → …
```
- 变量宏先于 `{{user}}/{{char}}`，使 setvar 值内占位符仍可被 Placeholder 处理（若宏移除后残留）。

### 分支变量（关键）
仅 `Conversation.variables` 顶层 **不够**：多 MessageNode 备选会互相污染。

**推荐**：
```kotlin
// Conversation
variables: Map<String, String> = emptyMap()  // 当前选中分支的工作副本

// MessageNode 或 per-message 元数据
variableSnapshot: Map<String, String>? = null  // 该备选生成结束时快照
```
- 新备选开始/结束：写入该 message/node 的 snapshot。
- selectIndex 变更：`conversation.variables = node.selected.variableSnapshot ?: conversation.variables`。
- fork：复制 conversation.variables（及必要时 nodes 上 snapshot）。

若实现复杂度过高的 MVP 折中（需在 implement 标明）：先只做 conversation 级 map + fork 复制，**文档声明分支隔离为二期**——但 issue AC 要求分支语义，**默认不做折中**。

### 开关
```kotlin
// 推荐直接前瞻 #215
Assistant.experimentalFeatureOverrides: Map<String, Boolean> = emptyMap()
// 读：overrides["variable_system"] ?: false

// 或最小
enableVariableSystem: Boolean = false
```
PreferencesStore `updateAssistantConfig` / 专用 partial 写。

### VariableRepository / 写路径
- 所有 setvar/addvar/MVU/UI 编辑走同一原子更新 conversation 方法（Room 事务或 mutex），禁止 read-modify-write 无锁。

### MVU
- 正则/解析器提取块 → JSON Patch 应用 → 剥离文本。
- 仿 HookOutputParser 严格 key；失败 no-op 保留原文。

### UI
- 抽屉节读 `conversation.variables`；写调 VM→Repository。
- 开关关不组合。

## 数据流

```
发送: Injection 拼装 → 变量宏（读写 variables，移除 set/add）→ Placeholder → 模型
完成: onGenerationFinish → MVU 解析 → 原子写 variables → 剥离 → 分支 snapshot
UI: 抽屉编辑 → 原子写
分支: selectIndex → 恢复 snapshot
```

## 与 #215 契约

- 消费点只依赖 `isFeatureEnabled(assistant, "variable_system")` 单一函数。
- #215 落地后该函数改为读实验注册表；本任务实现函数体读过渡字段。

## 取舍

| 方案 | 结论 |
|------|------|
| 变量进 MemoryTable | 拒绝（过重/UI 混淆） |
| Hook 额外模型调用更新 | 拒绝 |
| 仅工具通道 | 拒绝作主通道 |
| Conversation 顶层 only | 不足分支 AC → 加 snapshot |

## 兼容 / 回滚

- 默认关；关则 transformers 短路。
- 回滚：关字段 + 跳过 transformer 注册。

## 风险

- 流式半块；嵌套递归深度；addvar 膨胀；JSON 损坏；备份范围；分支 snapshot 旧数据 null。
