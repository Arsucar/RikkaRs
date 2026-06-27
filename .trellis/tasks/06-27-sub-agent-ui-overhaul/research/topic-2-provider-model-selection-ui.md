# Research: Provider list UI and model selection

- **Query**: Model selection UI, provider list presentation, data models, settings flow, provider management
- **Scope**: internal (`app/` UI + `ai/` provider types + `Settings` persistence)
- **Date**: 2026-06-27

## Findings

### 1. Model selection UI (where users pick models)

| Entry point | File | Usage |
|-------------|------|--------|
| **Primary picker** | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt` | `ModelSelector` (L162–242) opens `ModelListSheet` → bottom sheet with `ModelList` (L244–664) |
| Chat input | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` ~L242 | `ModelSelector` for conversation model |
| Assistant default model | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantBasicPage.kt` ~L245 | `ModelSelector` |
| Subagent profile model | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` ~L242 | `ModelSelector` with `allowClear` pattern |
| Translator / ImgGen / connection test | `TranslatorPage.kt`, `ImgGenPage.kt`, `ProviderConnectionTester.kt` | Same component |
| **Global default models** | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt` | Uses `rememberModelListState` + `ModelListSheet` for `chatModelId`, `fastModelId`, title/suggestion/OCR/compress models, etc. (pager tab “Model”) |

**`ModelListState`** (L96–138): holds `modelId`, `providers`, `ModelType` filter, `visible`; `filteredProviders` = enabled providers with at least one model of requested type (L116–119).

**Sheet UX** (`ModelList` L293–664):

- Search by `displayName` (L313–327, L427–447).
- **Favorites** section: `Settings.favoriteModels` ordered list, drag-reorder (L469–535).
- **Per-provider sticky headers** with provider name + `ProviderBalanceText` (L538–559).
- Model rows via `ModelItem` (L667–752).
- Horizontal **provider chips** (`AssistChip`) scroll-synced with list (L616–662).
- Long-press model row → navigate to `Screen.SettingProviderDetail` (L698–704).

**`ModelSelector` collapsed UI** (L178–201): `AutoAIIcon` + `displayName` or `R.string.model_list_select_model`; optional clear (`allowClear`).

### 2. What the provider list shows (settings list page)

**Page:** `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`

- Top bar: title, back, actions — recommend (`RecommendProviderButton` L236+), import QR/image (`ImportProviderButton` L331+), manual add (`AddButton`).
- Search filters by `provider.name` (L107–115, L162–181).
- Reorderable `LazyColumn` of providers (L100–105, L194–228).

**`ProviderItem`** (L589–655) displays:

| Element | Lines | Content |
|---------|-------|---------|
| Icon | L611–614 | `AutoAIIcon(provider.name, 40.dp)` |
| Title | L619–624 | `provider.name` |
| Subtitle | L625–628 | `provider.shortDescription()` composable |
| Tags | L630–649 | Enabled/disabled tag; model count `R.string.setting_provider_page_model_count`; hardcoded “10% 优惠” for name `"AiHubMix"` |
| Drag handle | L652 | Reorder |

**Recommend sheet items** (`RecommendProviderItem` L287–327): icon, name, full `provider.description()`, add button; data from `RECOMMENDED_PROVIDERS` (`data/datastore/RecommendedProviders.kt`).

**Not shown on list row:** API URL, API key, individual model names, latency, or connection status (those live on detail page).

### 3. Data layer: providers and models

**Global settings** — `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`:

```kotlin
// L532–560 (excerpt)
data class Settings(
    ...
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    ...
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    ...
)
```

**Provider aggregate** — `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` L26–51:

- Sealed class variants: `OpenAI`, `Google`, … (each `@Serializable` with `SerialName`).
- Common fields: `id`, `enabled`, `name`, `models: List<Model>`, `balanceOption`, `builtIn`, `description` / `shortDescription` (@Composable, `@Transient`).
- CRUD helpers: `addModel`, `editModel`, `delModel`, `moveMove`, `copyProvider`.

**Model entity** — `ai/src/main/java/me/rerere/ai/provider/Model.kt` L8–20:

- `modelId`, `displayName`, `id: Uuid`, `type: ModelType` (CHAT/IMAGE/EMBEDDING), modalities, `abilities` (TOOL, REASONING), `tools: Set<BuiltInTools>`, optional `providerOverwrite`.

**Resolution helpers** — `PreferencesStore.kt` / datastore extensions:

- `Model.findProvider(providers, checkOverwrite)` (grep L768).
- `providers.findModelById` used throughout UI (`ModelList.kt` L307, L114).

**Defaults:** `DEFAULT_PROVIDERS` in `app/.../data/datastore/DefaultProviders.kt`; recommended templates in `RecommendedProviders.kt`.

### 4. Settings / configuration flow

```
SettingPage.kt (L211–217, L409)
  ├─ Screen.SettingModels → SettingModelPage (global role models + prompts tab)
  └─ Screen.SettingProvider → SettingProviderPage (provider list)
        └─ tap row → Screen.SettingProviderDetail(providerId)
              └─ SettingProviderDetailPage.kt
```

**`SettingProviderDetailPage.kt`** (L138+):

- Pager/tabs: config vs models (`SettingProviderConfigPage` L259+, `SettingProviderModelPage` L373+).
- Config: `ProviderConfigure` components, balance (`SettingProviderBalanceOption`), connection test, share, enable switch, type-specific fields (API key, base URL, …).
- Models: list/add/edit/delete/reorder models, registry fetch, tags (`ModelTypeTag`, `ModelModalityTag`, `ModelAbilityTag`), `ModelSelector` for test chat.

**Persistence:** `SettingVM` → `SettingsStore.update { copy(providers = ...) }` (pattern in `SettingProviderPage` L101–104, add/import L128–146).

**Import paths:** QR / image decode → `decodeProviderSetting`; Cherry Studio importer exists at `data/sync/importer/CherryStudioProviderImporter.kt`.

### 5. What each model row shows (selection list)

**`ModelItem`** (`ModelList.kt` L667–752):

| UI | Details |
|----|---------|
| Icon | `AutoAIIcon(model.modelId)` in `secondaryContainer` surface (L713–722) |
| Primary text | `model.displayName` (L728–733) |
| Tags | `ModelTypeTag` (CHAT/EMBEDDING/IMAGE) L741, L755–769 |
| | `ModelModalityTag` — input/output modality icons (L772–807) |
| | `ModelAbilityTag` — TOOL (wrench), REASONING (deepthink drawable) L809–837 |
| Selection | `primaryContainer` when `select == true` (L681–684) |
| Tail | Favorite heart toggle (L576–609) |
| Provider context | Passed as `providerSetting` for balance in header, not repeated on every row |

**Provider header in model sheet** (L538–559): name + `ProviderBalanceText` (optional balance fetch when `balanceOption.enabled`).

### 6. Existing provider management UI summary

| Surface | Purpose |
|---------|---------|
| `SettingProviderPage` | List, search, reorder, add recommended/custom, import |
| `SettingProviderDetailPage` | Full provider config + model CRUD + connection test |
| `components/ProviderConfigure.kt` | Type-specific configuration forms |
| `components/ProviderConnectionTester.kt` | Test generation with `ModelSelector` |
| `components/ASRProviderConfigure.kt` / `TTSProviderConfigure.kt` | Speech providers (separate from LLM `providers`) |
| `ProviderBalanceText.kt` | Async balance display in model list headers |

### Code patterns (citations)

```589:644:app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt
private fun ProviderItem(...) {
    ...
    Text(text = provider.name, ...)
    provider.shortDescription()
    Tag { enabled/disabled }
    Tag { model count }
}
```

```116:119:app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt
    val filteredProviders: List<ProviderSetting>
        get() = providers.fastFilter { provider ->
            provider.enabled && provider.models.fastAny { model -> model.type == type }
        }
```

```8:20:ai/src/main/java/me/rerere/ai/provider/Model.kt
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    ...
)
```

### Navigation routes

`RouteActivity.kt` L438–448, L670–676: `Screen.SettingProvider`, `Screen.SettingProviderDetail(providerId)`, `Screen.SettingModels`.

## Caveats / Not Found

- Provider list UI does **not** surface per-provider model previews (only count tag).
- `description` / `shortDescription` on `ProviderSetting` are Compose lambdas (`@Transient`)—not serialized; built-in defaults set in `DefaultProviders.kt`.
- TTS/ASR providers are parallel settings lists, not mixed into `ModelList` provider sheet.
- Tests: `ProviderConfigureConvertToTest.kt` in `app/src/test/.../setting/components/` for config conversion only.