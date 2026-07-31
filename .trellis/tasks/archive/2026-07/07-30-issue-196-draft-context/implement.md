# Implement: #196 draft context

## Checklist

1. [x] `DraftContextConfig` + `Preset.draftContext`
2. [x] `UIMessage.toDraftContextText` + resolve helper
3. [x] `ChatService.generateInputDraft` integration
4. [x] PresetDetailPage conditional section + strings en/zh
5. [x] Unit tests (assembly + serialization null-compat)

## Validation (focused)

```powershell
.\gradlew --no-daemon :ai:testDebugUnitTest :app:testDebugUnitTest --tests "*DraftContext*" --tests "*PresetEntrySerialization*"
```

No installDebug / no full lint in this agent.
