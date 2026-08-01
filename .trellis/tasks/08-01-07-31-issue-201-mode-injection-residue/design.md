# 技术设计：清理模式注入直接绑定残留

## 现状

- `collectInjections`（`PromptInjectionTransformer.kt`）默认路径（`allowConversationPromptInjection=false`）用 `assistant.modeInjectionIds`（:95-99）并入 `allModeInjectionIds`（:115-125）后按 `enabled` 注入。
- #73 `withDefaultPreset`（`PreferencesStore.kt:1465-1478`）：全量 mode injection id → Default Preset 的 `modeInjectionIds`。
- #182 `migratedWithEntries`（`Assistant.kt:291-328`）：`Preset.modeInjectionIds` → `PresetEntry.Custom(id = injection.id, content = 快照)` 且清空 Preset 旧字段；**从不清理 `Assistant.modeInjectionIds`**。
- 关键事实：迁移 entries 的 `id` 与原全局 mode injection id **相同**（`PresetEntry.Custom(id = injection.id)`），故「直连 id ∈ 已绑定 preset 的 entry ids」即双路径。

## 方案

### A. 迁移去重（PreferencesStore.kt settings flow 清理段）

在现有 assistant 过滤段（:461-484）内，对每个 assistant 先计算其绑定 preset 的 entry id 集合，再清理直连：

```kotlin
val boundPresetEntryIds = assistant.presetIds
    .flatMap { presetId -> presets.firstOrNull { it.id == presetId }?.entries?.map { it.id }.orEmpty() }
    .toSet()
modeInjectionIds = assistant.modeInjectionIds
    .filter { it !in boundPresetEntryIds }
    .filter { it in validModeInjectionIds }
    .toSet(),
```

- 只去掉与 preset entries 重复的直连；与预设无关的独立注入保留（可由新 UI 关闭）。
- 顺序：在 `migratedPresets` 计算之后、assistant 过滤段内执行，确保用迁移后的 entries。
- 幂等：filter 天然幂等。

### B. UI 独立注入 tab（ExtensionSelector.kt）

1. `rememberPagerState { 4 }` → `{ 5 }`（:90）。
2. `SecondaryScrollableTabRow` 加第 5 个 Tab，文案 `R.string.extension_selector_tab_mode_injections`（:101-128 模式）。
3. `HorizontalPager` `when(page)` 加 `4 ->` 分支（:137）。
4. 分支内容复用 `ModeInjectionsContent`（`ExtensionContent.kt:98-133`）：
   - `modeInjections = settings.modeInjections`
   - `selectedIds = if (useConversationInjections) conversation.modeInjectionIds else assistant.modeInjectionIds`
   - `onToggle = { id, checked -> ... }` 写 assistant（`onUpdate(assistant.copy(modeInjectionIds = ...))`）或 conversation（`onUpdateConversation(conversation.copy(modeInjectionIds = ...))`），与 lorebook tab（:196-222）同模式。
   - `onManage`/`onEdit` 参照 `AssistantExtensionsPage.kt:229-246`（可给 onEdit 进编辑弹窗，onManage = onNavigateToPrompts）。
5. `useConversationInjections` 已在组件内定义（:82-83），可直接复用。

### C. Transformer 不改

`PromptInjectionTransformer.collectInjections` 直连路径是合法独立注入功能；关闭预设等三项本就只停对应源。残留问题由 A（历史重复清理）+ B（可见开关）解决。

## 边界与取舍

- 不新增 `allowConversationPromptInjection` 判定逻辑；沿用现有 transformer 二选一语义（:95-106）。
- 迁移清理只动 assistant 直连；conversation 级绑定是用户主动选择，不自动清理。
- 与 #200 无文件交集；与 #199 无交集，可完全并行。

## 风险

- `presets.firstOrNull` 在清理段前已 distinctBy（:443）；boundPresetEntryIds 计算应在 migratedPresets 之后。
- 若某直连 id 的全局内容已被 #182 快照进 Default Preset，但 assistant 未绑定该 preset → 不清理（保留），仍可能注入；由 B 的可见开关兜底。符合 issue「清理/迁移 + 可见开关」双重期望。
