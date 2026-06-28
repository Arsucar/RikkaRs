# Research: Provider list UI — code reference

- **Query**: Implementation details for provider list UI optimization (ModelList, ModelSelector, SettingProviderPage, ProviderSetting, navigation)
- **Scope**: internal
- **Date**: 2026-06-27

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` | Model picker sheet, list, provider chips, model rows |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ProviderBalanceText.kt` | Balance label in provider sticky headers |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt` | Settings provider list, search, reorder |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt` | Opens `ModelListSheet` via `rememberModelListState` |
| `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` | Serializable provider model (sealed hierarchy) |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `Screen` routes and `entry<>` composition |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ui/Tag.kt` | `Tag` / `TagType` used on model and provider rows |

---

## 1. ModelList.kt — sheet, list, provider chips

**Path**: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`

### State and entry points

| Symbol | Lines | Role |
|---|---|---|
| `ModelListState` | 96–138 | Holds `modelId`, `providers`, `type`, `visible`; `filteredProviders` = enabled providers with ≥1 model of `type`; `open()` / `close()` |
| `rememberModelListState` | 140–159 | Creates state and syncs via `update()` |
| `ModelSelector` | 162–242 | Trigger UI + always composes `ModelListSheet` |
| `ModelListSheet` | 245–291 | `ModalBottomSheet` wrapper |
| `ModelList` (private) | 293–664 | Search, `LazyColumn`, favorites, per-provider sections, bottom `LazyRow` chips |
| `ModelItem` | 666–753 | Single model row; long-press → provider detail |
| `ModelTypeTag` / `ModelModalityTag` / `ModelAbilityTag` | 755–838 | Model metadata chips |

### How the sheet is opened

1. `ModelSelector` calls `state.open()` from `TextButton` (L182–185) or `IconButton` (L217–220).
2. `ModelListSheet` returns early when `!state.visible` (L249).
3. On dismiss: `sheetState.hide()` then `state.close()` (L257–261); `onDismissRequest` also sets `state.close()` (L265–267).

### Sheet structure (`ModelListSheet`, L245–291)

```kotlin
ModalBottomSheet(onDismissRequest = { state.close() }, sheetState = sheetState) {
    Column(
        modifier = Modifier.padding(8.dp).fillMaxHeight(0.8f).imePadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModelList(...)
    }
}
```

- **Height**: `fillMaxHeight(0.8f)` — ~80% of screen, not a separate “half sheet” API.
- **`rememberBottomSheetState`**: `initialValue = SheetValue.Hidden`, `enabledValues = { Hidden, Expanded }` only (L252–255). No `PartiallyExpanded` in this sheet.

### `ModelList` layout (top → bottom)

1. **Search** (L421–448): `Surface` + `OutlinedTextField`, filters `displayName` per provider (L321–327).
2. **`LazyColumn`** (L450–614, `Modifier.weight(1f)`):
   - Empty providers message (L458–467)
   - **Favorites** sticky header + reorderable `ModelItem` rows (L469–536)
   - **Per provider**: `stickyHeader` with name + `ProviderBalanceText` (L538–560), then model `items` (L562–612)
3. **Provider badge row** (L616–663): horizontal `LazyRow` of `AssistChip` at bottom of column (below list, not above).

### Provider horizontal chips (L616–662)

- Comment: `// 供应商Badge行`
- `providerPositions` map (L403–419): lazy list index per provider header for scroll sync.
- `LaunchedEffect(lazyListState)` (L618–637): debounced `firstVisibleItemIndex` → `providerBadgeListState.animateScrollToItem(index)`.
- `LazyRow` (L640–662): `items(providers)` → `AssistChip` with `onClick` → `lazyListState.animateScrollToItem(position)`; `leadingIcon` = `AutoAIIcon(name = provider.name, 16.dp)`.

### Interaction with model list

- Chips scroll the main `LazyColumn` to provider sticky headers; list scroll drives chip row via `providerPositions`.
- Model selection: `onSelect` in sheet calls user callback then `dismiss()` (L281–284).
- `ModelItem` long-click: dismiss sheet + `navController.navigate(Screen.SettingProviderDetail(providerSetting.id.toString()))` (L698–704).

### Other `ModelListSheet` call sites

- `SettingModelPage.kt`: `ModelSettingItem` / `SuggestionModelSettingItem` use `state.open()` on row click and compose `ModelListSheet` alongside (L179–242, L254–301).

---

## 2. ModelSelector (L162–242)

**Parameters**: `modelId`, `providers`, `type`, `modifier`, `onlyIcon`, `allowClear`, `onSelect`.

| Mode | UI | Open sheet |
|---|---|---|
| `onlyIcon == false` | `Row`: `TextButton` (icon + `displayName` or placeholder string) + optional clear `IconButton` | L182–185 |
| `onlyIcon == true` | `IconButton` with model `AutoAIIcon` or `Brain02` | L217–220 |

Always renders `ModelListSheet(state, onSelect)` at end (L238–241) — sheet is sibling composable, not navigation route.

**Example — chat** (`ChatInput.kt` L242–251): `onlyIcon = true`, `type = ModelType.CHAT`, `providers = settings.providers`.

---

## 3. SettingProviderPage.kt

**Path**: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`

| Section | Lines | Structure |
|---|---|---|
| `SettingProviderPage` | 93–233 | `Scaffold` + `LargeFlexibleTopAppBar`; actions: recommend / import / add |
| Search | 161–181 | `OutlinedTextField`, filters `provider.name` (L107–115) |
| List + reorder | 184–230 | `LazyColumn` + `rememberReorderableLazyListState`; reorder updates `settings.providers` order via `vm.updateSettings` (L100–105) |
| `ProviderItem` | 588–655 | Card row: icon, name, `shortDescription()`, `FlowRow` of `Tag`s, drag handle |

### `ProviderItem` tags (L630–650)

- Enabled/disabled `Tag`
- Model count `Tag` (`provider.models.size`)
- Hardcoded promo for `provider.name == "AiHubMix"` — not data-driven from `ProviderSetting`

### Navigation from list

- Row click: `Screen.SettingProviderDetail(provider.id.toString())` (L224–226)

---

## 4. ProviderSetting.kt

**Path**: `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`

### Shared abstract API (L25–50)

| Field / method | Notes |
|---|---|
| `id: Uuid` | |
| `enabled: Boolean` | |
| `name: String` | |
| `models: List<Model>` | |
| `balanceOption: BalanceOption` | L9–14: `enabled`, `apiPath`, `resultPath` |
| `builtIn: Boolean` | `@Transient` on subclasses |
| `description` / `shortDescription` | `@Composable () -> Unit`, `@Transient` |
| `addModel` / `editModel` / `delModel` / `moveMove` | |
| `copyProvider(...)` | Full copy with optional overrides |

### Concrete types (serializable)

- `OpenAI` (L52–112): `apiKey`, `baseUrl`, `chatCompletionsPath`, `useResponseApi`, `includeHistoryReasoning`
- `Google` (L114–177): Vertex / service account fields
- `Claude` (L179–238): `promptCaching`, `promptCacheTtl`

**Tags on provider**: No `tags` (or similar) field exists on `ProviderSetting` or `BalanceOption` today. UI tags on settings list are composed in `ProviderItem` (status, count, one-off name check). Model-level tags use `Tag` in `ModelItem` (`ModelTypeTag`, etc.).

Adding provider tags would require new `@Serializable` fields on the sealed types (and `copyProvider` / persistence), unless tags are derived only in UI from existing fields.

---

## 5. Navigation routes (provider / model selection)

**Path**: `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`

| Screen | Lines | Composition |
|---|---|---|
| `Screen.SettingProvider` | 670, 438–440 | `SettingProviderPage()` |
| `Screen.SettingProviderDetail` | 673, 442–445 | `SettingProviderDetailPage(id = Uuid.parse(key.providerId))` |
| `Screen.SettingModels` | 676, 447–449 | `SettingModelPage()` (uses `ModelListSheet`, not a dedicated route for picker) |

**Entry to provider settings**: `SettingPage.kt` L217 — `navController.navigate(Screen.SettingProvider)`.

**Model picker**: Not a `Screen`; embedded `ModalBottomSheet` from `ModelList.kt` wherever `ModelSelector` or `ModelListSheet` is composed.

**Cross-link**: From model sheet, `Screen.SettingProviderDetail` (see `ModelItem` L700–703).

---

## Code patterns summary (modification anchors)

| Goal | Primary locations |
|---|---|
| Provider tab/chip UI in model picker | `ModelList.kt` L616–662 (`LazyRow` / `AssistChip`); scroll sync L403–419, L618–637 |
| Sheet height / expansion | `ModelListSheet` L270–274 (`fillMaxHeight(0.8f)`), L252–255 (`SheetValue`) |
| Provider list in settings | `SettingProviderPage.kt` L107–115 search, L194–228 list, `ProviderItem` L588–655 |
| Provider row tags | `ProviderItem` `FlowRow` L630–650; compare `ModelItem` `FlowRow` L735–746 |
| Provider data shape | `ProviderSetting.kt` L25–50 abstract + per-type `data class`; `copyProvider` must include new fields |
| Open picker from settings models | `SettingModelPage.kt` `rememberModelListState` + `state.open()` + `ModelListSheet` |

---

## Related specs

- (none under `.trellis/spec/` specifically for this UI; task dir: `.trellis/tasks/06-27-provider-list-ui-opt/`)

## Caveats / Not Found

- No `SheetValue.PartiallyExpanded` or explicit “half height” flag for model picker; only `0.8f` max height on inner `Column`.
- No first-class **provider tags** in `ProviderSetting`; only UI-composed tags and model-level tags.
- `ProviderBalanceText` only renders for `ProviderSetting.OpenAI` with `balanceOption.enabled` (`ProviderBalanceText.kt` L38–40).
- Grep did not find a separate `ModelListSheet` configuration API beyond `ModelListState.visible` and sheet composable parameters.