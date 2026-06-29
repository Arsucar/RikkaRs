# Research: sub/master vs release/rikka-arsucar 子代理导航与配置项

- **Query**: 对比 sub fork 与我们 fork 在子代理配置的导航路径层级和配置项清单
- **Scope**: internal（`git show` + 工作区源码）
- **Date**: 2026-06-29

## 第一部分：导航路径对比

### 共同骨架

两边路由一致（`RouteActivity.kt` / `Screen`）：

```
助手详情 (AssistantDetail)
  └─ 列表项「子代理」→ AssistantSubagentPage
       └─ 点击某 profile 行 → AssistantSubagentProfilePage
```

### sub/master（`AssistantDetailPage` + `AssistantSubagentPage.kt`）

```
助手详情
  └─ [仅] 「子代理」入口行（Connect 图标 + 描述 + 箭头）
        → AssistantSubagentPage（LargeFlexibleTopAppBar 标题 = 子代理）
              ├─ Card（助手级「多配置」— 全在同一页顶部一张 Card 内）
              │    ├─ Switch：启用子代理 (enableSubagents)
              │    ├─ Slider：最大嵌套深度 (subagentMaxDepth, 1..5, steps=3)
              │    ├─ Switch：仅委派模式 (subagentDelegateOnly，依赖 enableSubagents)
              │    └─ Switch：并行工具执行 (parallelToolExecution，依赖 enableSubagents)
              ├─ 区块标题 subagent_profiles_section + 说明
              │    ├─ [可选] Refresh：清空 disabledBuiltinSubagents（恢复被禁用的内置）
              │    └─ Add：新建自定义 profile（AlertDialog → 创建后 navigate 到详情页）
              └─ LazyColumn：每个 profile 一张 CardGroup 行
                   ├─ 点击行 → AssistantSubagentProfilePage(id, profileName)  // 无 createMode
                   ├─ trailing：Delete（内置 → disabledBuiltinSubagents；自定义 → removeSubagentProfile）
                   └─ trailing：ArrowRight01
```

**sub 的「多配置」指什么**：不是多个子代理 Tab/多个列表入口，而是 **子代理专页顶部一张 Card 里集中放 4 项助手级开关/滑块**（启用、深度、delegate-only、并行），然后再进入「profile 列表 → 单个详细配置」。用户从助手详情 **只点一次** 进子代理页，在子代理页 **先改全局行为、再点某个 profile 进细化配置**。

### release/rikka-arsucar（工作区 / `release/rikka-arsucar` 分支 UI）

```
助手详情
  ├─ 「子代理」入口行 → AssistantSubagentPage
  └─ AssistantSubagentHubControls（独立 Card，仍在助手详情页 LazyColumn 内）
       ├─ Switch：启用子代理 (enableSubagents)
       └─ Slider：最大嵌套深度 (subagentMaxDepth, 1..3, steps=1)
       （无 subagentDelegateOnly / parallelToolExecution UI）

AssistantSubagentPage
  ├─ 无顶部助手级 Card（与 sub 不同）
  ├─ 区块标题 subagent_profiles_section + Add + [可选] Refresh（disabledGlobalSubagents）
  └─ profile 列表 CardGroup
       ├─ 点击行 → 本地 profile：AssistantSubagentProfilePage(id, name, createMode)
       │              全局 profile：ExtensionSubagentProfile（扩展页编辑）
       ├─ 列表元数据：workspaceAccess / workspaceApproval / canSpawn（卡片 supporting）
       ├─ 全局项：Delete → disabledGlobalSubagents；禁用项 Refresh 恢复
       └─ 本地项：Copy 克隆、Delete 删自定义
```

**与我们用户描述的对应关系**：

| 用户说法 | 事实核对 |
|----------|----------|
| sub：助手 → 子代理 → 多配置+列表 → 单个详细 | ✅ 助手详情只有入口；**多配置**在 `AssistantSubagentPage` 首 Card；列表同页；再进 Profile |
| 我们：助手 → 子代理+启用+深度 → 列表 → 详细 | ✅ **启用+深度在助手详情** `AssistantSubagentHubControls`；子代理页主要是列表；**缺** sub 同页的 delegate/并行两项 |
| sub 有更多新配置项 | ⚠️ 数据层 sub 有 `extraLocalTools`、内置 `excludedTools` 策略；**UI 上** sub 的 Profile 页反而 **没有** 我们 fork 的 workspace / canSpawn / excludedTools / summaryContinuation 等 |

### 关键文件

| 角色 | sub/master | release/rikka-arsucar |
|------|------------|------------------------|
| 助手详情入口 + 页内控件 | `AssistantDetailPage.kt`（仅入口） | `AssistantDetailPage.kt`（入口 + `AssistantSubagentHubControls`） |
| 子代理列表页 | `AssistantSubagentPage.kt`（助手级 4 项 + 列表） | `AssistantSubagentPage.kt`（列表 + global 语义） |
| 页内助手级控件组件 | （内联在 SubagentPage 首 Card） | `AssistantSubagentHubSection.kt` → `AssistantSubagentHubControls` |
| 单 profile 编辑 | `AssistantSubagentProfilePage.kt` | `AssistantSubagentProfilePage.kt` + `SubagentProfileForm` |

---

## 第二部分：配置项差异表

### SubagentProfile 数据字段（`SubagentProfile.kt`）

| 字段名 | 我们 fork | sub | 差异说明 | 是否值得对齐（调研备注） |
|--------|-----------|-----|----------|-------------------------|
| name / displayName / description / systemPrompt | ✅ | ✅ | 一致 | — |
| chatModelId / temperature / topP / maxTokens / reasoningLevel | ✅ | ✅ | 一致；UI 均为可空继承父代理 | — |
| maxSteps | ✅ 默认 32 | ✅ 默认 32；内置各档不同 | 内置默认值不同（见第三部分） | 可对齐内置默认，非新字段 |
| inheritTools | ✅ | ✅ | 一致 | — |
| localTools | ✅ | ✅ | 一致；`inheritTools=false` 时 UI 展示 | — |
| enabledSkills / mcpServerIds | ✅ | ✅ | 一致 | — |
| excludedTools | ✅ | ✅ | **我们 UI**：`inheritTools=true` 时可编辑 chip；**sub UI**：Profile 页未暴露 | sub 靠内置默认+prompt；我们显式可配 |
| extraLocalTools | ❌ | ✅ | sub 数据层「继承工具时额外注入」；我们无此字段 | 需模型+运行时+UI 才完整引入 |
| streamOutput / enableMemory / summaryMinLength | ✅ | ✅ | sub 默认 summaryMinLength=0、continuation=0（常量） | 我们 UI 有 continuation；sub Profile UI 无 continuation |
| summaryContinuationAttempts | ✅ UI+字段 | ✅ 字段默认 0；**sub Profile UI 无** | 我们多 UI 项 | 保留我们能力即可 |
| workspaceAccess | ✅ | ❌ | 我们 workspace 权限体系 | 与 sub 架构分叉，非 sub「新增」 |
| workspaceApproval | ✅ | ❌ | 同上 | 同上 |
| allowedPathPrefixes | ✅ | ❌ | 我们路径前缀 | 同上 |
| canSpawn | ✅ UI+字段 | ❌ 字段无 | 我们 per-profile 是否可再 spawn | sub 用助手级 depth/delegate 约束 |
| toolApprovalOverrides | ✅ | ❌ | OVERRIDE 时 workspace 工具审批 | 我们独有 |
| 合并来源 | custom + **global** + `SubagentRegistry`；`disabledGlobalSubagents` | custom + **BUILTIN**；`disabledBuiltinSubagents` | 我们多「扩展页全局 profile」导航 | 架构差异，非单纯 UI |

### Assistant 级字段（子代理相关）

| 字段 | 我们 fork (`Assistant.kt` release) | sub (`Assistant.kt`) |
|------|--------------------------------------|----------------------|
| enableSubagents | ✅ 默认 false | ✅ 默认 true |
| subagentMaxDepth | ✅ | ✅ |
| subagentProfiles | ✅ | ✅ |
| disabledBuiltinSubagents | ✅（兼容字段） | ✅ 禁用内置 |
| disabledGlobalSubagents | ✅ | ❌ |
| subagentDelegateOnly | ❌（模型/UI 均无） | ✅ + SubagentPage UI |
| parallelToolExecution | ❌（模型/UI 均无） | ✅ + SubagentPage UI |

### 单页 UI 字段出现顺序（Profile 详细页）

**sub**（`AssistantSubagentProfilePage.kt`）：name → displayName → description → systemPrompt → model → temperature → topP → maxTokens → reasoning → maxSteps → inheritTools →（若 !inheritTools）localTools / skills / mcp → stream → memory → summaryMinLength。

**我们**（`SubagentProfileForm`）：name → displayName → description → systemPrompt → model → **workspaceAccess** → **workspaceApproval** →（OVERRIDE）**toolApprovalOverrides** → **canSpawn** → temperature → topP → maxTokens → reasoning → maxSteps → **allowedPathPrefixes** → stream → inheritTools →（inherit 时）**excludedTools** →（!inherit）localTools/skills/mcp → memory → summaryMinLength → **summaryContinuationAttempts**。

**sub 相对我们 Profile UI「多出来」的**：无（sub Profile 更简）。

**我们相对 sub Profile UI「多出来」的**：workspace 全套、canSpawn、allowedPathPrefixes、inherit 时 excludedTools、summaryContinuationAttempts、global 只读提示与扩展页跳转。

---

## 第三部分：内置子代理默认值差异

内置定义位置：**我们** `SubagentRegistry.BUILTIN_PROFILES`；**sub** `SubagentProfile.BUILTIN`。

| 字段 | explore（我们 / sub） | coder（我们 / sub） | reviewer（我们 / sub） |
|------|------------------------|---------------------|-------------------------|
| maxSteps | 48 / **16** | 64 / **20** | 24 / **12** |
| workspaceAccess | READ_ONLY / *无字段* | FULL / *无* | READ_ONLY / *无* |
| workspaceApproval | INHERIT / *无* | AUTO / *无* | INHERIT / *无* |
| canSpawn | false / *无* | true / *无* | false / *无* |
| inheritTools | true（默认）/ true（默认） | true / true | true / **true（显式）** |
| excludedTools | **emptySet** / **FILE_MUTATING**（write+edit） | empty / empty（默认） | **write+edit+shell** / **FULLY_READONLY**（write+edit+shell） |
| summaryMinLength（类型默认） | 200 / **0（DEFAULT）** | 200 / 0 | 200 / 0 |
| summaryContinuationAttempts | 1 / **0（DEFAULT）** | 1 / 0 | 1 / 0 |

sub 内置还带更长 `systemPrompt`（只读纪律、验证要求等）；我们内置 prompt 更短。

---

## 第四部分：对齐建议（基于现状差异，供主代理决策）

### 导航路径：贴近 sub 体验

1. **把助手级子代理控件收拢到 `AssistantSubagentPage` 首 Card**（对齐 sub 首屏「多配置」）  
   - 从 `AssistantDetailPage` **移除或弱化** `AssistantSubagentHubControls`，避免「助手详情改一半、子代理页只有列表」的分裂感。  
   - 涉及：`AssistantDetailPage.kt`、`AssistantSubagentHubSection.kt`、`AssistantSubagentPage.kt`（把 Hub 控件迁入或与 sub 一样内联 4 项）。

2. **补回 sub 有、我们缺的助手级 UI（若产品仍要行为）**  
   - `subagentDelegateOnly`、`parallelToolExecution`：sub 在 `Assistant.kt` + `AssistantSubagentPage`；我们 **release 模型已无此二字段**，要对齐需先恢复 `Assistant` 序列化字段 + `ChatService`/`GenerationHandler` 消费逻辑，再加 Switch。  
   - `subagentMaxDepth` 范围：sub 1..5；我们 Hub 1..3 — 若对齐 sub 需统一 Slider range/steps。

3. **保留我们优于 sub 的列表能力（可选）**  
   - 全局 profile、`ExtensionSubagentProfile`、clone/disable-global 可继续放在列表页，不必删掉；仅在首 Card 上对齐 sub 的「先总控再列表」节奏。

### 新配置项 / 字段：sub 值得引入的部分

| 项 | 说明 | 依赖 |
|----|------|------|
| `extraLocalTools` | sub 在 inheritTools 时仍可 additive 本地工具 | 数据类、工具组装（参考 sub `SubagentHost`）、Profile UI |
| 内置 `excludedTools` 默认 | explore/reviewer 结构性只读 | 仅改 `SubagentRegistry.BUILTIN_PROFILES` 或迁移 global 种子，**不砍 workspace** |
| DEFAULT summary 0 / continuation 0 | 减少无意义扩写轮次 | 改默认常量或内置 profile 字段；我们已有 UI 可保留给用户调高 |
| Profile 页 **不**建议砍掉 workspace/canSpawn | 这是我们 fork 差异化能力 | 与 sub「砍 workspace」方向相反 |

| 项 | 不建议为对齐 sub 而删 |
|----|----------------------|
| workspaceAccess / Approval / allowedPathPrefixes / toolApprovalOverrides | 运行时与 `ChatService` 已绑定 |
| `summaryContinuationAttempts` UI | sub 无 UI 但我们有，属于增强 |

### 数据层注意

- 合并 API：sub `mergeSubagentProfiles(custom, disabledBuiltin)` vs 我们 `merge(..., global, disabledGlobal)` — UI 导航对齐 **不必**改回 sub 的 BUILTIN-only，但文档与测试需继续区分 global 语义。  
- `SubagentResult.transcript`：sub `ToolCall.name` vs 我们 `toolName` — 跨 fork 移植 UI/序列化时需注意字段名。

## Caveats / Not Found

- 工作区 `Assistant.kt` 与 `git show release/rikka-arsucar:Assistant.kt` 一致：**无** `subagentDelegateOnly` / `parallelToolExecution`；sub 分支 **有** 且在 `AssistantSubagentPage` 展示。  
- sub `AssistantSubagentProfilePage` **未**暴露 `excludedTools` 编辑；「sub 更多配置项」主要指 **助手级** delegate/并行/深度范围，以及数据层 `extraLocalTools`、内置 excluded 策略，而非 Profile 表单比我们还多。  
- 未在本次调研中逐行核对 `ChatService` 对我们缺字段时的默认行为（仅确认模型层缺失）。