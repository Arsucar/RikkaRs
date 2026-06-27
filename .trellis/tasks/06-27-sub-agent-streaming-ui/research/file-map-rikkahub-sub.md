# Research: rikkahub-sub file map (sub-agent streaming)

- **Query**: File paths for sub-agent streaming, think/summary UI on linklink256/rikkahub-sub
- **Scope**: internal
- **Date**: 2026-06-27

## Runtime & tools

| File | Role |
|------|------|
| `app/.../data/ai/subagent/SubagentHost.kt` | Nested generation; `onProgress`; `buildTranscript`; throttling |
| `app/.../data/ai/subagent/SubagentTools.kt` | `spawn_subagent` / `ask_btw`; final result + `metadata.subagent_transcript` |
| `app/.../data/ai/subagent/SubagentProfile.kt` | `SubagentResult`, `SubagentTranscriptStep` (+ `executed`, `childTranscript` on sub) |
| `app/.../data/ai/subagent/SubagentPermissionBuilder.kt` or `buildSubagentTools` in service | Tool assembly (sub: largely in `ChatService`) |
| `app/.../service/ChatService.kt` | `buildSubagentTools`, **`updateSubagentProgress`**, `updateConversationState` |
| `app/.../data/ai/GenerationHandler.kt` | **`ToolCallIdElement`**, parallel `spawn_subagent` execution |

## UI

| File | Role |
|------|------|
| `app/.../ui/components/message/tools/SubagentToolUI.kt` | Metadata-driven nested `ChainOfThought` for transcript |
| `app/.../ui/components/message/tools/ToolUI.kt` | Registers `SubagentToolUI` |
| `app/.../ui/components/ui/ChainOfThought.kt` | Shared timeline component (also main chat) |
| `app/.../ui/components/message/ChatMessage.kt` | Main assistant reasoning/tool chain |

## Think / reasoning

| File | Role |
|------|------|
| `app/.../data/ai/transformers/ThinkTagTransformer.kt` | `<think>` → `UIMessagePart.Reasoning` (multi-block on `sub/fix/think-tag-multi-block`) |

## Settings / model

| File | Role |
|------|------|
| `app/.../data/model/Assistant.kt` | `enableSubagents`, `subagentMaxDepth`, `parallelToolExecution` |
| `app/.../ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | `streamOutput` toggle |

## Docs

| File | Role |
|------|------|
| `docs/SUBAGENT_FORK.md` | Fork design, file list, packaging notes |

## Local fork equivalents (Arsucar)

| Sub path | Local path | Delta |
|----------|------------|-------|
| `SubagentToolUI.kt` | `ui/.../SubagentToolUIs.kt` (`SpawnSubagentToolUI`) | JSON-only, no metadata stream |
| `ChatService.updateSubagentProgress` | *missing* | — |
| `GenerationHandler` ToolCallId | *missing* | — |

## Remote branches (git remote `sub`)

- `sub/fix/subagent-streaming-render` — streaming UI fix chain
- `sub/fix/think-tag-multi-block` — think transformer
- `sub/master` — integrated fork (CI, voice, perf)