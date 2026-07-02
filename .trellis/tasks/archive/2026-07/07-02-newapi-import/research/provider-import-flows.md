# Research: Provider import and share flows

- **Query**: How provider import currently works (QR, clipboard, file importers)
- **Scope**: internal
- **Date**: 2026-07-02

## Findings

### In-app import (settings → provider list)

| Entry | File | Behavior |
|-------|------|----------|
| QR scan | `app/.../setting/SettingProviderPage.kt` | `ImportProviderButton` → `ScanQRCode()` → `handleQRResult` → `decodeProviderSetting(rawValue)` → `onAdd` |
| QR from gallery image | same | `PickVisualMedia` → `ImageUtils.decodeQRCodeFromUri` → `decodeProviderSetting` → `onAdd` |
| Manual add | same | `AddButton` opens dialog with default `ProviderSetting.OpenAI()` |
| Recommended presets | same | `RecommendProviderButton` adds from `RECOMMENDED_PROVIDERS` with new `Uuid` |

After import/add, `SettingProviderPage` prepends provider via `vm.updateSettings(settings.copy(providers = listOf(it.copyProvider(Uuid.random())) + ...))` so a **new id** is assigned on import.

**No clipboard-paste import** on `SettingProviderPage` today. Import dialog only offers camera QR and gallery QR image.

### Share / export (inverse of import)

| Entry | File | Behavior |
|-------|------|----------|
| Share QR / text | `SettingProviderDetailPage.kt` + `ShareSheet.kt` | `encodeForShare()` → prefix `ai-provider:v1:` + Base64(JSON) |

### Backup / file importers (not QR)

| Importer | File | Trigger UI |
|----------|------|------------|
| Cherry Studio | `data/sync/importer/CherryStudioProviderImporter.kt` | `BackupVM.restoreFromCherryStudio` ← `ImportExportTab` |
| Chatbox | `data/sync/importer/ChatboxImporter.kt` | `BackupVM.restoreFromChatBox` / streaming import |

Cherry Studio: ZIP `data.json` → nested `persist:cherry-studio` → `llm.providers[]`.

Chatbox: JSON file → `settings.providers.{openai,claude,gemini}`.

### Format detection today

Provider settings import from QR/gallery uses **only** `decodeProviderSetting`:

1. String must start with `ai-provider:v1:`
2. Remainder is Base64 → UTF-8 JSON
3. `JsonInstant.decodeFromString<ProviderSetting>(jsonStr)`

There is **no** branch for raw JSON objects, NewAPI/OneAPI channel exports, or other prefixes in this path.

File importers use **dedicated parsers** (Cherry/Chatbox shapes), not `decodeProviderSetting`.

### Related tests

- `app/src/test/java/me/rerere/rikkahub/ShareSheetTest.kt` — round-trip and invalid prefix/version/base64 for `ai-provider:v1:`

## Caveats / Not Found

- **NewAPI / OneAPI**: zero code references in repo; no existing importer.
- **Clipboard paste** for provider JSON: not implemented on provider settings page (clipboard utilities exist in `utils/ContextUtil.kt` for generic text only).
- `encodeForShare()` serializes provider with `copyProvider(models = emptyList())` — shared payloads omit model list by design (`ShareSheet.kt` L96).