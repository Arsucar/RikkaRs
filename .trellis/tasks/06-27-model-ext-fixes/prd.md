# 扩展管理页/模型选择页多问题修复

## Goal

修复用户在工作区发现的 4 类问题：扩展管理页 Subagent Profiles 层级错位、i18n 残留硬编码、内置子代理语义混淆、模型选择页性能与交互缺陷。

## Background

用户 review 当前 `release/rikka-arsucar` 分支时发现：

1. 扩展管理页 `ExtensionsPage.kt` 把 Subagent Profiles 单独做成顶层标题分组，与用户期望"并入扩展标题下"不符。
2. `ModelList.kt` L217 `contentDescription = "Clear"` 等处仍有英文硬编码未走 `stringResource`。
3. `SubagentRegistry.BUILTIN_PROFILES`（explore/coder/reviewer）作为"内置"暴露给助手，UI 上与 `globalSubagentProfiles` 分两个分区展示，用户期望统一展示为"全局公共"。
4. 模型选择页 `ModelList.kt`（968 行）性能差、交互缺陷：
   - 全部展开/收纳 + provider tabs 按钮位于列表底部，易误触。
   - 收藏区无法折叠。
   - 展开/收纳时列表乱跳（`LaunchedEffect` 自动滚动 + `selectedModelPosition` 依赖 `providerGroupExpanded.toMap()` 共同导致）。
   - `favoriteModels`、`selectedModelPosition`、`providerPositions` 未 memo，每次 settings 变化整列重组。
   - 底部 LazyRow L751 `items(tagFilteredProviders)` 无 key。

## Scope

本父任务协调 4 个独立可验证的子任务：

| 子任务 | 目录 | 类型 |
|--------|------|------|
| 扩展页 Subagent Profiles 层级并入扩展分组 | `06-27-ext-subagent-section` | 轻量（PRD-only） |
| i18n 残留硬编码修复 | `06-27-i18n-hardcode-fix` | 轻量（PRD-only） |
| 内置子代理 UI 统一展示为全局公共 | `06-27-builtin-subagent-global` | 轻量（PRD-only） |
| 模型选择页性能与交互优化 | `06-27-model-select-perf` | 复杂（PRD + design + implement） |

依赖关系：无强依赖；`06-27-model-select-perf` 改动最大，建议最后实现。

## Cross-Child Acceptance Criteria

- AC-1：扩展管理页 Subagent Profiles 不再单独占一级标题，并入"扩展"CardGroup 下作为一项（或子分组）。
- AC-2：`ModelList.kt`、`SubagentRegistry.kt`、`ExtensionsPage.kt` 等用户可见文案无英文/中文硬编码（数据层 profile 内容字段除外）。
- AC-3：助手子代理页不再单独展示"内置"分区，builtin 与 global 合并展示为"全局公共"。
- AC-4：模型选择页全部展开/收纳按钮移出易误触区；收藏区支持折叠；展开/收纳时列表不乱跳；展开/收纳/搜索操作无明显卡顿。
- AC-5：`.\gradlew :app:compileDebugKotlin` 通过；`.\gradlew lint` 无新增 warning。
- AC-6：Debug APK 安装到设备后，手工验证上述 4 项交互。

## Out of Scope

- `SettingProviderDetailPage.kt` 内独立的 `ModelPicker`（注册表多选）性能优化。
- 文生图页 `ImgGenPage.kt` 的"全部展开/折叠"。
- `globalSubagentProfiles` 的数据迁移或字段删除（用户已选方案 2：UI 统一展示，代码层不动）。
- profile 数据字段（systemPrompt/description/displayName）的 i18n——这些是用户可编辑的数据，不属于 UI 文案。

## Decisions

- D-1：内置子代理处理方案 = 保留 `BUILTIN_PROFILES` 代码不动，UI 上把 builtin + global 合并到同一"全局公共"分区展示，助手层保留 `disabledBuiltinSubagents` / `disabledGlobalSubagents` 两套 disable 集合（语义不同：前者禁用硬编码内置，后者禁用用户全局）。
- D-2：模型选择页"全部展开/收纳"按钮移到顶部搜索栏右侧。
- D-3：收藏区折叠状态用 `remember` 内存态，不持久化。
- D-4：列表乱跳修复 = 去掉 `LaunchedEffect` 自动滚动，仅保留 `initialFirstVisibleItemIndex` 初始化定位；用户点 provider chip 时才滚动。
- D-5：性能优化深度 = 仅做 memo/stable/key 最小集，不拆分子组件。
