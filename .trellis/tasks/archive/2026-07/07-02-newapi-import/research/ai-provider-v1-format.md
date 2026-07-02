# Research: `ai-provider:v1:` share/import format

- **Query**: Current provider share string format mentioned in issue
- **Scope**: internal
- **Date**: 2026-07-02

## Findings

### Implementation

File: `app/src/main/java/me/rerere/rikkahub/ui/components/ui/ShareSheet.kt`

**Encode** (`ProviderSetting.encodeForShare()`):

```
ai-provider:v1:<Base64(UTF-8 JSON)>
```

- JSON is `JsonInstant.encodeToString(provider.copyProvider(models = emptyList()))`
- Polymorphic type discriminator comes from kotlinx.serialization `@SerialName` on sealed subclass (`openai`, `google`, `claude`)

**Decode** (`decodeProviderSetting(value: String)`):

1. `require(value.startsWith("ai-provider:v1:"))` else `IllegalArgumentException("Invalid provider setting string")`
2. Strip prefix, Base64-decode, UTF-8 string
3. `JsonInstant.decodeFromString<ProviderSetting>(jsonStr)`

### Versioning

Only **`v1`** is accepted. `ai-provider:v2:...` throws (covered by `ShareSheetTest`).

### Consumers

| Consumer | Usage |
|----------|-------|
| QR scan success | `SettingProviderPage.handleQRResult` L514 |
| Gallery QR | `handleImageQRCode` L550 |
| QR display / share intent | `ShareSheet` L66–80 |

### Distinction from file import JSON

- `ai-provider:v1:` is a **wrapped, typed Rikkahub `ProviderSetting`** payload.
- Cherry/Chatbox files are **third-party backup schemas**, parsed manually into `ProviderSetting`.
- Raw NewAPI/OneAPI channel JSON would be a **fourth shape** unless converted to `ProviderSetting` or wrapped into `ai-provider:v1:` externally.

## Code patterns

```91:111:app/src/main/java/me/rerere/rikkahub/ui/components/ui/ShareSheet.kt
fun ProviderSetting.encodeForShare(): String {
    return buildString {
        append("ai-provider:")
        append("v1:")
        val value = JsonInstant.encodeToString(this@encodeForShare.copyProvider(models = emptyList()))
        append(Base64.encode(value.encodeToByteArray()))
    }
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }
    val base64Str = value.removePrefix("ai-provider:v1:")
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()
    return JsonInstant.decodeFromString<ProviderSetting>(jsonStr)
}
```

## Caveats / Not Found

- No multi-format dispatcher (e.g. try `ai-provider:v1:` then try NewAPI JSON) in codebase yet.
- Clipboard path does not call `decodeProviderSetting` anywhere outside QR handlers.