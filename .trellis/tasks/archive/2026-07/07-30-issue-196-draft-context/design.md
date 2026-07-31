# Design: #196 draft context assembly

## Overview

Replace hard-coded `takeLast(8).summaryAsText(500)` in `generateInputDraft` with a dedicated
`toDraftContextText` path, driven by optional `Preset.draftContext`.

## Data

- `DraftContextConfig` (app `data/model`, kotlinx.serialization)
- `Preset.draftContext: DraftContextConfig?` — null means code defaults (backward compatible)

## Assembly

- Extension `UIMessage.toDraftContextText(config, isLatest)` lives next to draft helpers in app
  (uses `UIMessage` from ai; does **not** change `summaryAsText`).
- Truncation: `takeLast(maxChars)` when `maxCharsPerMessage > 0` and not
  `(isLatest && keepLatestMessageIntact)`.
- Placeholders (hard-coded for model prompt, not UI strings):
  - media: `[图片]`, `[文件: name]`
  - tools: `[工具: name → summary≤300]`
  - reasoning: `[推理: summary≤100]`

## Resolve config

```
assistant.presetIds order → first non-null preset.draftContext ?: DraftContextConfig()
```

## UI

`PresetDetailContent` LazyColumn section after Builtin list, only if any
`PresetEntry.Builtin.builtinKey == "reply_draft"`.

## Tests

Unit tests for `toDraftContextText` + Preset serialization with/without `draftContext`.
