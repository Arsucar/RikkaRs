# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/FinishWorkTool.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt`
- Workspace tool creation/filtering and delegation-mode prompts.

## Tool Policy

- Main agent:
  - may receive `spawn_subagent` when subagents are enabled.
  - must not receive `finish_work` by default.
  - receives workspace tools only according to workspace readiness and assistant mode.
- Subagent:
  - must receive `finish_work`.
  - receives delegated tools based on subagent permission builder.

## Cancellation Semantics

Introduce or reuse structured cancellation/failure reasons instead of mapping every interrupted subagent result to "cancelled by user".

## Compatibility

Removing main-agent `finish_work` changes only an unintended capability. Existing subagent flows should remain compatible.
