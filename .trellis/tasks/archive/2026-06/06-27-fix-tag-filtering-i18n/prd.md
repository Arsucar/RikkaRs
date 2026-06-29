# Fix tag filtering in ModelList + i18n

## Parent

`06-27-fix-modellist-subagent-prod`

## Goal

在 ModelList sheet 中添加标签筛选，并修复 i18n 硬编码。

## Requirements

### R1: ModelList 标签筛选
- 在 `ModelList` composable 中添加 `selectedModelListTag` state（`String?`）
- 在搜索栏下方、provider badge 行上方，添加 `LazyRow` 包含 `FilterChip`（全部 + 各 tag）
- 过滤逻辑：在 `searchFilteredModelsByProvider` 计算时，如果 `selectedModelListTag != null`，只保留 `provider.tags.contains(selectedModelListTag)` 的 provider
- 当 provider 因 tag 被过滤掉时，其下所有模型也不显示

### R2: SUGGESTED_PROVIDER_TAGS 国际化
- 将 `SettingProviderDetailPage.kt` 中 `SUGGESTED_PROVIDER_TAGS` 从 `listOf("常用", "便宜", ...)` 改为从 strings.xml 读取
- 方案：用 `stringArrayResource` 或逗号分隔的单一 string resource
- 注意：建议标签只是 UI 提示，用户添加的标签值本身不受 i18n 影响（用户自由输入）

### R3: 模型计数 i18n 修复
- `SettingProviderPage.kt` 中 `"$chatCount/${provider.models.size}"` 改回 `stringResource`：
- 新增 string key `setting_provider_page_model_count_chat` 格式：`%1$d/%2$d`
- 或复用现有 key 并扩展参数

## Acceptance Criteria

- [ ] AC1: ModelList sheet 中有标签筛选 FilterChip 行
- [ ] AC2: 选中标签后只显示匹配的 provider 及其模型
- [ ] AC3: "全部" chip 清除筛选
- [ ] AC4: SUGGESTED_PROVIDER_TAGS 文案走 stringResource
- [ ] AC5: 模型计数使用 stringResource
