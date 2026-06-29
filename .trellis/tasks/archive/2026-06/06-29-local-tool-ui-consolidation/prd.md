# PRD: Local tool UI consolidation (权限同步 + 错误卡片 + 枚举去重 + Profile Card 风格)

**Parent:** `06-29-backport-batch-2`
**Complexity:** 复杂（UI 改动多，需 design.md + implement.md）
**集成顺序建议:** 在 ScreenTime/日历就位后统一优化 local 工具 UI；与 `subagent-nav-and-features` 文件正交但都改 `AssistantSubagentPage`，建议串行

## 背景（更新版）

本 child 原包含子代理导航重组和新功能，已拆分到 `06-29-subagent-nav-and-features`。本 child 现专注 **local 工具体系**的 UI/数据保存统一，以及 **SubagentProfilePage 的 Card 分区风格**（不改导航、不加新功能）。

调研依据：
- `.trellis/tasks/06-29-screentime-accuracy/research/screentime-ui-and-persistence.md`
- `.trellis/tasks/06-29-calendar-tools/research/calendar-tools-porting.md`
- `.trellis/tasks/06-29-screentime-accuracy/research/sub-fork-subagent-ui-style.md`

## 范围

### 1. 权限与开关同步统一（问题 B）

**现状**：ScreenTime 开关可开但权限可无（`AssistantLocalToolPage.kt:84-93` 不 `return`），持久化与真实可用性不一致。

**目标**：统一所有 local 工具的开关-权限联动。
- 权限相关工具（ScreenTime/日历）开开关时：未授权 → 引导授权（弹权限请求/跳设置页），**不保存开关状态**；已授权 → 正常保存。
- 非权限工具（TimeInfo/Logs/Clipboard 等）→ 直接保存。
- 抽象 `LocalToolTogglePolicy`：判断该 LocalToolOption 是否需权限 + 是否满足 + 未满足时引导动作。

**触及文件**：`AssistantLocalToolPage.kt`（toggleLocalTool 逻辑）、可能新建 `LocalToolPermission.kt`。

### 2. 统一错误/空态 UI 组件（问题 C 通用部分）

**现状**：ScreenTime 只处理 `NO_PERMISSION`；其余错误/空数据走默认 JSON Preview。

**目标**：新建统一 local 工具结果卡片。
- `LocalToolErrorCard`：解析结果 JSON 的 `error` 字段，映射友好文案 + 图标 + 颜色（permission=橙、invalid=红、empty=灰）。
- `LocalToolEmptyCard`：成功但无数据时的友好空态。
- 现有 `GetScreenTimeToolUI` / `GetLogsToolUI` / 日历 UI 都改用这套组件。

**触及文件**：新建 `LocalToolCards.kt`（或加进 `BuiltinToolUIs.kt`）、改 `GetScreenTimeToolUI`、日历 UI。

### 3. 枚举单一 source of truth（问题 D 完整版）

**现状**：`LocalToolOption` / `LocalTools.getTools` / `SubagentTools.toLocalToolOption` / `AssistantSubagentProfilePage`（缺 AskUser）四处手写。

**目标**：`LocalToolOption` 作为唯一枚举源，配置页自动派生。
- `LocalToolOption` 加 `displayName: StringResource` / `description: StringResource` 属性（或扩展），配置页遍历 `entries` 渲染，不再手写 when。
- `SubagentTools.toLocalToolOption` 用 `entries.firstOrNull { it.serialName == s }` 派生。
- 补齐 `AssistantSubagentProfilePage` 缺的 AskUser。
- 新增工具只需：加枚举项 + `LocalTools.getTools` 分支 + strings，配置页自动出现。

**触及文件**：`LocalToolOption.kt`、`AssistantLocalToolPage.kt`、`AssistantSubagentProfilePage.kt`、`SubagentTools.kt`。

### 4. SubagentProfilePage Card 分区风格（问题 E，仅风格不加功能）

**注意**：子代理导航重组和新功能已拆到 `subagent-nav-and-features`。本项**只做 Profile 详情页的 Card 分区风格统一**，不改导航、不加字段。

**目标**：重构 `AssistantSubagentProfilePage`（`SubagentProfileForm`）为多 Card 分区，对齐 sub 的布局范式：
- 基本 Card：name / displayName / description
- Prompt Card：systemPrompt
- 模型参数 Card：chatModelId / temperature / topP / maxTokens / reasoningLevel
- 行为 Card：maxSteps / canSpawn / inheritTools
- workspace 权限 Card：workspaceAccess / workspaceApproval / allowedPathPrefixes / toolApprovalOverrides（**保留所有字段**）
- 工具 Card：inheritTools=false 时 localTools/skills/mcp；inheritTools=true 时 excludedTools
- 流式/记忆 Card：streamOutput / enableMemory / summaryMinLength / summaryContinuationAttempts

每段 `Card(colors = CustomColors.cardColorsOnSurfaceContainer) + FormItem + HorizontalDivider`。参数项统一范式（Switch+条件输入框+范围 supporting；Slider+数值行）。

**明确不做**（已拆走）：导航重组、delegate/parallel/extraLocalTools、删 Extensions 页、删 workspace UI、硬编码中文。

**触及文件**：`AssistantSubagentProfilePage.kt`、可能新建 `SubagentFormCards.kt`（共享 Card+FormItem 组件）。

### Out of Scope（拆分后明确）

- ❌ 子代理导航重组（启用/深度/delegate/并行收拢到子代理页）→ `subagent-nav-and-features`
- ❌ delegate-only / parallel-execution / extraLocalTools 新功能 → `subagent-nav-and-features`
- ❌ 子代理聊天工具 UI（SpawnSubagentToolUI）→ 后续单独评估
- ❌ search 模块 → `search-httpservice-base`

## 验收标准

- [ ] ScreenTime/日历开关未授权时不保存状态，引导授权；授权后保存。
- [ ] 所有 local 工具的错误/空态走统一 `LocalToolErrorCard`/`LocalToolEmptyCard`。
- [ ] 新增 local 工具只需改 `LocalToolOption`（+meta）+ `LocalTools.getTools`（+分支）+ strings，配置页/subagent 页自动出现。
- [ ] `AssistantSubagentProfilePage` 多 Card 分区，保留所有现有字段（workspace 全套不丢）。
- [ ] `AssistantSubagentProfilePage` 工具列表显示 AskUser（补齐缺失）。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] 真机验证：Profile 页多 Card 布局正常、local 工具开关权限联动、错误态友好。

## 约束

- UI 重构**不能丢字段**。
- `@SerialName` 字符串不变（向后兼容）。
- 统一错误组件文案走 strings.xml。

## 复杂任务说明

需 `design.md`（UI 组件抽象 / 枚举派生机制 / Card 分区布局图 / 权限联动策略）+ `implement.md`（分步检查表）。
