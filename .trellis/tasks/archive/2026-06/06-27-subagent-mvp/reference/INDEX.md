# Reference index: `linklink256/rikkahub-sub`

- **Source repo**: [linklink256/rikkahub-sub](https://github.com/linklink256/rikkahub-sub)
- **Branch / ref**: `master` @ `3081b6e3f0c75763f17abda2fd5ded2d8b5c0938` (subagent code present on default branch; no separate `sub` / `subagent` branch required)
- **Snapshot date**: 2026-06-27

All paths below are relative to this `reference/` directory (mirror of repo paths).

---

## Files captured

| Path | Summary |
|------|---------|
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt` | Nested run host: compiles `SubagentProfile` → child `Assistant`, calls `GenerationHandler.generateText`, optional summary continuation rounds, `buildTranscript`, `sandboxToolsForSubagent` (clears `needsApproval`), async throttled `onProgress`. |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentProfile.kt` | Profile schema, builtin `explore` / `coder` / `reviewer`, merge/upsert/remove helpers; also defines `SubagentResult` and `SubagentTranscriptStep` (no separate `SubagentResult.kt`). |
| `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentTools.kt` | `spawn_subagent`, `ask_btw`, `manage_subagent_profile` tool factories; transcript in `Text.metadata`; delegation system prompts. |
| `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` | `ToolCallIdElement` / `currentToolCallId()` for parallel spawn progress; parallel tool execution when `parallelToolExecution` or 2+ `spawn_subagent` in one turn; `executeSingleTool` extracted for `async`. |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | `SubagentHost` DI; `buildSubagentTools` / `buildSubagentBaseTools` / `buildCommonTools`; `subagentDelegateOnly` read-only parent tools; `updateSubagentProgress` streaming; `manageSubagentProfile` persistence. |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | `enableSubagents`, `subagentMaxDepth`, `subagentProfiles`, `disabledBuiltinSubagents`, `subagentDelegateOnly`, `parallelToolExecution`. |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUI.kt` | `ToolUIRenderer` for `spawn_subagent`: nested `ChainOfThought` from `subagent_transcript` metadata; streaming flags in metadata. |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/ToolUI.kt` | Registers `SubagentToolUI` in `ToolUIRegistry.renderers` list. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentPage.kt` | Assistant-level toggles (enable, max depth 1–5, delegate-only, parallel execution), profile list CRUD navigation. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentProfilePage.kt` | Per-profile editor: prompt, model, sampling, maxSteps, inheritTools vs explicit local/skills/MCP chips, stream/memory/summary min length. |

---

## PRD / MVP design deltas (informative)

Compared to `.trellis/tasks/06-27-subagent-mvp/prd.md` and child PRDs (e.g. `06-27-subagent-permissions`, `06-27-subagent-ui-settings`):

| Topic | Fork (`rikkahub-sub`) | Local MVP PRD |
|-------|------------------------|---------------|
| **Default enable** | `enableSubagents = true` on `Assistant` | Migration: subagents **off** by default for existing assistants |
| **Parallel spawns** | `parallelToolExecution` + auto-parallel when ≥2 `spawn_subagent` in one turn | Parent PRD decision **#17**: parallel fan-out **out of MVP** unless runtime explicitly adds serial-first |
| **Max depth UI** | Slider **1–5** on settings page | Settings child PRD: **1–3**, default **2** |
| **Workspace policy** | `inheritTools` / `excludedTools`; reviewer builtin excludes write/shell tool **names**; delegate-only uses `createWorkspaceReadOnlyTools` | Explicit `WorkspaceAccess` / `WorkspaceApproval` matrices on profile (permissions child) |
| **Continuation** | **Summary continuation** (re-prompt if summary too short); no separate continuation handle / card affordance | Product **Continuation** = explicit follow-up spawn with prior context handle |
| **HITL** | Subagent tools: global `sandboxToolsForSubagent` → no approval | Decision **#16**: nested HITL inside subagent run; profile may still define stricter workspace approval |
| **Token rollup** | `SubagentResult.usage` + message usage accumulation in host; UI card does not clearly expose rollup in captured `SubagentToolUI` | Parent criteria: card + parent turn usage consistency |
| **ask_btw UI** | Uses default tool renderer unless separate entry added (only `SubagentToolUI` registered for spawn) | ui-chat: dedicated card for `ask_btw` |
| **Profile `canSpawn`** | Nested spawn gated by `depth + 1 < maxDepth` and non-empty profiles | Settings PRD lists **canSpawn** as first-class profile field |

---

## Not found / not copied

- Standalone `SubagentResult.kt` — types live in `SubagentProfile.kt`.
- Dedicated `permissions/` package — policy is embedded in `ChatService` + `SubagentHost.sandboxToolsForSubagent` + profile `excludedTools`.
- `createWorkspaceReadOnlyTools` implementation files (referenced from `ChatService` only; may exist elsewhere in fork app module).

---

## Related fork branches (for follow-up diffs)

Branches on the same repo that mention subagent fixes (not used for this snapshot): `fix/subagent-manage`, `fix/subagent-streaming-render`.