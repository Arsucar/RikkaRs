# Subagent System MVP

## 1. Goal

Enable the main chat assistant to delegate bounded sub-tasks to configured subagents via tools, with isolated context, enforced permissions, and clear results in the conversation.

## 2. Background

Long, tool-heavy main-agent runs mix unrelated context, blur role boundaries, and make approval and workspace risk harder to reason about. Subagents provide dedicated prompts, tool subsets, and optional models for focused work (exploration, implementation checks, research) while the parent keeps a compact summary and control surface. The MVP targets safe delegation inside existing assistant conversations—not IDE or Trellis coding agents.

## 3. Core Concepts

| Term | Meaning |
|------|---------|
| **Subagent** | A short-lived worker run under a **Profile**, invoked by the main agent, with its own message context for that run. |
| **Profile** | Named configuration: role prompt, optional model override, allowed tools, workspace access mode, and permission posture. |
| **Spawn** | Starting a subagent run from a parent tool call with a task description and chosen profile (or builtin id). |
| **Transcript** | The subagent’s internal turn history for one run; not promoted to the full conversation tree by default. |
| **Summary** | Structured outcome returned to the parent: status, answer text, optional errors, and metadata for UI and token rollup. |
| **Continuation** | A follow-up spawn that reuses prior subagent context (same logical run or explicit continuation handle) without re-sending the full parent chat. |
| **Depth** | Nesting level: parent generation is depth 0; each subagent spawn increments depth; hard cap per assistant. |
| **ask_btw** | Lightweight side-call tool: no tools, pure text, single LLM call. Used by the **main agent** for quick second opinions, not a subagent-to-parent question. |

**Subagent definition (product):** A profile-bound, depth-limited execution that runs a nested generate-and-tool loop and returns a summary to the parent through a first-class main-agent tool.

## 4. Requirements

Requirements are owned at parent level; each child delivers a slice. Cross-cutting rules apply to all children.

### Cross-cutting

- Subagents are **opt-in per assistant** with a configurable maximum depth and a set of profiles (builtin + user-defined).
- Only the **main agent** (and subagents at depth &lt; max, if nested spawn is allowed) may spawn subagents through the exposed tool surface—not arbitrary UI entry points in MVP.
- Every completed or failed run produces a **Summary** consumable by the parent model and by chat UI.
- **Token usage** from subagent runs is attributable on the subagent card and rolled up to the parent turn where product rules require transparency.
- **Cancellation** of the parent conversation generation must stop in-flight subagent work or define a clear fail-safe outcome (no orphaned approvals on the parent chat).
- **Persistence** across app restart: subagent results visible in historical messages must deserialize consistently with conversation storage.

### Child: `06-27-subagent-model` (Data Model)

- Define profile schema: identity, display metadata, system prompt, optional model id (null = inherit parent resolution rules), tool allowlists/denylists or capability flags, workspace access level, builtin vs custom origin.
- Extend assistant-level settings: enable flag, max depth, profile list, disabled builtin ids.
- Define spawn input/output contracts and continuation/ask_btw payload shapes at the data layer (stable serialization).
- Migration: existing assistants default to subagents off without breaking settings load/save.

### Child: `06-27-subagent-runtime` (Runtime Engine)

- Host nested generation using the same generation/tool-loop semantics as the main agent, with an isolated message list per spawn.
- Register main-agent **spawn** (and related) tools when subagents are enabled for the assistant.
- Enforce **depth** before starting a run; reject or no-op with explicit summary when over limit.
- Implement **Continuation** and **ask_btw** (lightweight no-tool LLM side-call) as defined in the model contracts (runtime behavior, not UI).
- Produce **Summary** and optional transcript snapshot for UI; merge child token usage into parent accounting per decision table below.
- Coordinate lifecycle with the conversation session job (no undefined concurrent generation on the same conversation).

### Child: `06-27-subagent-permissions` (Permission Layer)

- Build effective tool sets for a profile from parent-available tools (search, local, MCP, workspace, skills, memory, etc.) per allow/deny and capability rules.
- Apply **workspace** policy: which workspace tools exist for the subagent and when human approval is required, including stricter-than-default posture where configured.
- Ensure subagent tool **HITL** does not stall the parent chat in ways that violate the chosen approval policy (auto-deny, auto-approve for sandbox, or route to parent—per decisions).
- Respect path/out-of-workspace rules for mutating workspace tools when exposed to subagents.

### Child: `06-27-subagent-ui-chat` (Chat UI)

- Render subagent tool invocations as dedicated **cards** in the message tool chain (status, profile name, summary, expandable transcript per MVP depth).
- Show **token usage** on the card when global display settings allow.
- Support user-visible states: running, succeeded, failed, cancelled.
- **Continuation** affordances where the product allows the user or parent flow to continue a prior subagent run from the card context.

### Child: `06-27-subagent-ui-settings` (Settings UI)

- Assistant detail entry for subagents: master enable, max depth, builtin enable/disable, profile list CRUD.
- Profile editor: prompt, model override picker, tool/workspace permission summary aligned with permission layer capabilities.
- Validation and empty states: cannot enable without at least one usable profile; clear copy when depth is 1 vs higher.

## 5. Cross-Child Acceptance Criteria

End-to-end checks that span multiple children:

- [ ] With subagents enabled on an assistant, the main model can invoke a spawn tool; a subagent card appears in chat; on completion the parent receives a summary in the tool result and the conversation persists after reload.
- [ ] With subagents disabled, spawn tools are not offered and stored conversations with old subagent tool parts still render without crash.
- [ ] At max depth, a nested spawn attempt returns a failed summary (or equivalent) without starting another nested generation loop.
- [ ] A profile that denies workspace write tools cannot mutate workspace via subagent even if the parent assistant has workspace write enabled.
- [ ] A subagent run that requires approval under profile policy does not leave the parent main loop stuck indefinitely; outcome matches the chosen HITL policy.
- [ ] ask_btw renders as a simple Q&A card with token count in chat UI.
- [ ] Continuation from a prior run includes enough context that the subagent does not require repeating the full original task verbatim in the new spawn payload.
- [ ] Token usage shown on the subagent card is consistent with merged totals on the parent assistant message for that turn (within defined rounding/display rules).
- [ ] Settings changes (disable subagents, remove profile, lower max depth) apply to **new** spawns; in-flight runs behave per documented cancel/complete rules.
- [ ] Builtin profiles can be disabled in settings; disabled builtins are not spawnable by name/id.

## 6. Constraints & Decisions

Summarized product/architecture decisions (detail in `design.md`):

| # | Decision |
|---|----------|
| 1 | MVP is **in-app chat subagents** only; not Trellis/OpenCode coding subagents. |
| 2 | Invocation is **tool-based** from the main agent loop, not a separate chat mode. |
| 3 | Nested runs reuse the **same generation and tool-loop** behavior as the parent, with a child message list. |
| 4 | **Profiles** live on the assistant; builtins are seeded, user profiles are editable. |
| 5 | **Model override** on a profile is optional; null inherits parent assistant/global resolution. |
| 6 | **Max depth** is per assistant, enforced at spawn time. |
| 7 | Default max depth for new enablement is **2** (per original design). |
| 8 | **Transcript** is stored for UI/continuation, not merged as full peer branches in the conversation tree. |
| 9 | Parent model sees **summary + structured metadata**, not the full transcript by default. |
| 10 | **Continuation** is explicit (handle or continuation spawn), not implicit replay of parent chat. |
| 11 | **ask_btw** is a lightweight no-tool LLM side-call (主代理自用旁路工具，无工具、纯文本、一次 LLM 调用)，not a subagent-to-parent question mechanism. |
| 12 | Subagent **token usage** is merged into parent message usage for the turn. |
| 13 | Tool results remain on **assistant message tool parts** (consistent with current app semantics). |
| 14 | **Permission layer** filters tools at spawn; subagents do not widen parent capabilities. |
| 15 | Workspace exposure is **profile-scoped** (none / read-only / full with approval rules). |
| 16 | Subagent **HITL default**: nested approvals handled inside subagent run without blocking parent unless policy routes to parent. |
| 17 | **Parallel spawns** are out of MVP unless runtime child explicitly delivers serial queueing first. |
| 18 | **Serialization** of profiles and results is versioned for forward-compatible settings and history. |
| 19 | **No** user-defined arbitrary code execution profile type in MVP—prompt + tool policy only. |
| 20 | External reference implementation (`rikkahub-sub`) is informative only; MVP ships against current `release/rikka-arsucar` architecture. |

## 7. Out of Scope

- Subagent marketplace, sharing profiles across users/devices, or cloud-hosted profile sync.
- Automatic subagent orchestration without an explicit parent tool call (background autonomous agents).
- Full conversation-tree branching that treats each subagent turn as a user-visible MessageNode branch.
- Replacing MCP, workspace, or local tools with a new tool protocol.
- Multi-conversation subagent memory or cross-assistant profile libraries.
- Guaranteed parallel fan-out of many subagents with merged ordering in one parent step (post-MVP).
- IDE/Trellis task subagent injection hooks (already exist separately).
- Legal/compliance audit logging beyond existing conversation persistence.

## 8. Risks

| Risk | Impact | Mitigation direction |
|------|--------|----------------------|
| Parent generation job vs nested run concurrency | Duplicate updates, stuck UI, double save | Single session ownership rules in runtime child; documented cancel propagation |
| HITL pending tools surfacing on parent chat | User confusion, blocked main agent | Permission layer + isolated nested approval handling per decision 16 |
| Token/cost blow-up from depth & retries | User trust, billing surprises | Depth cap, summary-only parent context, usage rollup visibility |
| Profile misconfiguration (over-broad tools) | Data exfiltration or destructive workspace ops | Deny-by-default tool building; workspace path rules; settings validation |
| Missing external draft / fork parity | Rework if assumptions diverge | Treat `research.md` and this PRD as source of truth for MVP; optional fork diff before implement |
| Large transcripts in persistence | DB bloat, slow load | Cap stored transcript size for MVP; expandable UI loads truncated view |
| ask_btw UX | Minor | ask_btw is a simple no-tool side-call; renders as Q&A card |
| Settings migration errors | Assistants fail to load | Model child: defaults off, backward-compatible deserialization |

## Notes

- Child tasks own detailed acceptance criteria in their own `prd.md` files; parent criteria above are integration gates only.
- Technical design belongs in `design.md`; execution order belongs in `implement.md` before `task.py start`.