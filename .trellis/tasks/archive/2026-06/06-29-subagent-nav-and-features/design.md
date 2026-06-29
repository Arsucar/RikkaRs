# Design: Subagent nav restructure + parallel/delegate/extraLocalTools

**Task:** `06-29-subagent-nav-and-features`
**Branch:** `release/rikka-arsucar`
**Status:** planning (design.md)
**Verified against:** working tree 2026-06-29 (see `research/code-facts-subagent-features-working-tree.md`)

本设计仅描述技术契约与流程；执行步骤见 `implement.md`。

---

## 0. 已确认的产品决策（锁定）

| # | 决策 | 来源 |
|---|------|------|
| D1 | delegate-only 模式下**完全排除 MCP**（对齐 sub；`mcpManager.getAllAvailableTools()` 在 delegate 分支跳过） | 用户确认 |
| D2 | `AssistantSubagentHubControls` **内联到 `AssistantSubagentPage.kt`**；删除 `AssistantSubagentHubSection.kt`；`AssistantDetailPage` 不再引用 | 用户确认 |
| D3 | extraLocalTools 编辑 UI 放在 `AssistantSubagentProfilePage` 的 `if (resolved.inheritTools)` 分支内、`excluded_tools` 下方；`!inherit` 时区块不显示（天然满足"忽略"语义） | 用户确认 |

---

## 1. 数据模型契约（`Assistant.kt` / `SubagentProfile.kt`）

### 1.1 `Assistant` 新增两字段

文件：`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`（当前 L50-L54 子代理字段块）

```kotlin
val enableSubagents: Boolean = false,
val subagentMaxDepth: Int = 2,
val parallelToolExecution: Boolean = false,      // 新增（D-parallel）
val subagentDelegateOnly: Boolean = false,        // 新增（D-delegate）
val subagentProfiles: List<SubagentProfile> = emptyList(),
val disabledBuiltinSubagents: Set<String> = emptySet(),
val disabledGlobalSubagents: Set<String> = emptySet(),
```

- **序列化兼容**：两字段 `Boolean = false`，kotlinx.serialization 对旧 JSON 缺字段自动取默认值。无需 `@SerialName`、无需迁移。
- **放置位置**：紧跟 `subagentMaxDepth` 之后，语义聚集；不破坏现有字段顺序（新增字段一律在末尾或紧邻同组，反序列化顺序无关，因按名匹配）。

### 1.2 `SubagentProfile` 新增字段

文件：`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt`（当前 L59-L84）

```kotlin
val extraLocalTools: List<LocalToolOption> = emptyList(),  // 新增
```

- **位置**：紧邻 `localTools` 字段之后（同组）。
- **类型**：`List<LocalToolOption>`，与 `localTools` 一致；`LocalToolOption` 已是 `@Serializable sealed class`（research L228）。
- **默认 `emptyList()`**：旧数据反序列化安全。

---

## 2. parallelToolExecution 运行时（复杂度：轻）

### 2.1 改动点（单文件单行）

文件：`app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` L257-L258

**现状：**
```kotlin
val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
val runInParallel = toolsToProcess.size > 1 && subagentCount > 1
```

**改后：**
```kotlin
val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
val runInParallel =
    (assistant.parallelToolExecution && toolsToProcess.size > 1) || subagentCount > 1
```

- 公式与 sub/master 完全一致（research 功能2 L78-81）。
- `executeSingleTool` (L412) 与 `coroutineScope { ... async ... awaitAll }` (L260-L266) 路径**已存在**，无需新增。

### 2.2 行为矩阵

| parallelToolExecution | toolsToProcess.size | subagentCount | runInParallel |
|---|---|---|---|
| any | 1 | — | false（串行） |
| false | >1 | ≤1 | false |
| false | >1 | >1 | **true**（多 spawn 强制并行） |
| true | >1 | any | **true** |
| true | 1 | — | false |

### 2.3 并发安全分析（不引入锁）

| 风险点 | 评估 | 处理 |
|--------|------|------|
| 同 conversation 并发写 workspace | 用户开启 parallel 即接受；sub 同承担 | **不加锁**（产品预期；非 spawn 工具并行乱序写属用户责任） |
| 同一 MCP 连接并发调用 | delegate 模式排除 MCP；非 delegate 模式并行多 MCP 调用 | **不加锁**（与 sub 一致；MCP SDK 内部应有连接复用保护） |
| `spawn_subagent` 并行 | 每个 spawn 独立 `SubagentHost.spawn` + 独立子循环；`updateSubagentProgress` 按 `currentToolCallId()` 区分 | 已有机制，**无需改**；验证 metadata key 不冲突（见验收） |
| `executeSingleTool` 共享审批态 | 每 tool 独立 `execute`；Denied/Answered/Pending 在并行前已统一处理 | 现有路径，**无需改** |

**结论**：本任务不引入并发原语；风险与 sub/master 对齐，属产品预期。

---

## 3. subagentDelegateOnly 运行时（复杂度：中）

### 3.1 delegate-only 白名单（D1 + 用户确认）

根代理（depth=0）启用 delegate 时，工具集为以下**只读 + 委派**子集：

| 来源 | 保留 | 排除 |
|------|------|------|
| Web 搜索 | ✅ `createSearchTools(settings)`（若 `settings.enableWebSearch`） | — |
| LocalToolOption 白名单 | ✅ 仅 `TimeInfo` / `Clipboard` / `Logs` / `AskUser` | ❌ `JavascriptEngine` / `Tts` / `ScreenTime` 及未来新增 |
| 最近对话引用 | ✅ `createConversationTools`（若 `assistant.enableRecentChatsReference`） | — |
| workspace | ✅ **只读**：`createWorkspaceToolsIfReady(..., readOnly = true)` | ❌ 可写 workspace 工具 |
| skills | ❌ 全部排除 | — |
| MCP | ❌ **全部排除**（D1） | — |
| 子代理 | ✅ `spawn_subagent`（经 `buildSubagentToolsForChat`） | — |

> **LocalToolOption 白名单定义**（约束 L120 要求写入 design）：
> ```kotlin
> private val DELEGATE_ALLOWED_LOCAL_TOOLS = setOf(
>     LocalToolOption.TimeInfo,
>     LocalToolOption.Clipboard,
>     LocalToolOption.Logs,
>     LocalToolOption.AskUser,
> )
> ```
> 位置：`ChatService.kt` 顶层私有常量（companion 或 file-private）。
> **理由**：与 sub 一致；这些是纯只读/交互类，无副作用；`ScreenTime` 不入白名单（sub 亦未纳入，且语义偏统计/可变）。

### 3.2 ChatService 根工具装配改造

文件：`app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` 根 `tools = buildList { ... }`（research L75-105，当前内联无 `buildCommonTools`）

**当前流程**（7 步内联）：
```
1. webSearch  2. localTools.getTools(assistant.localTools)  3. recentChats
4. workspace(可写)  5. skills  6. MCP  7. subagent
```

**改造方案（不抽 buildCommonTools，就地 if-else）**：在 `buildList` 内引入 `delegateOnly` 局部变量，根据它跳过/收紧各步：

```kotlin
val delegateOnly = assistant.enableSubagents && assistant.subagentDelegateOnly
val effectiveLocalTools = if (delegateOnly) {
    assistant.localTools.filter { it in DELEGATE_ALLOWED_LOCAL_TOOLS }
} else {
    assistant.localTools
}
val localToolNames = localTools.getTools(effectiveLocalTools)
```

各步骤行为：

| 步骤 | delegateOnly=false（现状） | delegateOnly=true |
|------|------|------|
| webSearch | 现状 | ✅ 保留 |
| localTools | 全量 | 仅白名单子集（上表） |
| recentChats | 现状 | ✅ 保留 |
| workspace | `createWorkspaceToolsIfReady(id, cwd)` | `createWorkspaceToolsIfReady(id, cwd, readOnly = true)` |
| skills | 现状 | **跳过** |
| MCP | 现状 | **跳过** |
| subagent | 现状（`depth=0`） | ✅ 保留，且 `createSubagentTools(..., delegateOnly = true)` |

> **`createWorkspaceToolsIfReady` 是否已有 readOnly 参数？** 实现期需先核实签名；若已有则直接传 `readOnly=true`，若无需扩展（参考 sub 用法 research 功能1 L22）。这是实现期第一个验证点。

### 3.3 SubagentTools.createSubagentTools 加 `delegateOnly` 参数

文件：`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt`（当前 L31-L38）

**签名改：**
```kotlin
fun createSubagentTools(
    json: Json,
    spawn: suspend (...) -> SubagentResult,
    askBtw: suspend (...) -> String,
    getProfiles: () -> List<SubagentProfile>,
    includeAskBtw: Boolean = true,
    delegateOnly: Boolean = false,   // 新增（默认 false，向后兼容现有调用）
): List<Tool>
```

**spawn_subagent 的 systemPrompt 分支**（L54-L68）：在 `buildString` 开头条件插入 orchestrator 文案：

```kotlin
systemPrompt = { _, _ ->
    buildString {
        appendLine()
        if (delegateOnly) {
            appendLine("**Delegation-Only Mode**")
            appendLine("You have NO execution tools. You MUST decompose the task and delegate via `spawn_subagent` (you may emit multiple in one response). Synthesize subagent results; do not paste raw transcripts.")
            appendLine()
        }
        appendLine("**Subagents — Delegation Guidance**")
        // ... 现有内容（available profiles 列表）保留
    }
}
```

### 3.4 调用链透传 `delegateOnly`

`ChatService.buildSubagentToolsForChat`（L1563+）→ `createSubagentTools`（L1588）需新增形参透传：

```kotlin
// buildSubagentToolsForChat 形参新增：
//   delegateOnly: Boolean
// 调用 createSubagentTools(..., delegateOnly = delegateOnly)
```

`ChatService.kt` L704-L716 的根装配调用处：
```kotlin
if (assistant.enableSubagents) {
    addAll(
        buildSubagentToolsForChat(
            ...,
            depth = 0,
            delegateOnly = delegateOnly,  // 新增（仅 depth==0 时可能为 true）
        ),
    )
}
```

> **子代理内层调用**：`depth > 0` 的递归调用恒传 `delegateOnly = false`（子代理本身不受父 delegate 限制；delegate 只约束根代理工具面）。

---

## 4. extraLocalTools 运行时（复杂度：中-重）

### 4.1 SubagentHost.buildChildAssistant 改造

文件：`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` L251-L293

**现状：**
```kotlin
val localTools = buildList {
    if (profile.inheritTools) {
        addAll(parent.localTools)
    } else {
        addAll(profile.localTools)
    }
    removeAll { it == LocalToolOption.AskUser }
}.distinct()
```

**改后（inherit 分支 additive）：**
```kotlin
val localTools = buildList {
    if (profile.inheritTools) {
        addAll(parent.localTools)
        addAll(profile.extraLocalTools)   // 新增：additive
    } else {
        addAll(profile.localTools)        // !inherit 忽略 extra（与 sub 一致）
    }
    removeAll { it == LocalToolOption.AskUser }
}.distinct()
```

- `distinct()` 保证 union 去重（约束：additive 不重复）。

### 4.2 SubagentPermissionBuilder.buildSubagentTools 改造

文件：`app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt` L114-L141

**现状 inherit 分支：** `parentTools.filter { 非workspace }.filter { 非excluded } + workspaceTools`
**现状非 inherit 分支：** 仅 `workspaceTools`（TODO 未展开）

**关键差异点**（与 sub 最大不同）：sub 无 PermissionBuilder，仅 Host 改 child assistant.localTools 即可（因为 sub 运行时直接读 child.localTools 装配）。我们 fork 的 PermissionBuilder **从 `parentTools`（已构造好的 `Tool` 列表）过滤**，不从 `LocalToolOption` 重建。因此 extraLocalTools 注入需在两层对齐：

**改造方案**（仅改 inherit 分支，**不破坏** non-inherit）：

```kotlin
fun buildSubagentTools(
    profile: SubagentProfile,
    depth: Int,
    maxDepth: Int,
    parentTools: List<Tool>,
    workspaceToolsFactory: (WorkspaceAccess) -> List<Tool>,
    spawnToolBuilder: (() -> Tool)? = null,
    extraLocalToolsProvider: (() -> List<Tool>) = { emptyList() },  // 新增参数，默认空（向后兼容）
): List<Tool> {
    val workspaceTools = workspaceToolsFactory(profile.workspaceAccess)
    val base = if (profile.inheritTools) {
        val nonWorkspaceParent = parentTools
            .filter { it.name !in WorkspaceToolNames }
            .filter { it.name !in profile.excludedTools }
        // extra 注入：在 exclude 之后追加（见 4.3 顺序分析）
        val extras = extraLocalToolsProvider()
            .filter { it.name !in WorkspaceToolNames }
            .filter { it.name !in profile.excludedTools }
        nonWorkspaceParent + extras + workspaceTools
    } else {
        // 不动（保持现状 TODO 行为；extra 在 !inherit 被忽略）
        workspaceTools
    }
    ...
}
```

### 4.3 注入顺序：base → extra → exclude（统一）

PRD 风险点（L91）："extra 注入的工具名是否被 `excludedTools` 二次过滤需明确"。

**决策：extra 注入后统一过 `excludedTools`**（约束 L119）。

顺序：
```
1. base = parentTools (非workspace, 非excluded)
2. extras = extraLocalToolsProvider() (非workspace, 非excluded)   ← 二次过滤生效
3. final = (base + extras).distinctBy { name } + workspaceTools
```

理由：`excludedTools` 是用户显式排除，优先级最高；extra 是 additive 增量，不能绕过用户排除意图。

### 4.4 extraLocalToolsProvider 的构造（调用方）

`buildSubagentTools` 的调用方（在 `ChatService` 或 `SubagentHost` 子代理装配路径）需传入：

```kotlin
extraLocalToolsProvider = {
    if (profile.inheritTools) {
        localTools.getTools(profile.extraLocalTools)  // LocalToolOption -> Tool
    } else {
        emptyList()
    }
}
```

> **实现期需验证**：`localTools.getTools(options: List<LocalToolOption>)` 已存在（research L242 确认 `LocalTools.getTools`）；`buildSubagentTools` 当前调用点在哪处（grep `buildSubagentTools(`）需在实现时定位并补形参。

### 4.5 non-inherit 分支不变（约束 L121）

`buildSubagentTools` 的 `else` 分支（仅 workspace）**不动**。extraLocalTools 在 `!inherit` 时：
- Host 层：`buildChildAssistant` 不 add extra（4.1）
- PermissionBuilder 层：`extraLocalToolsProvider` 返回空（4.4 if 守卫）
- UI 层：区块不显示（D3）

三重保证，满足"被忽略"约束。

---

## 5. 导航重组 UI 设计（D2）

### 5.1 文件删除/迁移

- **删除**：`app/src/main/java/me/rerere/rikkahub/pages/assistant/AssistantSubagentHubSection.kt`（含 `AssistantSubagentHubControls`）
- **修改**：
  - `AssistantDetailPage.kt`（L156-L161）：移除 `item { AssistantSubagentHubControls(...) }`
  - `AssistantSubagentPage.kt`（L96+）：在 profiles 列表之前插入一张助手级 Card

### 5.2 控件分布对比（UI 草图）

**改造前：**
```
AssistantDetailPage
├─ ...其他助手设置
├─ item: 子代理入口行（Navigate → AssistantSubagentPage）
└─ item: AssistantSubagentHubControls   ← 删除
     ├─ Switch: 启用 (enableSubagents)
     └─ Slider: 深度 (1..3)

AssistantSubagentPage
└─ LazyColumn
     └─ profiles section (Add / Refresh / 列表)   ← 首 item 即列表
```

**改造后：**
```
AssistantDetailPage
├─ ...其他助手设置
└─ item: 子代理入口行（Navigate → AssistantSubagentPage）
     （不再有助手级控件）

AssistantSubagentPage
└─ LazyColumn
     ├─ item: Card（助手级「多配置」）            ← 新增首 Card
     │    ├─ FormItem: Switch 启用 (enableSubagents)
     │    ├─ HorizontalDivider
     │    ├─ FormItem: Slider 深度 (1..5, steps=3)   ← 范围对齐 sub
     │    ├─ HorizontalDivider
     │    ├─ FormItem: Switch 仅委派 (subagentDelegateOnly, enabled = enableSubagents)
     │    ├─ HorizontalDivider
     │    └─ FormItem: Switch 并行执行 (parallelToolExecution, enabled = enableSubagents)
     └─ profiles section (Add / Refresh / 列表)   ← 原内容下移
```

### 5.3 `subagentMaxDepth` Slider 范围调整

文件：`AssistantSubagentHubSection.kt` L66-L77（迁移到 `AssistantSubagentPage.kt` 后）

```kotlin
// 改前
valueRange = 1f..3f; steps = 1; coerceIn(1, 3)
// 改后（对齐 sub）
valueRange = 1f..5f; steps = 3; coerceIn(1, 5)
```

约束 L122：旧 depth=3 配置仍有效（3 在 1..5 范围内，无需迁移）。

### 5.4 新增 strings（`values/strings.xml` + `values-zh/strings.xml`）

| key | en | zh |
|-----|----|----|
| `subagent_delegate_only_title` | Delegate only | 仅委派 |
| `subagent_delegate_only_desc` | Root agent keeps only read-only + spawn_subagent tools | 根代理仅保留只读与委派工具 |
| `subagent_parallel_execution_title` | Parallel tool execution | 并行工具执行 |
| `subagent_parallel_execution_desc` | Run multiple tool calls in the same turn in parallel | 同一轮次并行执行多个工具调用 |
| `subagent_profile_extra_local_tools_title` | Extra local tools (on inherit) | 额外本地工具（继承时） |
| `subagent_profile_extra_local_tools_desc` | Added on top of inherited parent local tools | 在继承的父本地工具之上追加 |

命名遵循 `subagent_` / `subagent_profile_` 约定（research L275）。

---

## 6. UI 改造（Profile 页 extraLocalTools，D3）

文件：`app/src/main/java/me/rerere/rikkahub/pages/assistant/AssistantSubagentProfilePage.kt` L515-L556

**当前：**
```kotlin
if (resolved.inheritTools) {
    // excluded_tools PathChipEditor (L528-546)
} else {
    // LocalToolsSkillMcpSection (L550-556)
}
```

**改后（仅在 inherit 分支追加）：**
```kotlin
if (resolved.inheritTools) {
    // excluded_tools PathChipEditor（原样）
    // ↓ 新增：extra local tools 多选区块
    FormItem(
        title = stringResource(R.string.subagent_profile_extra_local_tools_title),
        description = stringResource(R.string.subagent_profile_extra_local_tools_desc),
    ) {
        // 复用 localTools 选择 UI 模式（FlowRow + FilterChip 或 MultiChoiceSegment）
        // options = LocalToolOption.entries - LocalToolOption.AskUser
        // selected = profile.extraLocalTools
        // onChange = { vm.updateProfile(profile.copy(extraLocalTools = it)) }
    }
} else {
    // LocalToolsSkillMcpSection（原样，!inherit 时 extra 不显示）
}
```

> **复用点**：`!inherit` 分支的 `LocalToolsSkillMcpSection` 已有 localTools 多选 UI；实现期抽出其 LocalToolOption 选择子组件复用，或就地写一个同样的 FlowRow+FilterChip。**AskUser 不应出现在 extra 候选**（Host 会强制移除，候选列表 preempt 去掉避免用户困惑）。

---

## 7. 兼容性 / 回滚

| 维度 | 评估 |
|------|------|
| 序列化兼容 | 三新字段全默认值；旧 JSON / DataStore 自动取默认 |
| 行为兼容 | 三新字段默认 false / empty → 行为与现状等价（parallel 公式退化成 `size>1 && subagentCount>1`；delegate 永不触发；extra 永不注入） |
| UI 兼容 | 助手详情页移除控件 → 已保存的 `enableSubagents`/`subagentMaxDepth` 值不受影响（只是编辑入口迁移到子代理页） |
| 回滚 | 单 commit 内改动；回滚即 revert 该 commit。无 DB 迁移、无不可逆状态 |

---

## 8. 风险登记（实现期关注）

| 风险 | 缓解 |
|------|------|
| `createWorkspaceToolsIfReady` 无 readOnly 参数 | 实现期先核实签名；若无，扩展为 `readOnly: Boolean = false`（默认 false 不破坏其他调用） |
| `buildSubagentTools` 当前调用点未在 research 中定位 | 实现 step 1 先 grep `buildSubagentTools(` 所有调用点，补 `extraLocalToolsProvider` 形参 |
| `LocalToolsSkillMcpSection` 的 localTools 选择组件不可直接复用 | 实现期若发现耦合，就地新写 FlowRow+FilterChip（与 localTools 选择保持一致枚举源） |
| Slider 范围改 1..5 后旧 depth>5 的脏数据 | 不存在（现状范围 1..3，不可能存 >3）；但仍 `coerceIn(1,5)` 兜底 |
| delegate + workspace 只读父与 FULL 子代理的组合易混淆 | UI desc 文案明确："仅限根代理工具集；子代理权限仍由其 profile.workspaceAccess 控制" |

---

## 9. 跨任务边界（与 sibling child）

- **`local-tool-ui-consolidation`**：改 local 工具 UI/枚举/去重；本任务改 subagent 运行时/Assistant 字段。两者都触及 `AssistantSubagentPage.kt` 但**不同部分**（本任务改首 Card；sibling 改 profiles 列表项）。PRD 依赖说明：实现期建议串行避免合并冲突。
- **`subagent-audit-port`**（in_progress）：触及 `SubagentTools.kt` 同文件；本任务的 `createSubagentTools(delegateOnly)` 与 audit 的 tool-call 计数正交（不同参数/不同代码块），但合并时需注意 import 顺序。

---

## 10. 验收映射（design → PRD 验收）

| PRD 验收 | 设计落点 |
|----------|----------|
| AssistantDetailPage 无控件 | §5.1（删除 item） |
| SubagentPage 顶部 Card 4 项 | §5.2 |
| parallelToolExecution 并行生效 | §2.1 + §2.2 行为矩阵 |
| delegate 根工具为只读子集 | §3.1 白名单 + §3.2 装配表 |
| spawn systemPrompt orchestrator 文案 | §3.3 |
| extraLocalTools additive 注入 | §4.1 + §4.4 |
| !inherit 忽略 extra | §4.5 三重保证 |
| extra 受 excludedTools 过滤 | §4.3 顺序 |
| 旧数据反序列化不崩 | §1.1 + §1.2 默认值 |
| compile/install 通过 | implement.md 验证步骤 |
