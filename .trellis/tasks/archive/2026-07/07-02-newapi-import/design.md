# Design: NewAPI channel JSON import

## Architecture

```
Import text (QR / image QR / clipboard)
        │
        ▼
decodeProviderImportText(text)  ──► ProviderSetting
        │
        ├─ prefix ai-provider:v1: ──► decodeProviderSetting (ShareSheet.kt, unchanged logic)
        │
        └─ JSON _type == newapi_channel_conn ──► parseNewApiChannelConn (importer)
                    │
                    ▼
            ProviderSetting.OpenAI (apiKey, baseUrl, default name placeholder)
                    │
                    ▼
        [NewAPI only] Name dialog ──► onAdd(setting.copy(name = userInput))
                    │
                    ▼
        SettingProviderPage: copyProvider(Uuid.random()) + prepend list
```

Cherry / Chatbox remain on `BackupVM` + `ImportExportTab`; they do not call this router.

## Format detection router

**Location:** `app/src/main/java/me/rerere/rikkahub/ui/components/ui/ShareSheet.kt` (alongside `decodeProviderSetting`) **or** dedicated `ProviderImportDecoder.kt` in `data/sync/importer/` if we want UI layer thin — prefer **`ProviderImportDecoder.kt`** in importer package + keep `decodeProviderSetting` as private v1 step to avoid growing ShareSheet UI file.

**API:**

```kotlin
fun decodeProviderImportText(raw: String): ProviderSetting
```

**Algorithm:**

1. `trimmed = raw.trim()`
2. If `trimmed.startsWith("ai-provider:v1:")` → `decodeProviderSetting(trimmed)` (existing).
3. Else `runCatching { JsonInstant.parseToJsonElement(trimmed) }` → on failure throw `IllegalArgumentException` with stable message key / English message for toast.
4. Require `jsonObject`; read `json["_type"]?.jsonPrimitive?.content == "newapi_channel_conn"`.
5. Delegate to `NewApiChannelImporter.parseChannelConn(json)` → `ProviderSetting.OpenAI`.

**Order:** Prefix check **before** JSON parse so v1 payloads that are not JSON still work.

## NewAPI parser

**File:** `app/src/main/java/me/rerere/rikkahub/data/sync/importer/NewApiChannelImporter.kt`

**Responsibilities:**

- `parseChannelConn(element: JsonElement): ProviderSetting.OpenAI`
- Validate `_type`, non-blank `key`, non-blank `url`
- `baseUrl`: reuse normalization pattern from `CherryStudioProviderImporter.normalizeBaseUrl` — extract shared helper to `importer/ImportUrlUtils.kt` **or** duplicate minimal trim/slash logic if avoiding refactor scope (PRD allows either; prefer small shared `normalizeGatewayBaseUrl(url: String)` in same file as importer).

**Defaults for new OpenAI provider:**

- `name = "NewAPI"` (overwritten by UI dialog before persist)
- `models = emptyList()`
- `id` = random at save time via existing `copyProvider(Uuid.random())` in page (parser may use default `Uuid.random()`; page always replaces id on add)

## UI: clipboard + name flow

**File:** `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`

### Import dialog

Add third `OutlinedButton` (or equivalent): “Paste from clipboard”.

- Use `ContextUtil` clipboard read (see `utils/ContextUtil.kt` — mirror theme export copy pattern in reverse).
- Empty clipboard → error toast.
- On success: `decodeProviderImportText(text)` inside `runCatching`.

### Name dialog (NewAPI only)

**Detection:** After decode, if import was NewAPI-shaped input, show name dialog. Options:

- **A (recommended):** Router returns `sealed class ProviderImportResult { data class Complete(ProviderSetting); data class NeedsName(ProviderSetting) }` where v1 decode → `Complete`, NewAPI → `NeedsName`.
- **B:** Flag on `ProviderSetting` — avoid.

Flow for `NeedsName`:

- `AlertDialog` + `OutlinedTextField`, default value = parsed placeholder name or host from `url`.
- Confirm → `onAdd(setting.copy(name = input))`, success toast, close import dialog.
- Cancel → no add.

### QR handlers

Replace direct `decodeProviderSetting(...)` in:

- `handleQRResult` (`QRSuccess` branch)
- `handleImageQRCode`

With shared `handleImportText(raw, onAdd, toaster, context, onNeedsName: (ProviderSetting, () -> Unit) -> Unit)` or inline `when (decodeProviderImportText(...))` equivalent.

v1 imports: call `onAdd` immediately (current behavior).

## Files to touch

| File | Change |
|------|--------|
| `data/sync/importer/NewApiChannelImporter.kt` | **New** — parse NewAPI JSON |
| `data/sync/importer/ProviderImportDecoder.kt` | **New** — `decodeProviderImportText` |
| `ui/components/ui/ShareSheet.kt` | `decodeProviderSetting` stays; optionally internal visibility only |
| `ui/pages/setting/SettingProviderPage.kt` | Clipboard button, name dialog, handler refactor |
| `utils/ContextUtil.kt` | Optional `readClipboardText(): String?` if not present |
| `app/src/main/res/values/strings.xml` (+ zh if required by repo habit) | Paste button, name dialog title, NewAPI-specific errors |
| `app/src/test/.../NewApiChannelImporterTest.kt` | **New** |
| `app/src/test/.../ProviderImportDecoderTest.kt` or extend `ShareSheetTest.kt` | Router + regression v1 |

## Error handling

- All import surfaces: `runCatching { ... }.onFailure { toaster.show(..., Error) }`.
- Do not log API keys.
- Messages: reuse `setting_provider_page_qr_decode_failed` with `%s` = exception message, or add `setting_provider_page_import_invalid_format` for generic invalid payload.

## Compatibility

- `JsonInstant` already `ignoreUnknownKeys = true` for any future NewAPI fields.
- QR codes containing raw JSON (not prefixed) become supported without breaking v1 QR.

## Testing strategy

- Unit: valid/invalid NewAPI JSON; v1 string still routes correctly; wrong `_type`; blank key.
- Unit: `ShareSheetTest` unchanged expectations for `decodeProviderSetting` direct calls.
- Manual: clipboard + QR with sample NewAPI JSON; confirm Cherry backup import still works on backup tab.