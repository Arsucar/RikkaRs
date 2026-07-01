# Design: Fix ModelList interactions

## Changes

### D1: 全部展开/全部折叠按钮

**文件**: `ModelList.kt`
- 在 provider badge 行的右侧 IconButton（`providerTabsExpanded` 切换）旁，新增一个 IconButton
- 图标：`ArrowDown01`（全部展开）/ `ArrowUp01`（全部折叠）
- 点击"全部展开"：遍历所有 providers，`providerGroupExpanded[provider.id] = true`
- 点击"全部折叠"：遍历所有 providers，`providerGroupExpanded[provider.id] = false`
- 状态计算：`allCollapsed = providers.all { providerGroupExpanded[it.id] == false }`，根据此切换图标

### D2: selectedModelPosition 尊重折叠状态

**文件**: `ModelList.kt`
- `selectedModelPosition` 计算循环中，对每个 provider：
  - `position += 1`（header 总是占位）
  - 仅当 `providerGroupExpanded[provider.id] != false`（展开状态）时才累加 `models.size`
  - 如果选中模型在折叠组中：在该 provider header 前展开该 group，并返回 header 位置

### D3: FlowRow 中 provider chip 点击后自动收纳

**文件**: `ModelList.kt`
- FlowRow 分支中的 `AssistChip.onClick` 末尾添加 `providerTabsExpanded = false`
- 确保跳转后再收纳（顺序：先 animateScrollToItem，后设 providerTabsExpanded）

### D4: Sheet 全屏交互

**文件**: `ModelList.kt`
- `rememberBottomSheetState` 添加 `skipPartiallyExpanded = true`
- 保留 `fillMaxSize`，但 SheetValue 只允许 Expanded 和 Hidden

## Tradeoffs

- D2 的"选中模型在折叠组中先展开"会增加一次 recomposition，但用户感知为"打开就自动定位"
- D4 的 skipPartiallyExpanded 破坏部分用户习惯（喜欢半屏的），但任务要求全屏交互，符合需求
