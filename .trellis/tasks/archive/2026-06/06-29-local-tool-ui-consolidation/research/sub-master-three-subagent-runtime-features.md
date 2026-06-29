# Research: sub/master 三项子代理运行时功能（移植评估）

- **Query**: subagentDelegateOnly、parallelToolExecution、extraLocalTools 在 sub/master 的运行时行为与移植面
- **Scope**: internal（`git show sub/master:` + 本 fork `release/rikka-arsucar` 对照）
- **Date**: 2026-06-29

## 调研方法摘要

- `git grep` on `sub/master` 定位 Kotlin 消费点
- `git show sub/master:<path>` 读关键实现
- 引入 commit：`8b9095d3`（delegate-only + parallel）；`extraLocalTools` 随早期 subagent MVP 在 `SubagentProfile`/`SubagentHost` 中（无单独小 commit 在本次 pickaxe 中突出）

---

## 功能 1: subagentDelegateOnly（仅委派 / 纯决策模式）

### sub 的运行时行为（启用后发生什么）

当 `assistant.enableSubagents && assistant.subagentDelegateOnly` 为真时：

1. **根代理（depth=0）工具列表**（`ChatService.handleMessageComplete` → `tools = buildList`）不走完整 `buildCommonTools`，改为「只读 + 委派」子集，再 `addAll(subagentTools)`：
   - **保留**：Web 搜索（若全局开启）、本地工具子集（仅 `AskUser` / `TimeInfo` / `Clipboard` / `Logs`）、最近对话引用（若开启）、**只读** workspace（`createWorkspaceToolsIfReady(..., readOnly = true)`）
   - **不注入**：skills、MCP、其余本地工具（如 AskBtw 等未在白名单内）、可写 workspace、以及 delegate 分支外的完整本地/MCP 装配
2. **子代理委派工具**：仍通过 `buildSubagentTools` 注入 `spawn_subagent` / `ask_btw`（及 manage 等，视 sub 版本）
3. **提示词强化**：`createSubagentTools(..., delegateOnly = depth == 0 && enableSubagents && subagentDelegateOnly)` 时，`spawn_subagent` 的 `systemPrompt` 注入 **delegation-only mode** 文案：主代理无执行类工具，必须拆解任务、`spawn_subagent` 并行委派、综合摘要而非粘贴

**不是**「模型不能直接回答」：主代理仍可输出自然语言；限制的是 **可调用工具面**（执行类工具被拿掉，委派与只读查看保留）。

### 数据层改动

| 项 | sub/master |
|----|------------|
| 字段 | `Assistant.subagentDelegateOnly: Boolean = false`（`@Serializable`，带 KDoc） |
| 序列化 | 新字段带默认值 → kotlinx.serialization **向后兼容**（旧 JSON 缺字段 = false） |

### 运行时改动（移植时要改什么）

| 文件 | 改动要点 |
|------|----------|
| `app/.../data/model/Assistant.kt` | 增加 `subagentDelegateOnly` |
| `app/.../service/ChatService.kt` | 根 `tools = buildList` 增加 `delegateOnly` 分支（sub 用 `buildCommonTools` + `readOnly` workspace；我们 fork 当前是内联装配，需等价拆分或抽 `buildCommonTools`） |
| `app/.../service/ChatService.kt` | `buildSubagentToolsForChat` / `createSubagentTools` 调用处传入 `delegateOnly = depth==0 && ...`（我们当前 **无** `delegateOnly` 参数） |
| `app/.../data/ai/subagent/SubagentTools.kt` | 增加 `delegateOnly` 参数与 systemPrompt 分支（sub 有长文案；我们 fork 为简化版 guidance） |

### UI 改动

- `AssistantSubagentPage.kt`：`FormItem` + `Switch`，`enabled = assistant.enableSubagents`
- 字符串：`subagent_delegate_only_title` / `_desc`（sub `values` / `values-zh`）

### 我们 fork 现状对照

- `Assistant.kt`：**无** `subagentDelegateOnly`
- `ChatService.kt` L657–717：根工具 **始终** 搜索 + 全量 `localTools` + workspace（非只读）+ skills + MCP + subagent，**无** delegate 分支
- `SubagentTools.kt`：**无** `delegateOnly`，无 orchestrator 专用 systemPrompt

### 移植复杂度

**中**

- 逻辑清晰、消费点少（grep 仅 ChatService + SubagentTools + Assistant + UI）
- 我们 fork 工具装配路径与 sub 不同（`toolsForSubagentProfile` / `SubagentPermissionBuilder` vs sub 的 `buildCommonTools`），需在 **不破坏** workspace 权限模型前提下复刻「根代理只读子集」

### 风险

- **与 workspace 模型**：我们子代理用 `WorkspaceAccess` / `excludedTools`；delegate-only 是 **父代理** 只读，二者正交但易混淆（只读父 + FULL 子代理是预期）
- **AskBtw**：sub delegate 白名单未包含 AskBtw，但 `includeAskBtw` 仍可能通过 subagent 工具包注入——需对照 sub 完整 `buildSubagentTools` 列表
- **MCP 严格校验**：我们根代理 MCP 非法名会 `addError` 并 return；delegate 分支需定义是否仍校验

---

## 功能 2: parallelToolExecution（并行工具执行）

### sub 的运行时行为

在 `GenerationHandler.generateText` 每轮 tool step：

```kotlin
val subagentCount = toolsToProcess.count { it.toolName == "spawn_subagent" }
val runInParallel =
    (assistant.parallelToolExecution && toolsToProcess.size > 1) || subagentCount > 1
```

- **并行**：`coroutineScope { toolsToProcess.map { async { executeSingleTool(...) } }.awaitAll() }`
- **串行**：单工具或上述条件不满足时 `mapNotNull { executeSingleTool }`
- 结果按 `toolCallId` 写回 last assistant message 的 tool parts（顺序无关）

**语义**：

1. 用户打开 `parallelToolExecution`：同一 turn **任意** 多个 tool call 可并行（不限于 spawn）
2. **即使用户未打开**：若本轮 **≥2 个** `spawn_subagent`，仍 **强制并行**（子代理长耗时、相互独立）

`executeSingleTool` 共享审批态处理（Denied / Answered / Pending / 执行）；并行下每个 tool 独立 `execute`。

### 数据层改动

| 项 | sub/master |
|----|------------|
| 字段 | `Assistant.parallelToolExecution: Boolean = false` |
| 序列化 | 默认 false，兼容旧数据 |

### 运行时改动

| 文件 | 改动要点 |
|------|----------|
| `Assistant.kt` | 增加字段 |
| `GenerationHandler.kt` | `runInParallel` 条件改为 sub 公式（我们已有 `executeSingleTool` + 部分并行，仅 **条件更窄**） |

### UI 改动

- `AssistantSubagentPage` Switch + `subagent_parallel_execution_title` / `_desc`

### 我们 fork 现状对照

`GenerationHandler.kt` L257–258：

```kotlin
val runInParallel = toolsToProcess.size > 1 && subagentCount > 1
```

- **仅**「多个工具且多个 spawn」才并行；**无** `parallelToolExecution` 全局开关
- **未**实现「2 个 spawn 但混 1 个非 spawn 工具」时 sub 的 `subagentCount > 1` 单独触发（我们要求 `size > 1` 且 `subagentCount > 1`）

### 移植复杂度

**轻**

- 已有 `executeSingleTool`、`coroutineScope`/`async` 路径；主要改一行条件 + `Assistant` 字段 + UI/字符串

### 风险（并发安全）

- **共享可变状态**：`executeSingleTool` 内 `toolDef.execute`；若工具实现依赖会话级可变状态（同一 conversation 并发写 workspace / 同一 MCP 连接），可能竞态——sub 同样承担此风险
- **spawn_subagent**：每个 spawn 走 `SubagentHost.spawn` + 独立子循环；并行多个 spawn 时 `updateSubagentProgress` 依赖 `toolCallId`（我们已有 `currentToolCallId()`）——需确认 metadata 键不冲突
- **非 spawn 工具并行**：打开 `parallelToolExecution` 后，例如并行多个 `workspace_write_file` 可能乱序写——属产品预期，非 sub 独有

引入 commit：`8b9095d3` 明确包含 GenerationHandler 重构与 `executeSingleTool` 提取。

---

## 功能 3: extraLocalTools（inheritTools 时额外注入本地工具）

### sub 的运行时行为

仅在 **`SubagentHost.buildChildAssistant`**（构造子代理用的 `Assistant` 副本）中消费：

```kotlin
val localTools = buildList {
    if (profile.inheritTools) {
        addAll(parent.localTools)
        addAll(profile.extraLocalTools)  // additive
    } else {
        addAll(profile.localTools)
    }
    removeAll { it == LocalToolOption.AskUser }
}.distinct()
```

- **inheritTools=true**：子代理 `Assistant.localTools` = 父 localTools ∪ extraLocalTools（再去 AskUser）
- **inheritTools=false**：仅用 `profile.localTools`，**忽略** extraLocalTools
- 注释说明：真正进模型的工具集以 `generateText(tools=...)` 为准；此处 `localTools` 供 GenerationHandler 内 **memory 等** 判断

sub **未**在 `SubagentPermissionBuilder` / UI Profile 页 grep 到 `extraLocalTools`（仅数据类 + Host）。

### 数据层改动

| 项 | sub/master |
|----|------------|
| 字段 | `SubagentProfile.extraLocalTools: List<LocalToolOption> = emptyList()` |
| 序列化 | 默认 emptyList，兼容 |

### 运行时改动（我们 fork）

| 文件 | 改动要点 |
|------|----------|
| `SubagentProfile.kt` | 增加 `extraLocalTools` |
| `SubagentHost.buildChildAssistant` | inherit 分支 `addAll(profile.extraLocalTools)` |
| `SubagentPermissionBuilder.buildSubagentTools` | 我们 **inherit** 时从 `parentTools` 过滤，**不**从 `childAssistant.localTools` 重建本地工具；`inheritTools=false` 分支 TODO 未展开 profile.localTools。移植 extraLocalTools 需明确：**本地工具是走 parentTools 继承还是单独 `localTools.getTools(...)` 注入**——sub 偏 Host 层 child assistant；我们偏 PermissionBuilder + ChatService `toolsForSubagentProfile` |

我们 fork `buildChildAssistant`（L257–264）仅 parent/profile.localTools，**无** extraLocalTools。

我们 fork `SubagentPermissionBuilder` L123–130：inherit 时 `parentTools` 非 workspace 过滤 + workspace 工厂；**未**映射 `LocalToolOption` → Tool 的 extra 列表。

### UI 改动

- sub/master：**无** Profile UI 编辑 `extraLocalTools`（仅数据层）
- 完整移植通常需 `AssistantSubagentProfilePage` 多选本地工具（或「继承时追加」区块）

### 我们 fork 现状对照

- 有 `inheritTools`、`localTools`、`excludedTools`、`workspaceAccess` 等 **更丰富** Profile 运行时
- **无** `extraLocalTools` 字段；inherit 时无法「父工具集 + 额外本地项」的 additive 语义（除非改 parent 的 localTools 或扩展 PermissionBuilder）

### 移植复杂度

**中～重**（相对另两项）

- 字段 + Host 一行较易
- 我们工具链以 `parentTools` + workspace 为主，**本地工具 additive** 需在 `toolsForSubagentProfile` / `buildSubagentTools` 或 `localTools.getTools` 显式合并，并与 `excludedTools`、workspace 替换逻辑一致

### 风险

- **与 inheritTools=false**：extra 应被忽略（sub 行为明确）
- **与 excludedTools**：extra 注入的 tool name 是否再被 excluded 过滤——sub Host 层只改 localTools 列表，工具名排除在 sub 另一路径（`excludedTools` on profile）；我们需在 PermissionBuilder 统一顺序：base → extra → exclude
- **LocalToolOption 包路径**：我们 `me.rerere.rikkahub.data.ai.tools.local.LocalToolOption`，sub 为 `...tools.LocalToolOption`（移植时勿混 import）

---

## 三个功能的依赖关系

| 关系 | 结论 |
|------|------|
| delegate-only ↔ parallel | **无硬依赖**。同一助手可同时开启；delegate 改变**有哪些工具**，parallel 改变**同一 turn 多工具如何跑**。sub 文案鼓励 delegate 模式下同一 response 多个 `spawn_subagent`，与 parallel（尤其 `subagentCount > 1`）协同，但可独立实现 |
| extraLocalTools ↔ delegate-only | **无依赖**。extra 仅影响**子代理** child assistant 的 localTools 列表 |
| extraLocalTools ↔ parallel | **无依赖** |
| 共同前提 | 均假设 **enableSubagents**（UI 上 sub 对 delegate/parallel Switch 使用 `enabled = enableSubagents`）；extra 在 profile 级，隐含子代理体系已存在 |

**可独立引入顺序建议（实施层面，非 sub 代码强制）**：

1. `parallelToolExecution` — 改动最小，行为已部分存在  
2. `subagentDelegateOnly` — 需 ChatService + SubagentTools 提示词  
3. `extraLocalTools` — 需对齐我们 PermissionBuilder 与 Host 双路径  

---

## 关键文件对照表

| 能力 | sub/master 消费文件 | release/rikka-arsucar |
|------|---------------------|------------------------|
| subagentDelegateOnly | `Assistant.kt`, `ChatService.kt`, `SubagentTools.kt`, `AssistantSubagentPage.kt` | 均无字段/分支 |
| parallelToolExecution | `Assistant.kt`, `GenerationHandler.kt`, `AssistantSubagentPage.kt` | 无字段；GenerationHandler 仅「多 spawn」并行 |
| extraLocalTools | `SubagentProfile.kt`, `SubagentHost.kt` | 无字段；Host inherit 无 additive |

## Caveats / Not Found

- sub/master **无** `SubagentPermissionBuilder.kt`（我们 fork 独有）；extraLocalTools 在我们侧 **不能** 只抄 SubagentHost，必须验证 `toolsForSubagentProfile` 是否从 child `localTools` 生成工具
- `git log -S extraLocalTools sub/master` 仅命中 merge commit，功能应来自更早 subagent 合入
- 未对 sub/master 做运行时真机验证，行为来自静态代码