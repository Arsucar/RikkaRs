# Research: sub fork 子代理 UI 配置与「统一风格」优化

- **Query**: linklink256/rikkahub-sub 在子代理 UI 配置方面做了哪些优化，特别是统一风格
- **Scope**: internal（git diff `release/rikka-arsucar`..`sub/master`）
- **Date**: 2026-06-29
- **Fork base**: `release/rikka-arsucar` @ `a27d6a2d`（用户提及的 `2f1f1ac0` 与当前 HEAD 一致为 `a27d6a2d`）

## Findings

### 1. sub 的子代理 UI 文件清单

| 文件路径 | 说明 |
|---|---|
| `app/.../ui/pages/assistant/detail/AssistantSubagentPage.kt` | 助手内子代理列表 + 总开关/深度等 |
| `app/.../ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | 单个子代理配置表单 |
| `app/.../ui/components/message/tools/SubagentToolUI.kt` | 聊天中 `spawn_subagent` 工具展示（**sub 独有文件名**） |

**sub 删除、我们 fork 仍保留的 UI 相关文件**（对比 `sub/master` 为删除）：

| 文件路径 | 说明 |
|---|---|
| `AssistantSubagentHubSection.kt` | 我们 fork 抽出的 Hub 控件 Card；sub 内联进 `AssistantSubagentPage` |
| `SubagentUiHelpers.kt` | 全局/本地列表合并、`SubagentListEntry`、workspace 文案 helper |
| `ExtensionSubagentsPage.kt` / `ExtensionSubagentProfilePage.kt` | 扩展页全局子代理管理；sub 无此路径 |
| `SubagentToolUIs.kt`（我们 fork） | 含 `SpawnSubagentToolUI` + i18n/审计展示；sub 用更薄的 `SubagentToolUI.kt` |

我们 fork 聊天工具 UI：`SpawnSubagentToolUI` in `SubagentToolUIs.kt`（已注册于 `ToolUI.kt`）。

---

### 2. 各 UI 文件 diff 摘要（`release/rikka-arsucar` → `sub/master`）

#### `AssistantSubagentPage.kt`（约 +174 / -209 行，净重构）

| 类别 | 改动要点 |
|---|---|
| **风格统一** | 顶部增加单张 `Card` + `CustomColors.cardColorsOnSurfaceContainer`，内用 `FormItem` + `HorizontalDivider` 串联：启用子代理、最大深度 `Slider`、delegate-only、并行执行 —— 与 Profile 页同一套「设置卡片」语言 |
| **风格统一** | Profile 列表仍用 `CardGroup`，但去掉 `alpha` 灰显、全局 badge、卡片 supporting 里的 workspace/spawn 元信息行，列表更干净 |
| **布局重构** | 子代理总控从独立 `AssistantSubagentHubSection` **合并进本页**；不再读 `settings.globalSubagentProfiles` |
| **功能取舍** | 移除：全局子代理跳转 Extensions、复制 profile、全局禁用/恢复、空列表文案；新建/删除对话框部分 **硬编码中文**（非 stringResource） |
| **数据模型** | `mergeSubagentProfiles` + `disabledBuiltinSubagents`（非 `disabledGlobalSubagents`） |

#### `AssistantSubagentProfilePage.kt`（约 +378 / -783 行，大幅瘦身）

| 类别 | 改动要点 |
|---|---|
| **风格统一（核心）** | 单一大表单 `SubagentProfileForm` 拆成 **多张主题 Card**，每张只一类设置：`基本信息` / `系统提示词` / `模型与参数` / `行为参数` / `工具与 MCP`（条件显示）/ `流式与记忆` |
| **风格统一** | 统一模式：`Card(colors = CustomColors.cardColorsOnSurfaceContainer)` + `FormItem(modifier.padding(8.dp))` + `HorizontalDivider`；开关放 `FormItem.tail`；数值项带 `description` + Slider 下方 secondary 小字 |
| **风格统一** | 温度/topP：`Switch` 启用后才显示 `OutlinedTextField`，带 `isError` + `supportingText("0-2" / "0-1")`；maxTokens 带 inherit placeholder + supporting |
| **布局重构** | 删除共享 `SubagentProfileForm` 对外暴露、`readOnly` / `createMode` / 全局只读提示条 |
| **功能取舍** | **移除整块 workspace UI**：`WorkspaceAccess`/`WorkspaceApproval` `Select`、`allowedPathPrefixes` PathChip、`canSpawn`、`excludedTools` PathChip、`toolApprovalOverrides` 编辑器、`summaryContinuationAttempts` |
| **功能取舍** | `inheritTools=false` 时 Local Tools / Skills / MCP 各 **独立 Card** + `FlowRow` + `FilterChip`（部分 label 仍为英文 + todo string） |
| **实现细节** | Skills/MCP 用 `koinInject()`（`SkillManager` / `SettingsStore`），非 VM 注入；导航标题简化为 `profileName` 字符串 |

#### `SubagentToolUI.kt`（sub：**新增** +277 行）

| 类别 | 改动要点 |
|---|---|
| **新组件** | `object SubagentToolUI : ToolUIRenderer`，工具名 `spawn_subagent` |
| **风格统一** | 折叠摘要用嵌套 `ChainOfThought`，`surfaceContainerHighest` 背景，与主对话思维链组件一致 |
| **预览** | `Preview` 底部 sheet：标题 + 复用 `Summary` |
| **相对我们 fork** | 我们 `SpawnSubagentToolUI` 更重：i18n 标题（步数/token/工具调用数）、`SubagentRegistry` displayName、更完整 result 解析；sub 版更薄、metadata 驱动 transcript |

#### 已删除文件（sub 侧）

- `AssistantSubagentHubSection.kt`、`SubagentUiHelpers.kt`：逻辑并入 `AssistantSubagentPage` 或 `SubagentProfile.kt` 的 `mergeSubagentProfiles`。

---

### 3. sub 内置子代理配置

- **数量**：3 个，无 explore/coder/reviewer 之外的内置名。
- **定义位置**：`SubagentProfile.kt` 内 `SubagentProfile.BUILTIN`（**无** `SubagentRegistry.kt`）。

| name | displayName | maxSteps | inheritTools | excludedTools 要点 | 其它 |
|---|---|---:|---|---|
| `explore` | Explorer | 16 | true（默认） | `FILE_MUTATING_TOOLS`（write/edit，**保留 shell**） | 长 systemPrompt（只读 shell 纪律 + verify） |
| `coder` | Coder | 20 | true | 无 | 编码+验证摘要 |
| `reviewer` | Reviewer | 12 | true | `FULLY_READONLY_EXCLUDED_TOOLS`（含 shell） | 只读评审 |

默认摘要：`summaryMinLength=0`、`summaryContinuationAttempts=0`（sub 默认不扩写追问）。

我们 fork 内置在 `SubagentRegistry.BUILTIN_PROFILES`，同样 3 个，但用 **`workspaceAccess` / `workspaceApproval` / `canSpawn`** 表达权限（如 coder `FULL`+`AUTO`+`canSpawn=true`），步数更大（48/64/24）。

---

### 4. `SubagentProfile` 字段：sub vs 我们 fork

| 字段 / 能力 | sub (`sub/master`) | 我们 fork (`release/rikka-arsucar`) |
|---|---|---|
| `workspaceAccess` | **无**（用 `excludedTools` + prompt） | 有（NONE/READ_ONLY/FULL） |
| `workspaceApproval` | **无** | INHERIT/AUTO/OVERRIDE |
| `allowedPathPrefixes` | **无** | 默认 `["/workspace"]` |
| `canSpawn` | **无** | 有（coder 可 true） |
| `toolApprovalOverrides` | **无** | 有 |
| `extraLocalTools` | **有** | **无**（我们仅 `localTools`） |
| `summaryMinLength` 默认 | 0 | 200 |
| `summaryContinuationAttempts` 默认 | 0 | 1 |
| `maxSteps` 默认 | 32 | 32 |
| BUILTIN 位置 | `SubagentProfile.BUILTIN` | `SubagentRegistry.BUILTIN_PROFILES` |
| 合并 API | `mergeSubagentProfiles(custom, disabledBuiltin)` | Registry + global + `disabledGlobalSubagents` |
| `SubagentResult.toolCallCount` | 有 | 需对照我们 fork 是否已有（审计相关） |

共有字段：name, displayName, description, systemPrompt, chatModelId, temperature, topP, maxTokens, reasoningLevel, maxSteps, inheritTools, localTools, enabledSkills, mcpServerIds, excludedTools, enableMemory, streamOutput, summary* 等。

---

### 5. 值得 fork 学习的 UI 优化点（按优先级）

**P0 — 风格统一（建议只抄 UI 模式，不砍功能）**

1. **Profile 编辑页多 Card 分区**：按「基本信息 / Prompt / 模型参数 / 行为 / 工具 / 其它」分 Card，每段 `FormItem` + `Divider`，与我们现有 `SubagentProfileForm` 单 Card 堆叠相比，扫描成本更低。
2. **列表页顶部设置 Card**：将 `AssistantSubagentHubControls` 的视觉与 sub 内联块对齐（同一 `Card`+`FormItem` 密度）；我们可保留 HubSection 文件，只统一 spacing/文案/启用态。
3. **参数项统一范式**：temperature/topP 的 Switch+条件输入框+范围 supporting；maxSteps/summaryMinLength 的 Slider + `secondary.copy(alpha=0.75f)` 数值行 —— 可直接套在我们保留的字段上。
4. **`inheritTools=false` 工具区**：Local / Skills / MCP **分三张 Card** + `FlowRow(FilterChip)`，比我们单段 `LocalToolsSkillMcpSection` 更清晰（我们已有类似结构，可对齐 padding/label 用 stringResource）。

**P1 — 体验增强**

5. **聊天 `spawn_subagent` 嵌套 ChainOfThought**：sub 的 `SubagentToolUI` 用统一 `ChainOfThought` + step 图标（Sparkles/Tools/Connect）；我们已有更丰富的 `SpawnSubagentToolUI`，可借鉴 **cardColors / collapsedVisibleCount** 与更轻的 step 布局，避免重复造轮子。
6. **列表卡片简化**：sub 去掉卡片内 workspace/spawn 三行 meta，仅 description —— 若我们保留权限模型，可用 **一行短 badge** 代替三行小字，避免信息墙。

**P2 — 慎搬（与 fork 产品方向冲突）**

7. **去掉 Extensions 全局子代理页**：sub 删除 Extension* 页面；我们 fork 明确做 global profiles，**不应**跟 sub 删页。
8. **硬编码中文对话框**：sub 新建/删除对话框未 i18n；我们应保持 `strings.xml`。
9. **砍掉 workspace/canSpawn UI**：sub 为简化模型；我们 fork 的 workspace 权限是差异化能力，应 **保留字段，只学 Card 分区样式**。

**P3 — 架构差异（非纯 UI）**

10. `mergeSubagentProfiles` + `disabledBuiltinSubagents` vs global profiles —— 合并策略不同，UI 统一不依赖照搬数据层。

---

## Caveats / Not Found

- `SubagentRegistry.kt` 在 `sub/master` **不存在**；内置与 merge 均在 `SubagentProfile.kt`。
- 对比基准为当前 `release/rikka-arsucar` HEAD `a27d6a2d`，若你本地 commit 与 `2f1f1ac0` 不一致，请用 `git merge-base` 重跑 diff。
- sub 的 `SubagentToolUI.kt` 与我们 `SubagentToolUIs.kt` 是**并行实现**，不是简单重命名；移植时需合并注册表（`ToolUI.kt` 只应保留一个 `spawn_subagent` renderer）。
- sub Profile 页仍有英文硬编码与 `// todo: use string resource`，成熟度低于我们 fork 的 i18n。