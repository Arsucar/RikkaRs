# Subagent System MVP — Execution Plan

Parent: `06-27-subagent-mvp`. Requirements: `prd.md`. Technical design: `design.md` (when present). Each child owns detailed steps in its own `implement.md`; this file orchestrates delivery order, integration gates, and rollback.

## 1. Task Graph

Five child tasks and dependencies:

```
subagent-model (no deps)
subagent-permissions (depends: model)
subagent-runtime (depends: model, permissions)
subagent-ui-chat (depends: model, runtime)
subagent-ui-settings (depends: model)
```

| Child slug | Trellis dir | Depends on |
|------------|-------------|------------|
| model | `06-27-subagent-model` | — |
| permissions | `06-27-subagent-permissions` | model |
| runtime | `06-27-subagent-runtime` | model, permissions |
| ui-chat | `06-27-subagent-ui-chat` | model, runtime |
| ui-settings | `06-27-subagent-ui-settings` | model |

### Execution phases

- **Phase A** — `model` + `ui-settings` (parallel; no deps beyond model for settings)
- **Phase B** — `permissions` (after model)
- **Phase C** — `runtime` (after model + permissions)
- **Phase D** — `ui-chat` (after model + runtime)
- **Phase E** — Integration (after all children complete their unit scope and parent checklist)

Dispatch rule: every sub-agent prompt starts with `Active task: <child task path>` per Trellis workflow.

## 2. Ordered Checklist

Parent-level deliverables mapped to children. Check items as the owning child lands them; run child `trellis-check` before marking a phase complete.

### `06-27-subagent-model`

- [ ] model: `SubagentProfile`, `WorkspaceAccess`, `WorkspaceApproval`, `SubagentResult`, `SubagentTranscriptStep` data classes + serialization
- [ ] model: `Assistant` extension (4 new fields) + serialization migration
- [ ] model: Built-in profile registry (`explore`, `coder`, `reviewer`)
- [ ] model: Unit tests pass

### `06-27-subagent-permissions`

- [ ] permissions: Workspace access filtering factory (`NONE` / `READ_ONLY` / `FULL`)
- [ ] permissions: Approval strategy application (`INHERIT` / `AUTO` / `OVERRIDE`)
- [ ] permissions: `allowedPathPrefixes` path validation
- [ ] permissions: `excludedTools` filtering
- [ ] permissions: `spawn_subagent` depth-gated injection
- [ ] permissions: Unit tests pass

### `06-27-subagent-ui-settings`

- [ ] settings: Assistant detail page additions (toggle, max depth, nav row)
- [ ] settings: Profile list page
- [ ] settings: Profile edit page (all fields)
- [ ] settings: State persistence through `AssistantDetailVM`

### `06-27-subagent-runtime`

- [ ] runtime: `SubagentHost.spawn()` — profile resolution, depth check, model resolution, tool building
- [ ] runtime: `spawn_subagent` tool definition + registration
- [ ] runtime: `ask_btw` tool definition + registration
- [ ] runtime: `manage_subagent_profile` tool definition + registration
- [ ] runtime: Continuation mechanism
- [ ] runtime: Promise / parallel spawn support (per PRD: serial queueing first unless runtime child explicitly delivers parallel)
- [ ] runtime: Cancellation propagation
- [ ] runtime: Token usage merging

### `06-27-subagent-ui-chat`

- [ ] ui-chat: `ToolUIRegistry` entries for `spawn_subagent` + `ask_btw`
- [ ] ui-chat: `SubagentToolUI` card (header + summary + expandable transcript)
- [ ] ui-chat: `AskBtwToolUI` card
- [ ] ui-chat: Streaming card updates
- [ ] ui-chat: Persistence of `SubagentResult` in tool output JSON

### Integration (Phase E)

- [ ] Integration: End-to-end — enable subagent, spawn explorer, get summary, see card
- [ ] Integration: Permission — reviewer cannot write, coder can
- [ ] Integration: Parallel — two spawns run concurrently (only if runtime delivers parallel; otherwise document serial behavior and skip or defer)

Cross-child gates from `prd.md` §5 apply here (depth cap, disabled subagents, continuation, token rollup, settings apply to new spawns only).

## 3. Validation Commands

Run from repo root (`release/rikka-arsucar`):

| When | Command |
|------|---------|
| After each child’s unit tests | `./gradlew :app:testDebugUnitTest` |
| After integration / before parent finish | `./gradlew :app:assembleDebug` |
| Manual smoke | Create assistant with subagents enabled; send message that triggers spawn; verify card + summary + reload persistence |

Optional module-scoped tests if children add tests outside `:app` — extend commands in child `implement.md` and note here when added.

## 4. Review Gates

| Gate | After | Review focus |
|------|-------|----------------|
| **Gate 1** | Phase A | Model types + settings UI; contracts stable before runtime and permissions depend on them |
| **Gate 2** | Phase C | Runtime integration (`SubagentHost`, tools, lifecycle, cancel, token merge) before chat UI binds to live tool parts |
| **Gate 3** | Phase E | Full integration vs parent PRD §5; serialization of historical messages; HITL behavior |

Do not call `task.py start` on parent for implementation until `prd.md`, `design.md`, and this `implement.md` are reviewed for complex-task completeness.

## 5. Rollback Points

Revert in reverse dependency order if integration fails or scope must shrink.

| After | Revert target | Notes |
|-------|---------------|--------|
| model | `Assistant.kt` / model types | New fields use safe defaults; subagents off by default |
| permissions | `WorkspaceTools.kt` (and permission factory additions) | Parent tool surface unchanged if spawn tools not registered |
| runtime | `SubagentHost` + tool registrations | Disable subagents in settings to hide spawn tools without deleting history |
| ui-chat | `ToolUIRegistry` entries + composable pages | Old tool parts should still deserialize; cards may degrade gracefully |

Document any partial rollback in parent task notes and child journals before re-entering Execute.

## 6. Child task activation order (suggested)

1. Start `06-27-subagent-model` → Gate 1 partial (model checklist)
2. Start `06-27-subagent-ui-settings` in parallel once model contracts are reviewable
3. Start `06-27-subagent-permissions` after model merge
4. Start `06-27-subagent-runtime` after permissions + model
5. Start `06-27-subagent-ui-chat` after runtime spawn path exists
6. Parent Phase E integration on `release/rikka-arsucar` with dirty tree reconciled per team practice