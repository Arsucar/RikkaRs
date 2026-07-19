# Implement: Subagent memory table injection (#164)

## Checklist

1. [x] Replace `injectedMemoryTableTemplateIds` with `injectedMemoryTableDocumentIds` on `SubagentProfile`.
2. [x] Rewrite `resolveSubagentMemoryTableInjection` for document ids + optional `memoryTableIsolation`.
3. [x] Wire DI loader: ConversationRepository for isolation, getEffectiveDocuments, resolve, transformer.
4. [x] SubagentHost: pass document ids; first spawn only.
5. [x] UI: multi-select documents from `memoryTableDocuments` with template-name labels + missing chips.
6. [x] Strings: wording for document instances if needed (en/zh/zh-rTW).
7. [x] Tests: roundtrip, filter invalid, isolation CONVERSATION-only, empty selection.
8. [x] Also fix tools page dead `onOpenPresets` param (review finding) if still present.

## Validation (final check agent only)

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.subagent.SubagentMemoryTableInjectionTest" --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantToolsPageTest"
.\gradlew --no-daemon :app:compileDebugKotlin
```

## Rollback

Remove field + host wiring + UI block.
