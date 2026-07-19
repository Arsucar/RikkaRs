# #153 Assistant Tool Capability Catalog Design

## Boundary

Add a pure capability domain model consumed by the existing `AssistantToolsPage`; do not add a new top-level entry.
The model describes configured, available, effective, reason, approval policy, and stable IDs without creating files,
connecting MCP, reading Room repositories, instantiating Android service tools, or executing provider tools.

## Stable Keys And Sources

- Built-in/local/memory/recent/subagent keys use a namespaced tool name.
- MCP keys use `mcp:<server UUID>:<tool name>`; display/runtime names remain `mcp__<serverName>__<toolName>`.
- Workspace keys use the `WorkspaceToolCapability` names from #151.
- Skills use `skill:<skill name>` for the capability and expose `use_skill` as the runtime tool.
- Calendar expands to its two runtime names; no UI count may assume one option equals one tool.

## Snapshot Inputs

`ToolCapabilitySnapshot` accepts only Settings/Assistant flags, already sampled MCP status and discovered tool
metadata, skill metadata from `listSkills(createIfMissing = false)`, workspace capability, and pure local option metadata.
The builder returns descriptors with `configured`, `available`, `effective`, `reasonCode`, and `needsApproval`.

`ChatService` creates a separate final runtime name snapshot immediately after its existing factories finish and before
the provider request. A test-only comparison maps effective catalog IDs to these names; catalog construction never calls
`buildGenerationTools()`.

## Availability Rules

- Configured but disabled, unselected, missing, non-READY, unauthorized, invalid, or unavailable sources remain visible
  with a stable machine reason and localized user text.
- MCP `Connected` with discovered enabled tools is available; Idle/Error/NeedsAuthorization is configured but unavailable.
- Skills use private-over-global visibility and do not create missing directories during snapshot.
- Local tools expose option metadata only; Android permissions/services are unavailable reasons until runtime.
- Memory and memory-table gates remain independent per the existing memory capability contract.
- Delegate-only and subagent profile projections are explicit and never widen parent availability.

## Tests And Side Effects

Pure table-driven tests cover every source and gate. Fake MCP/status/skill metadata are passed into the builder; tests
assert Settings, status flows, and file roots are unchanged. A cross-layer test compares catalog effective IDs with the
final runtime tool names. UI preview tests assert no network, shell, directory creation, or provider execution.

## Compatibility

No Assistant schema migration is required. Existing source bindings remain authoritative; new catalog reason codes are
additive and unknown future sources render as configured/unavailable instead of changing runtime behavior.
