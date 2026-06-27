# Fix ModelList interactions: collapse/expand/scroll/sheet

## Parent

`06-27-fix-modellist-subagent-prod`

## Goal

修复 ModelList sheet 的 4 个交互问题。

## Requirements

### R1: 全部展开/全部折叠按钮
- 在 provider badge 行（LazyRow/FlowRow 切换行）旁添加一个「全部折叠/全部展开」IconButton
- 点击"全部折叠"：`providerGroupExpanded` 所有 key 设为 `false`
- 点击"全部展开"：`providerGroupExpanded` 所有 key 设为 `true`（null 表示展开也行）
- 图标用 `HugeIcons.ArrowDown01`/`ArrowUp01`，与展开/折叠语义一致

### R2: selectedModelPosition 修复
- `selectedModelPosition` 计算时，当 provider group 被折叠（`providerGroupExpanded[provider.id] == false`），该 provider 下的模型不计入 position
- 但如果选中模型恰好在折叠组中，需先展开该组再定位

### R3: 展开 provider tab 后点击跳转+自动收纳
- FlowRow 中点击 provider chip 后：
  1. 正确跳转到 `providerPositions[provider.id]` 位置
  2. 自动将 `providerTabsExpanded` 设为 `false`（收纳回 LazyRow）
- 确保跳转位置在 FlowRow/LazyRow 切换后仍然正确

### R4: 全屏 sheet 交互模型
- 保留 `fillMaxSize` 内容区但添加 `skipPartiallyExpanded = true` 到 `rememberBottomSheetState`，使 sheet 直接展开到最大高度
- 或换用 `BottomSheetScaffold` + `SheetValue.Expanded` 初始值确保全屏感
- 保持底部拖拽手柄可见

## Acceptance Criteria

- [ ] AC1: 有全部展开/折叠按钮，一键操作
- [ ] AC2: 打开 sheet 时选中模型滚动到正确位置
- [ ] AC3: 展开 tab 后点击 provider chip 能跳转并自动收纳
- [ ] AC4: Sheet 全屏展示但保留拖拽收起交互
