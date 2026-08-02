# Batch 1 CRITICAL/HIGH Fix Plan

## Must Fix (Batch 1A — correctness/data-loss/security)

| ID | Sev | File | Fix |
|----|-----|------|-----|
| F1-2/F2-6 | C | Conversation.kt | Coerce selectIndex in currentMessages / MessageNode.currentMessage |
| F1-1 | C | ChatVM + ChatService | stopGeneration + tombstone session before delete |
| F8-1 | C | EventsRoutes.kt | Redact secrets from Settings SSE payload |
| F8-2 | C | WebApiModule.kt | Default JWT required when binding non-loopback; never stream raw keys |
| F3-1 | C | ResponseAPI.kt | Use call_id not item id for tool calls |
| F4-1 | C | QuickJSFetch.kt | SSRF/scheme guard (block file/private IPs) |
| F6-1 | C | DocumentAsPromptTransformer + parsers | Size/char caps on parse |
| F6-2 | C | Docx/Pptx/Epub parsers | Zip entry size limits |
| F1-6 | H | RegexOutputTransformer | visual=false must not mutate durable path incorrectly |
| F3-2 | H | GoogleProvider.kt | Don't overwrite custom tools with BuiltInTools put |
| F3-3 | H | ChatCompletionsAPI.kt | close(error) not throw in SSE |
| F3-4 | H | ChatCompletionsAPI | Stop logging full request body |
| F4-2 | H | JavascriptTool.kt | destroy QuickJS context |
| F7-3 | H | TTSAutoPlay.kt | Filter by conversationId |
| F2-4 | H | AssistantVM.kt | Clear assistantId on delete current |
| F1-4/F2-3/F13-2 | H | ChatService generateTitle/Suggestion | Patch field only, not full DB object |
| F1-5 | H | ChatService handleMessageComplete | Surface error when model null |
| F7-1 | H | AudioPlayer | AudioAttributes / focus |
| F5-1 | C | RootfsInstaller | Contain absolute tar symlinks |
| F10-4 | C | BackupRestorer | Zip bomb limits |
| F14-1 | C | docs | Remove keystore password from docs (if present) |

## Defer to report (large/architectural)

- F5-2 shell not real jail (threat model)
- F10-1/2 backup/datastore encryption (large)
- F10-3 atomic restore (large refactor)
- F9-1 Migration_11_12 historical (already applied)
- F8-3/4 IDOR (needs auth design)
- F11 streaming recompose (perf, large)
