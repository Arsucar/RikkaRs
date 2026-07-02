# Research: ProviderSetting data model (OpenAI fields)

- **Query**: `ProviderSetting` structure, especially `OpenAI` (`apiKey`, `baseUrl`, etc.)
- **Scope**: internal
- **Date**: 2026-07-02

## Findings

### Location

Primary definition: `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`  
(App `data/model/` does **not** define `ProviderSetting`; app imports from `ai` module.)

### Sealed hierarchy

`ProviderSetting` is `@Serializable sealed class` with variants:

| `@SerialName` | Kotlin type | Type-specific fields (high level) |
|---------------|-------------|-----------------------------------|
| `openai` | `OpenAI` | `apiKey`, `baseUrl`, `chatCompletionsPath`, `useResponseApi`, `includeHistoryReasoning` |
| `google` | `Google` | `apiKey`, `baseUrl`, `vertexAI`, service account fields, etc. |
| `claude` | `Claude` | `apiKey`, `baseUrl`, `promptCaching`, `promptCacheTtl` |

Shared on all variants: `id`, `enabled`, `name`, `models`, `balanceOption`, `tags`; `builtIn` / `description` / `shortDescription` are `@Transient` (not in JSON).

### `ProviderSetting.OpenAI` (lines 54–117)

```kotlin
@SerialName("openai")
data class OpenAI(
    override var id: Uuid = Uuid.random(),
    override var enabled: Boolean = true,
    override var name: String = "OpenAI",
    override var models: List<Model> = emptyList(),
    override val balanceOption: BalanceOption = BalanceOption(),
    override val tags: List<String> = emptyList(),
    var apiKey: String = "",
    var baseUrl: String = "https://api.openai.com/v1",
    var chatCompletionsPath: String = "/chat/completions",
    var useResponseApi: Boolean = false,
    var includeHistoryReasoning: Boolean = true,
) : ProviderSetting()
```

`BalanceOption`: `enabled`, `apiPath` (default `/credits`), `resultPath` (default `data.total_usage`).

### Persistence

`PreferencesStore.kt` stores `Settings.providers: List<ProviderSetting>` (default `DEFAULT_PROVIDERS`).

### JSON codec

`JsonInstant` (`app/.../utils/Json.kt`): `ignoreUnknownKeys = true`, `encodeDefaults = true` — used for share encode/decode and Cherry Studio nested JSON parsing.

## Caveats / Not Found

- No `NewAPI`/`OneAPI`-specific fields on `ProviderSetting`; channel gateways are expected to map into `OpenAI`-compatible `baseUrl` + `apiKey` (typical for OpenAI-compatible proxies).