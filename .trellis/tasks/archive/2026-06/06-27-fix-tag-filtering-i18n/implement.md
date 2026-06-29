# Implement: Tag filtering + i18n

## Checklist

- [ ] **1. strings.xml**: 添加 string-array `provider_suggested_tags` 和 string `setting_provider_page_model_count_chat`
- [ ] **2. SettingProviderDetailPage.kt**: SUGGESTED_PROVIDER_TAGS 改为 stringArrayResource
- [ ] **3. SettingProviderPage.kt**: 模型计数改用 stringResource
- [ ] **4. ModelList.kt**: 添加标签筛选 FilterChip 行
- [ ] **5. Build + install**

## Validation

```bash
.\gradlew :app:compileDebugKotlin
.\gradlew :app:installDebug
```
