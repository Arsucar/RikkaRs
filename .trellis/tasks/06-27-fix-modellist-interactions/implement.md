# Implement: Fix ModelList interactions

## Checklist

- [ ] **1. ModelList.kt**: 添加全部展开/折叠按钮
  - 在 provider badge 行右侧添加新 IconButton（在 providerTabsExpanded 切换按钮之前）
  - 计算 `allCollapsed` 状态决定图标
  - 点击逻辑：遍历 providers 设置 `providerGroupExpanded`

- [ ] **2. ModelList.kt**: 修复 selectedModelPosition
  - 在累加 models.size 前检查 `providerGroupExpanded[provider.id] != false`
  - 选中模型在折叠组中：返回 header position 并展开

- [ ] **3. ModelList.kt**: FlowRow chip 点击后收纳
  - FlowRow 中 AssistChip.onClick 末尾加 `providerTabsExpanded = false`

- [ ] **4. ModelList.kt**: Sheet skipPartiallyExpanded
  - `rememberBottomSheetState(skipPartiallyExpanded = true)`

- [ ] **5. Build + install**

## Validation

```bash
.\gradlew :app:compileDebugKotlin
.\gradlew :app:installDebug
```
