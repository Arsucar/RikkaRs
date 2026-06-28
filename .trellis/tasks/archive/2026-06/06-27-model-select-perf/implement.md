# 执行计划：模型选择页性能与交互优化

## 前置确认（实现前必做）

- [ ] `Read app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` 全文（已知 968 行，确认行号未漂移）
- [x] `Read ai/src/main/java/me/rerere/ai/provider/Model.kt`：Model 全 val + 只读 List/Set，**确定用 `@Immutable`**
- [x] `Read ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`：sealed abstract + 大量 `var`，**本次不动**（超 D-5 边界）

## 实现步骤（有序）

### Step 1: 注解（低风险，先做验证编译）
- [ ] `Model.kt` L8 加 `@Immutable`（若字段满足；否则 `@Stable`，再否则跳过并在 check 记录原因）
- [ ] `ModelList.kt` L102 `ModelListState` 加 `@Stable`，import `androidx.compose.runtime.Stable`
- [ ] 编译：`.\gradlew :app:compileDebugKotlin`

### Step 2: 删除乱跳根因（核心修复）
- [ ] 删除 `ModelList.kt` L405-408 整个 `LaunchedEffect(currentModel, selectedModelPosition, providerGroupExpanded.toMap()) { ... animateScrollToItem ... }`
- [ ] `selectedModelPosition`（L344）remember key 去掉 `providerGroupExpanded.toMap()`
- [ ] 编译 + 手工验证：展开/收纳任意 provider 分组，列表不跳

### Step 3: favoriteModels memo
- [ ] `ModelList.kt` L312-317 包一层 `remember(settings.value, providers, modelType) { ... }`
- [ ] 编译

### Step 4: 底部 LazyRow key
- [ ] `ModelList.kt` L751 `items(tagFilteredProviders)` → `items(tagFilteredProviders, key = { it.id })`
- [ ] 编译

### Step 5: 按钮迁移到顶部搜索栏右侧
- [ ] 在 L459 的 `Surface`（搜索栏）外层包一个 `Row`，搜索栏 `Modifier.weight(1f)`，右侧放两个 IconButton（全部展开/收纳 + provider tabs 切换），onClick 逻辑从 L770-775 和 L783-784 原样搬入
- [ ] 删除 L769-791 底部那两个 IconButton（保留底部 Row 的 provider badge LazyRow/FlowRow 部分 L714-768）
- [ ] 编译 + 手工验证：按钮在顶部可点；底部不再有全局按钮

### Step 6: 收藏区折叠
- [ ] L319 附近新增 `var favoriteCollapsed by remember { mutableStateOf(false) }`
- [ ] L533-541 收藏 stickyHeader 改为 `Row(clickable { favoriteCollapsed = !favoriteCollapsed })`，左侧加箭头 Icon（`ArrowDown01`/`ArrowRight01`），Text 显示"收藏 (N)"
- [ ] L543-598 收藏 `items` 外层包 `if (!favoriteCollapsed) { ... }`
- [ ] **拖拽索引校验**：`reorderableState` 的 `favoriteStartIndex` 计算（L411-435）在折叠态下不会触发（items 不渲染，无拖拽事件），无需改；但展开态下索引仍正确。手工验证拖拽排序。
- [ ] 编译

### Step 7: 全量验证
- [ ] `.\gradlew :app:compileDebugKotlin`
- [ ] `.\gradlew lint`（确认无新增 warning，特别是 `HardcodedText`、`MutableCollection`）
- [ ] adb devices 确认设备
- [ ] `.\gradlew :app:installDebug`
- [ ] 设备手工验证：
  - 打开聊天 → 点模型选择 → 初始定位到当前模型 ✓
  - 展开/收纳 provider 分组 → 列表不跳 ✓
  - 点底部 provider chip → 滚动到对应分组 ✓
  - 顶部"全部展开/收纳"按钮可点 ✓
  - 收藏区可折叠/展开 ✓
  - 收藏拖拽排序（展开态）正常 ✓
  - 搜索/tag 筛选流畅 ✓

## 验证命令

```powershell
.\gradlew :app:compileDebugKotlin
.\gradlew lint
adb devices
.\gradlew :app:installDebug
```

## 风险与回滚

- 风险点见 `design.md` 「风险」段。
- 每步独立可回滚；若 Step 2 引入新问题，先回滚 Step 2 再排查。
- `Model` 注解若编译/运行异常，立即移除注解（Step 1 可独立回滚）。

## Review Gate

实现完不提交，等用户 review。展示 diff + 设备验证结果。
