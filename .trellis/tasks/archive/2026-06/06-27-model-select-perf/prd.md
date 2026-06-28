# 模型选择页性能与交互优化

## Goal

修复模型选择页（`ModelList.kt`，968 行）的性能卡顿与交互缺陷：全部展开/收纳按钮易误触、收藏区无法折叠、展开/收纳时列表乱跳、settings 变化时整列重组。

## Background

`ModelList.kt` 是聊天/助手/翻译/文生图/设置等多入口共用的模型选择底部 sheet，核心问题：

1. **易误触**：L769-791 的"全部展开/收纳"+ "provider tabs 切换"两个 IconButton 紧贴列表底部，且与收藏区/sticky header 同区，用户操作收藏或滚动时容易误触。
2. **收藏区无法折叠**：L532-598 收藏 `stickyHeader` + `items` 写死，无折叠态。收藏数量多时占据大量固定空间。
3. **展开/收纳乱跳**：
   - L405-408 `LaunchedEffect(currentModel, selectedModelPosition, providerGroupExpanded.toMap())` 在 `providerGroupExpanded` 变化时也触发 `animateScrollToItem`。
   - L344 `selectedModelPosition` 的 remember key 含 `providerGroupExpanded.toMap()`，展开/收纳会重算 position 并触发滚动。
   - 结果：用户点任意 provider 分组展开/收纳，列表会跳回 selectedModel 位置。
4. **性能**：
   - L312-317 `favoriteModels` 每次 settings 变化都 `mapNotNull` 生成新 List，未 remember。
   - L344/405/439 三处 `remember(... providerGroupExpanded.toMap())` 依赖，`mutableStateMapOf` 任何条目变更生成新 Map，驱动下游重算。
   - `ModelListState`（L102-144）无 `@Stable`。
   - `Model`（`ai/.../Model.kt`）无 `@Immutable`。
   - L751 底部 LazyRow `items(tagFilteredProviders)` 无 key。

证据：见 explore 子代理报告 `ses_0f6b1d6b9ffeqazmxJ2iM4L7td`。

## Requirements

### 性能（最小 memo/stable 集）
- R-1：`favoriteModels`（L312-317）用 `remember(settings.value, providers, modelType)` memo。
- R-2：`selectedModelPosition`（L344）的 remember key 去掉 `providerGroupExpanded.toMap()`，改为只依赖 `currentModel, favoriteModels, tagFilteredProviders, typeFilteredModelsByProvider`（与 R-4 配合：不再因展开态变化而滚动）。
- R-3：`providerPositions`（L439）保留对 `providerGroupExpanded.toMap()` 的依赖（badge 联动需要），但确保其重算不会反向触发列表滚动（已被 R-4 解耦）。
- R-4：去掉 L405-408 的 `LaunchedEffect(currentModel, selectedModelPosition, providerGroupExpanded.toMap()) { animateScrollToItem }`。sheet 打开时仅靠 `rememberLazyListState(initialFirstVisibleItemIndex = selectedModelPosition)`（L388-390）做初始定位；之后只在用户点 provider chip 时滚动（L732/L756 已有）。
- R-5：`ModelListState`（L102）加 `@Stable`。
- R-6：`Model`（`ai/src/main/java/me/rerere/ai/provider/Model.kt`）加 `@Immutable`（data class + val 全字段，符合 immutable 契约）。
- R-7：L751 底部 LazyRow `items(tagFilteredProviders)` 加 `key = { it.id }`。

### 交互
- R-8：把 L769-791 的"全部展开/收纳" IconButton + "provider tabs 切换" IconButton **从列表底部移到顶部搜索栏右侧**。搜索栏 `OutlinedTextField`（L459-486）右侧加这两个按钮；或搜索栏所在 Row 末尾追加。布局需保证搜索框仍占主要宽度。
- R-9：收藏 `stickyHeader`（L533-541）加箭头折叠按钮，点击切换 `favoriteCollapsed` 状态（`remember { mutableStateOf(false) }`，不持久化）。折叠时收藏 `items`（L543-598）不渲染（保留 stickyHeader 显示"收藏 (N)" + 收起箭头）。展开时恢复。
- R-10：移除底部 Row 中残留的 provider badge 区（L714-792）原本要承载的全部展开按钮后，底部仅保留 provider badge LazyRow/FlowRow；底部 Row 整体保留，但不再有易误触的全局按钮。

## Acceptance Criteria

- AC-1：打开模型选择 sheet，列表初始定位到当前选中模型（通过 `initialFirstVisibleItemIndex`）。
- AC-2：用户点任意 provider 分组头展开/收纳，**列表不自动滚动跳回选中模型**；滚动位置稳定。
- AC-3：用户点 provider chip（底部 badge），列表滚动到对应分组，行为不变。
- AC-4："全部展开/收纳"按钮位于顶部搜索栏右侧，不与列表内容重叠；不易误触。
- AC-5：收藏区有折叠箭头；点击折叠后收藏 items 不显示，stickyHeader 仍可见；再点击恢复。
- AC-6：搜索、tag 筛选、provider 分组展开/收纳、收藏增删、拖拽排序，操作流畅无明显卡顿（主观验证 + 无整列重组）。
- AC-7：`@Stable` / `@Immutable` 注解编译通过；无新 warning。
- AC-8：`.\gradlew :app:compileDebugKotlin` 通过；`.\gradlew lint` 无新增 warning。

## Out of Scope

- `SettingProviderDetailPage.kt` 内独立 `ModelPicker` 的性能优化。
- `ImgGenPage.kt` 的"全部展开/折叠"。
- 把 `ModelList` 拆分为多个子 Composable（父任务 D-5：仅 memo/stable，不拆分）。
- `ProviderBalanceText` 改动。
- sheet 打开动画/手势优化。

## Technical Notes

- `initialFirstVisibleItemIndex` 只在 `rememberLazyListState` 首次构造时生效，符合"仅初始化定位"诉求。
- `mutableStateMapOf.toMap()` 作为 remember key 是 Compose 常见反模式（每次变更生成新 Map 实例）；本任务在 `selectedModelPosition` 处去掉该依赖即可，`providerPositions` 处保留（badge 联动确实需要响应展开态）。
- `Model` 加 `@Immutable` 需确认所有字段都是 val 且无可变集合；若 `abilities` / `inputModalities` 是 `List`/`Set`，`@Immutable` 仍成立（Compose 把不可变集合视为 stable）。
- 折叠状态默认值：用户期望"收藏无法被折叠/收纳不是我期望的"——暗示默认应可折叠，初始展开（显示完整列表），用户主动折叠。
