# Research: Where providers are created in the UI

- **Query**: Settings pages and dialogs for adding/importing providers
- **Scope**: internal
- **Date**: 2026-07-02

## Findings

### Navigation

```
SettingPage.kt
  └─ Screen.SettingProvider → SettingProviderPage.kt
        └─ Screen.SettingProviderDetail(providerId) → SettingProviderDetailPage.kt
```

### `SettingProviderPage.kt` top-bar actions (L129–150)

1. **Recommend** (`RecommendProviderButton`) — bottom sheet, `RECOMMENDED_PROVIDERS`
2. **Import** (`ImportProviderButton`) — `FileImport` icon, import dialog (QR + gallery)
3. **Add** (`AddButton`) — `AlertDialog` + `ProviderConfigure` for new provider

List: search, tag filters, reorder (`ReorderableLazyListState`), tap row → detail.

### `SettingProviderDetailPage.kt`

- Tabs: configuration vs models
- **Share**: `ShareSheet` + share icon → QR encodes `encodeForShare()`
- Edit fields via `ProviderConfigure`, connection test, balance option, model CRUD

### Configuration components

| Component | Path |
|-----------|------|
| `ProviderConfigure.kt` | `ui/pages/setting/components/` — type switch, API key, base URL, Claude/Google-specific |
| `ProviderConnectionTester.kt` | connection test against `ProviderManager` |
| `BalanceOption.kt` | balance fetch settings |

### Other provider-related settings (not import)

- `SettingModelPage.kt` — global model roles across providers
- `SettingSpeechPage.kt` / TTS / ASR configure — separate `TTSProviderSetting` / `ASRProviderSetting`
- `ImportExportTab.kt` — Cherry Studio / Chatbox backup import (bulk), not inline QR flow

### Persistence hook

`SettingVM` + `vm.updateSettings(settings.copy(providers = ...))` on list page; detail page updates single provider in list by id.

## Caveats / Not Found

- No dedicated “paste JSON” button on provider import dialog.
- Issue #19 likely extends `ImportProviderButton` flow and/or adds a new importer under `data/sync/importer/` — exact UX not specified in `prd.md` (still TBD).