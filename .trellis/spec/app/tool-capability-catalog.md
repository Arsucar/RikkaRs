# Assistant Tool Capability Catalog

## Scope

Use this contract whenever assistant tool availability is summarized for UI, diagnostics, permissions, presets, or
runtime consistency checks.

## Contracts

- `ToolCapability.id` is stable and source-qualified. MCP IDs include the server UUID; skill rows use the skill name.
- `configured`, `available`, and `effective` are distinct. A configured capability may remain visible while unavailable,
  and an available capability may be filtered from delegate-only execution.
- `reasonCode` is a machine-readable explanation. UI code maps it to localized text and never displays enum names.
- `ToolCapabilitySnapshot.effectiveRuntimeNames` is deduplicated because several skill rows share `use_skill`.
- Workspace availability must use `resolveWorkspaceToolCapability`; delegation-only exposes only
  `workspace_read_file`.
- Normal memory and memory-table gates are independent.
- MCP availability requires a selected and enabled server, enabled tool, connected status, and valid runtime names.
- Preview/catalog construction is pure. It receives already sampled workspaces, skills, MCP configs, and statuses and
  must not create directories, connect services, execute factories, query Room, access shell/network, or call tools.

## Validation

- Cover every static source and each dynamic expansion: calendar, multiple skills sharing `use_skill`, MCP server IDs,
  workspace tools, and subagent tools.
- Cover MCP disabled, invalid, connecting, reconnecting, authorizing, error, idle, and connected states.
- Assert approval metadata for management and MCP tools.
- Compare the effective runtime-name projection with final generation tool names at the cross-layer test boundary.
- Parse every configured locale resource and verify capability reason keys have matching coverage.
