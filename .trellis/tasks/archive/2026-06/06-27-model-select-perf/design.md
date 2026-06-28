# 设计：模型选择页性能与交互优化

## 架构与边界

改动限定在单文件 `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`，外加 `ai/src/main/java/me/rerere/ai/provider/Model.kt` 一处注解。不引入新组件、不拆分文件、不改数据流。

## 关键改动点（按文件 + 行号）

### A. ModelList.kt 顶部状态区（L307-440）

```
// 现状（反模式）
val favoriteModels = settings.value.favoriteModels.mapNotNull { ... }   // L312 未 remember
val providerGroupExpanded = remember { mutableStateMapOf<Uuid, Boolean>() }  // L322

val selectedModelPosition = remember(currentModel, favoriteModels, tagFilteredProviders,
    typeFilteredModelsByProvider, providerGroupExpanded.toMap()) { ... }  // L344 含 toMap()

LaunchedEffect(currentModel, selectedModelPosition, providerGroupExpanded.toMap()) {
    lazyListState.animateScrollToItem(selectedModelPosition)             // L405-408 乱跳根因
}

val providerPositions = remember(tagFilteredProviders, favoriteModels,
    searchFilteredModelsByProvider, providerGroupExpanded.toMap()) { ... }  // L439 保留
```

改动：
1. `favoriteModels` → `remember(settings.value, providers, modelType) { ... }`。
2. `selectedModelPosition` remember key 去掉 `providerGroupExpanded.toMap()`。
3. 删除 L405-408 整个 `LaunchedEffect`。
4. `providerPositions` 不动（保留 toMap 依赖）。

### B. ModelListState 注解（L102）

```kotlin
@Stable   // 新增
class ModelListState internal constructor(...) { ... }
```

### C. Model 注解（Model.kt L7-20）

已 Read 确认：`Model` 全字段 val，类型为 String/Uuid/enum/只读 List/只读 Set，无 `var`、无 `mutableStateOf`、无 `MutableList`。**满足 `@Immutable` 契约**。

```kotlin
@Immutable   // 新增（在 @Serializable 上方或下方均可）
@Serializable
data class Model(...)
```

### C-2. ProviderSetting 不动（已知限制）

`ProviderSetting`（ProviderSetting.kt L26-260）是 abstract sealed class，子类用大量 `var`（id/enabled/name/models/apiKey...）。这导致 `List<ProviderSetting>` 作为 Compose 入参时不稳定，是 `ModelList` 重组的潜在来源之一。**但本次范围内不修复**（sealed + var + @Composable 字段重构风险大，超父任务 D-5"仅 memo/stable 最小集"边界）。在 check.jsonl 的 thinking guide 提示下，review 时需识别"加 @Immutable 就能解决全部卡顿"是 false-positive。

### D. 顶部搜索栏 + 按钮迁入（L459-511）

现状：搜索栏 L459-486 单独 Surface；tag 筛选 L488-511 单独 LazyRow；底部 L714-792 是 provider badge + 全局按钮。

改后布局：
```
Row(horizontalArrangement = SpaceBetween) {
    OutlinedTextField(...)  // weight(1f)
    Row {
        IconButton(全部展开/收纳) { ArrowDown01 / ArrowUp01 }
        IconButton(provider tabs 切换) { ArrowDown01 / ArrowUp01 }
    }
}
LazyRow { FilterChip(all) + items(allTags) FilterChip }   // tag 筛选不动
```

把 L769-791 两个 IconButton 的 onClick 逻辑原样搬上来。

### E. 收藏区折叠（L532-598）

```kotlin
var favoriteCollapsed by remember { mutableStateOf(false) }   // 新增

if (favoriteModels.isNotEmpty()) {
    stickyHeader {
        Row(clickable { favoriteCollapsed = !favoriteCollapsed }) {
            Icon(if (favoriteCollapsed) ArrowRight01 else ArrowDown01)
            Text(stringResource(R.string.model_list_favorite) + " (${favoriteModels.size})")
        }
    }
    if (!favoriteCollapsed) {
        items(favoriteModels, key = { "favorite:" + it.first.id }) { ... }  // 原样
    }
}
```

### F. 底部 Row 简化（L714-792）

移除 L769-791 的两个全局 IconButton 后，底部 Row 仅保留 provider badge LazyRow/FlowRow。底部 Row 容器保留（padding/alignment 不动）。

### G. 底部 LazyRow key（L751）

```kotlin
items(tagFilteredProviders, key = { it.id }) { provider -> ... }
```

## 数据流

无变化。`favoriteModels` 仍从 `settings.value.favoriteModels` 派生；`providerGroupExpanded` 仍是内存态 `mutableStateMapOf`；`providerPositions` 仍驱动底部 badge 联动。

## 兼容性

- `Model` 加 `@Immutable`：`Model` 是 data class 且字段全 val，注解只是告诉 Compose 编译器跳过重组检查，运行时无影响。需确认无 `mutableStateOf` 字段或 `MutableList`。
- `ModelListState` 加 `@Stable`：类内用 `mutableStateOf`，`@Stable` 是正确注解。
- 收藏折叠不持久化：sheet 关闭再打开回到默认展开态。用户可接受（父任务 D-3）。

## 回滚点

每条改动独立，可单独回滚：
- 注解（B/C）→ 删一行。
- memo（A1）→ 还原未 remember。
- 删 LaunchedEffect（A3）→ 还原。
- 按钮迁移（D）→ 还原底部。
- 收藏折叠（E）→ 删 `favoriteCollapsed` + 还原 items 无条件渲染。

## 风险

- `Model` 若含 `MutableList`/`var`，`@Immutable` 会误导 Compose，导致不重组。**实现前必须 Read Model.kt 确认**；若不满足，改用 `@Stable` 或不加。
- 收藏折叠后 stickyHeader 仍占位，拖拽排序（`rememberReorderableLazyListState` L410）依赖 favorite items 的索引计算（L411-435），折叠时 `favoriteModels` 不为空但 items 不渲染，需确认 reorderableState 的 `from/to.index` 计算不会因 items 缺失而错位。**实现时折叠状态下禁用拖拽或调整 favoriteStartIndex 计算**。
