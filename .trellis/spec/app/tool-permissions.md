# Assistant Tool Permissions

- `Assistant.toolPermissions` is keyed by stable capability IDs and defaults to empty for legacy JSON.
- `INHERIT` preserves the existing tool approval behavior; `ALLOW` cannot bypass a hard approval requirement.
- `ASK` forces approval before execution, and `DENY` removes the tool before the provider request.
- MCP policies include the server UUID (`mcp:<server UUID>:<tool name>`) so equal tool names on different servers stay isolated.
- Delegate-only subagents receive the intersection of parent policy and their explicit read-only projection.
- Policy changes are applied when the next generation tool list is finalized; an in-flight generation keeps its frozen list.
- Presets accept only version 1, at most 256 stable keys, a 128-character name, and a 512-character description.
- Preset persistence rejects malformed, oversized, unknown-version, MCP-resource, and private-skill entries before storage.
- Applying a preset may return `SKIPPED_UNKNOWN`: known changes are applied atomically while unknown stable keys are reported.
- Cross-assistant copy excludes MCP bindings and private skill resources; `skill:management` remains a stable capability policy.
