# Assistant Tool Permissions

- `Assistant.toolPermissions` is keyed by stable capability IDs and defaults to empty for legacy JSON.
- `INHERIT` preserves the existing tool approval behavior; `ALLOW` cannot bypass a hard approval requirement.
- `ASK` forces approval before execution, and `DENY` removes the tool before the provider request.
- MCP policies include the server UUID (`mcp:<server UUID>:<tool name>`) so equal tool names on different servers stay isolated.
- Delegate-only subagents receive the intersection of parent policy and their explicit read-only projection.
