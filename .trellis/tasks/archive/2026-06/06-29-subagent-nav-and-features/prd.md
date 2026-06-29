# PRD: Subagent nav restructure + parallel/delegate/extraLocalTools

**Parent:** `06-29-backport-batch-2`
**Complexity:** 复杂（数据模型 + 运行时 + UI 导航，需 design.md + implement.md）
**集成顺序建议:** 与 `local-tool-ui-consolidation` 并行或先后（两者触及文件不同，但都改 AssistantSubagentPage）

## 背景

用户反馈 sub fork 的子代理配置进入路径更好：
- **sub**：助手 → 子代理页（顶部 Card 集中 4 项助手级控件：启用/深度/delegate/并行）→ profile 列表 → 单个详细配置
- **fork 现状**：助手详情页就放了启用+深度（`AssistantSubagentHubControls`），子代理页只剩列表；且无 delegate/并行功能（数据模型都没有）

此外 sub 有三个我们缺的子代理功能：`subagentDelegateOnly`、`parallelToolExecution`、`extraLocalTools`。

调研依据：
- `.trellis/tasks/06-29-local-tool-ui-consolidation/research/sub-vs-rikka-subagent-nav-and-fields.md`（导航 + 字段对比）
- `.trellis/tasks/06-29-local-tool-ui-consolidation/research/sub-master-three-subagent-runtime-features.md`（三功能运行时实现）

## 范围

### 1. 导航重组：助手级控件收拢到子代理页（对齐 sub 布局）

**现状割裂**：`AssistantDetailPage` 放了 `AssistantSubagentHubControls`（启用+深度），`AssistantSubagentPage` 只有列表。

**目标**：对齐 sub 的"先总控再列表"节奏。
- 从 `AssistantDetailPage` 移除 `AssistantSubagentHubControls`，助手详情只保留"子代理"入口行。
- `AssistantSubagentPage` 顶部增加一张 Card，集中放所有助手级子代理控件（启用/深度/新增的 delegate/并行），用 `Card + FormItem + HorizontalDivider` 统一风格。
- **决策（已确认）**：`AssistantSubagentHubControls` **内联到 `AssistantSubagentPage.kt`**；删除独立文件 `AssistantSubagentHubSection.kt`。`AssistantDetailPage` 不再引用该控件。
- `subagentMaxDepth` Slider 范围对齐 sub 的 1..5（我们现在是 1..3）。

**触及文件**：`AssistantDetailPage.kt`、`AssistantSubagentPage.kt`、`AssistantSubagentHubSection.kt`、strings.xml。

### 2. parallelToolExecution（并行工具执行）— 复杂度：轻

**sub 运行时**（`GenerationHandler.generateText`）：
```kotlin
val runInParallel = (assistant.parallelToolExecution && toolsToProcess.size > 1) || subagentCount > 1
```
启用后同一 turn 任意多个 tool call 可并行；即使未开启，≥2 个 spawn_subagent 也强制并行。

**fork 现状**（`GenerationHandler.kt:257`）：仅 `size > 1 && subagentCount > 1` 才并行，无全局开关。

**移植**：
- `Assistant.kt` 加 `parallelToolExecution: Boolean = false`（序列化兼容）。
- `GenerationHandler.kt:257` 改 `runInParallel` 条件为 sub 公式。
- `AssistantSubagentPage` 顶部 Card 加 Switch（`enabled = assistant.enableSubagents`）。
- strings：`subagent_parallel_execution_title` / `_desc`。

**风险**：并发写 workspace / MCP 连接竞态（sub 同样承担，属产品预期）。

### 3. subagentDelegateOnly（仅委派模式）— 复杂度：中

**sub 运行时**（`ChatService.handleMessageComplete`）：启用后根代理（depth=0）工具列表改为"只读+委派"子集：
- 保留：Web 搜索、AskUser/TimeInfo/Clipboard/Logs、只读 workspace、spawn_subagent。
- 不注入：skills、MCP、其余 local tools、可写 workspace。
- `spawn_subagent` 的 systemPrompt 注入 orchestrator 文案（主代理无执行工具，必须拆解任务委派）。

**fork 现状**：`ChatService.kt:657-717` 根工具始终全量装配，无 delegate 分支；`SubagentTools.kt` 无 `delegateOnly` 参数。

**移植**：
- `Assistant.kt` 加 `subagentDelegateOnly: Boolean = false`。
- `ChatService.kt` 根工具装配加 `delegateOnly` 分支：抽 `buildCommonTools` 或就地 if-else，只读 workspace + local 子集 + subagent。
- `SubagentTools.kt` `createSubagentTools` 加 `delegateOnly` 参数，spawn_subagent systemPrompt 注入 orchestrator 文案。
- `AssistantSubagentPage` 顶部 Card 加 Switch。
- strings：`subagent_delegate_only_title` / `_desc`。

**决策（已确认）**：delegate-only 模式下**完全排除 MCP**（对齐 sub；`mcpManager.getAllAvailableTools()` 步骤在 delegate 分支跳过，无需保留 MCP 严格校验）。MCP 执行能力下放给子代理。

**风险**：与 workspace 权限模型正交但易混淆（只读父 + FULL 子代理是预期）。

**关键约束**：不破坏现有 workspace 权限模型 —— delegate-only 限制的是**父代理**工具集，子代理权限仍由 `SubagentProfile.workspaceAccess` 控制。

### 4. extraLocalTools（inheritTools 时额外注入）— 复杂度：中-重

**sub 运行时**（`SubagentHost.buildChildAssistant`）：
```kotlin
val localTools = buildList {
    if (profile.inheritTools) { addAll(parent.localTools); addAll(profile.extraLocalTools) }
    else { addAll(profile.localTools) }
}.distinct()
```
inheritTools=true 时，子代理 localTools = 父 localTools ∪ extraLocalTools（additive）。

**fork 现状**：`SubagentProfile` 无 `extraLocalTools`；`SubagentHost.buildChildAssistant`（L257-264）仅 parent/profile.localTools；`SubagentPermissionBuilder` inherit 时从 `parentTools` 过滤，不从 child localTools 重建。

**移植**：
- `SubagentProfile.kt` 加 `extraLocalTools: List<LocalToolOption> = emptyList()`。
- `SubagentHost.buildChildAssistant` inherit 分支加 `addAll(profile.extraLocalTools)`。
- **关键**：`SubagentPermissionBuilder.buildSubagentTools` 需对齐 —— inherit 时除了 `parentTools` 过滤，还要注入 `extraLocalTools` 对应的 `localTools.getTools()`，顺序：base → extra → exclude。这是与 sub 最大的差异点（sub 无 PermissionBuilder，我们需在 PermissionBuilder 层补 additive 逻辑）。
- `AssistantSubagentProfilePage` 加 extraLocalTools 多选区块（**决策（已确认）**：放在 `if (resolved.inheritTools)` 分支内、`excluded_tools` PathChipEditor 下方；`inheritTools=false` 时该区块不显示，自动满足"extra 被忽略"约束）。
- strings：`subagent_extra_local_tools_title` / `_desc`。

**风险**：extra 注入的工具名是否被 `excludedTools` 二次过滤需明确（应在 base→extra 之后统一 exclude）；`inheritTools=false` 时 extra 应被忽略。

### Out of Scope

- 不学 sub 的 `mergeSubagentProfiles(custom, disabledBuiltin)` 数据模型 —— 保留我们的 global profiles 架构。
- 不删 Extensions 全局子代理页。
- 不改子代理聊天工具 UI（`SpawnSubagentToolUI`）。
- 不改 `SubagentProfile` 的 workspace 相关字段。
- local 工具的权限同步/枚举去重/错误卡片由 `local-tool-ui-consolidation` child 处理。

## 验收标准

- [ ] `AssistantDetailPage` 不再有启用/深度控件，只有子代理入口行。
- [ ] `AssistantSubagentPage` 顶部 Card 含：启用/深度(1..5)/delegate-only/parallel-execution 四项，风格统一（Card+FormItem+HorizontalDivider）。
- [ ] `parallelToolExecution=true` 时，同一 turn 任意多个 tool call 并行执行（真机验证：2 个搜索并行跑）。
- [ ] `subagentDelegateOnly=true` 时，根代理工具集为只读子集 + spawn_subagent（无 skills/MCP/可写 workspace）。
- [ ] spawn_subagent systemPrompt 在 delegate 模式下注入 orchestrator 文案。
- [ ] `extraLocalTools` 在 inheritTools=true 时 additive 注入子代理（真机验证：子代理能用 extra 的 local tool）。
- [ ] `inheritTools=false` 时 extraLocalTools 被忽略。
- [ ] `extraLocalTools` 注入的工具仍受 `excludedTools` 过滤。
- [ ] 旧 Assistant 数据（无三个新字段）反序列化不崩（默认 false / emptyList）。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :app:installDebug --no-daemon` 成功。

## 约束

- 新字段全部带默认值（false / emptyList），kotlinx.serialization 向后兼容。
- delegate-only 的"只读子集"定义要明确白名单（哪些 LocalToolOption 保留），写在 design.md 里。
- extraLocalTools 的 PermissionBuilder 改动**不能破坏** inheritTools=false 分支。
- Slider 范围改 1..5 不能让旧的 depth=3 配置失效（3 仍在范围内）。

## 复杂任务说明

本任务为**复杂任务**，`task.py start` 前需补 `design.md`：
- 导航重组的 UI 草图（助手详情 vs 子代理页控件分布）。
- delegate-only 的 LocalToolOption 白名单清单。
- extraLocalTools 在 PermissionBuilder 的 additive 流程图（base→extra→exclude 顺序）。
- parallel 的并发安全分析（哪些工具竞态、是否需锁）。
+ `implement.md`：分步检查表（建议顺序：parallel → 导航重组 → delegate → extraLocalTools，按复杂度递增）。

## 依赖

- 不依赖其他 child（文件正交：本任务改 ChatService/GenerationHandler/SubagentHost/SubagentPermissionBuilder/SubagentTools；`local-tool-ui-consolidation` 改 local 工具 UI/枚举）。
- 可与 `local-tool-ui-consolidation` 并行规划，但实现期建议串行避免 `AssistantSubagentPage` 合并冲突（两个都改这个文件的不同部分）。
