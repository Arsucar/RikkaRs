# Design: Tag filtering + i18n

## Changes

### D1: ModelList 标签筛选

**文件**: `ModelList.kt`
- 在 `ModelList` composable 中添加 `var selectedModelListTag by remember { mutableStateOf<String?>(null) }`
- 在搜索栏下方、provider badge 行上方，添加 `LazyRow` 包含 FilterChip：
  - "全部" chip（`selectedModelListTag == null`）
  - 各 tag chip（从 `providers.flatMap { it.tags }.distinct()` 计算）
- 过滤逻辑：在 `searchFilteredModelsByProvider` 计算前，先按 tag 过滤 providers
  - 当 `selectedModelListTag != null`，过滤掉 `!provider.tags.contains(selectedModelListTag)` 的 provider
  - 同时影响 `providerPositions` 和 `selectedModelPosition` 的计算（被过滤的 provider 不参与）

**实现要点**: 不修改 `providers` 入参本身，而是引入 `tagFilteredProviders` 中间变量，后续所有计算用它。

### D2: SUGGESTED_PROVIDER_TAGS 国际化

**文件**: `SettingProviderDetailPage.kt` + `strings.xml`
- 将 `private val SUGGESTED_PROVIDER_TAGS = listOf("常用", "便宜", ...)` 改为 Composable 函数返回 List<String>
- 在 strings.xml 添加 `<string-array name="provider_suggested_tags">` 包含建议标签
- 用 `stringArrayResource(R.array.provider_suggested_tags).toList()` 读取
- 注意：这只是 UI 建议文案，用户实际添加的 tag 值不受 i18n 影响

### D3: 模型计数 i18n

**文件**: `SettingProviderPage.kt` + `strings.xml`
- 将 `"$chatCount/${provider.models.size}"` 替换为 `stringResource(R.string.setting_provider_page_model_count_chat, chatCount, provider.models.size)`
- 在 strings.xml 添加：`<string name="setting_provider_page_model_count_chat">%1$d/%2$d</string>`

## Tradeoffs

- D1 引入 `tagFilteredProviders` 会增加一次 `remember` 计算，但开销可忽略
- D2 用 string-array 方案对 i18n 友好，但用户已添加的标签不会翻译（预期行为）
