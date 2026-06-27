# Subagent Permission Layer

## Goal

When the runtime builds a subagent’s tool surface, apply profile-driven rules so workspace access, human-in-the-loop approval, path boundaries, tool inheritance/exclusion, and nested spawn eligibility are enforced consistently. This child does not own spawn orchestration or generation loops; it owns **policy application** on tool lists and tool execution boundaries.

## Parent & Dependencies

- **Parent**: `06-27-subagent-mvp`
- **Depends on subagent-model**: `SubagentProfile`, `WorkspaceAccess`, `WorkspaceApproval`, and related serializable fields (e.g. `allowedPathPrefixes`, `excludedTools`, `inheritTools`, `canSpawn`, approval override maps) must exist and match the agreed access/approval matrices before this layer integrates.

## Requirements

### 1. Workspace access filtering (`WorkspaceAccess`)

Subagents must receive only the workspace tool subset implied by the profile’s `workspaceAccess`:

| Access | Workspace tools exposed to subagent |
|--------|-------------------------------------|
| `NONE` | No workspace tools |
| `READ_ONLY` | Read capability plus shell only (no write or edit workspace tools) |
| `FULL` | Full workspace tool set (read, write, edit, shell) |

Filtering applies when assembling the subagent tool list from the same workspace tool factory the main agent uses. Tools removed by access level must not appear in the subagent’s registered tool set (not merely hidden at execute time).

### 2. Approval strategy (`WorkspaceApproval`)

For each workspace tool retained after access filtering, approval behavior for the subagent must follow `workspaceApproval`:

| Strategy | Behavior |
|----------|----------|
| `INHERIT` | Same effective approval rules as the parent/workspace configuration for that tool (including existing workspace overrides and path-based approval where the main agent already applies them). |
| `AUTO` | No human-in-the-loop pause for subagent workspace tools: approval is not required at the subagent boundary for those tools. |
| `OVERRIDE` | Per-tool approval behavior is taken from the profile’s override map (e.g. `toolApprovalOverrides`), superseding inherit defaults for named tools. |

Approval policy must affect whether generation pauses for approval before execute, consistent with how `needsApproval` is evaluated for the main agent.

### 3. Path prefix allowlist (`allowedPathPrefixes`)

- **Default** when the profile does not specify prefixes: `["/workspace"]`.
- For mutating workspace tools and shell (write, edit, shell—and any other tool that targets a filesystem path), **before** execution, validate that the resolved target path starts with at least one allowed prefix.
- Validation runs **in addition to** existing workspace boundary checks (e.g. path-outside-workspace rules); both must pass where applicable.
- If validation fails: return a **clear, actionable error message** to the subagent as the tool result; do not crash the host or abort unrelated work.

Read-only tools should not be blocked solely by prefix rules unless product matrix explicitly requires path checks for read paths; mutating and shell paths are in scope for prefix enforcement.

### 4. Tool exclusion (`excludedTools`)

When `inheritTools` is true, the subagent starts from the parent’s non-workspace and workspace tool surface (after workspace access filtering). Remove any tool whose name appears in `profile.excludedTools`.

Exclusion runs **after** workspace access filtering so a tool cannot be reintroduced by inheritance when access or exclusion both forbid it.

### 5. `spawn_subagent` injection

Inject the `spawn_subagent` tool into a subagent’s tool list only when **all** of the following hold:

- Profile `canSpawn` is true.
- `(current nesting depth + 1) < configured max depth` (assistant/profile max depth from model).

Otherwise `spawn_subagent` must be absent from that subagent’s tool list to prevent unbounded recursion.

### 6. Non-workspace tools

Search, MCP, skills, and local tools follow the same **inherit then exclude** model as the parent when `inheritTools` is true. When `inheritTools` is false, only tools explicitly granted by product/runtime contract for that profile apply (as defined in the parent MVP; this child applies exclusion and approval wrapping on whatever set the runtime passes in).

Legacy or placeholder “sandbox all tools” behavior is **out of scope**; permissions are expressed only through access, approval, path prefix, exclusion, and spawn gating above.

## Constraints

- Permission logic must be deterministic and testable from profile + depth + parent tool snapshot inputs.
- Errors from path prefix violations must be user- and model-visible tool errors, not uncaught exceptions.
- Must not weaken main-agent workspace policy when subagents are disabled; this layer applies only on subagent tool construction and subagent tool execution paths.

## Out of scope

- UI for editing profiles or workspace approvals.
- Implementing `SubagentProfile` types or persistence (subagent-model).
- Running generation loops, parallel spawn scheduling, or transcript storage (subagent-runtime).
- Replacing workspace repository approval storage or MCP server configuration.

## Acceptance Criteria

- [ ] `NONE` access: subagent tool list contains zero workspace tools.
- [ ] `READ_ONLY` access: only read and shell workspace tools appear; write and edit workspace tools are absent.
- [ ] `FULL` access: read, write, edit, and shell workspace tools are present when the workspace is otherwise eligible for tools.
- [ ] `AUTO` approval: subagent workspace tools do not require HITL approval at the subagent boundary.
- [ ] `INHERIT` approval: subagent workspace tool approval matches parent/workspace configuration for the same tool and arguments.
- [ ] `OVERRIDE` approval: tools named in the profile override map use the overridden approval behavior instead of inherit.
- [ ] Path outside `allowedPathPrefixes` (default `["/workspace"]`) returns a clear error tool result for write, edit, and shell (and does not crash).
- [ ] `excludedTools` removes named tools from the inherited set after access filtering.
- [ ] `spawn_subagent` is present only when `canSpawn` is true and depth allows; absent otherwise.
- [ ] Non-workspace inherited tools respect `excludedTools`; behavior aligns with inherit/exclude rules above.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Align tool naming with `createWorkspaceTools` registrations (`workspace_read_file`, `workspace_write_file`, `workspace_edit_file`, `workspace_shell`) for matrix tests.
- Runtime child consumes this layer’s outputs when building per-subagent `List<Tool>` and when wrapping execute paths for path checks.