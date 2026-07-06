# Subagent tool policy and finish_work scope

## Goal

Fix incorrect tool exposure and error semantics around subagents, `finish_work`, cancellation, and delegation-only workspace tooling.

## Issues

- #42: enabling subagents injects `finish_work` into the main agent even though it is designed as a subagent meta-tool.
- #33: subagent task can be reported as "Generation cancelled by user" when the user did not cancel.
- #33: delegation-only mode can lead the main assistant to call `workspace_shell` when it is not registered.

## Requirements

- `finish_work` must be registered for spawned subagent loops, not ordinary main-agent conversations merely because `enableSubagents` is true.
- Main-agent system prompts must not include `FinishWorkTool` instructions unless a future explicit main-agent setting is added.
- Tool loop termination on `finish_work` must remain correct for subagents.
- Cancellation errors must distinguish real user cancel, parent generation stop, timeout, model/tool failure, and permission/tool unavailable cases where possible.
- Delegation-only mode must align prompt/tool registration so the model is not asked to use unavailable workspace tools.

## Acceptance Criteria

- [ ] With `enableSubagents=true`, main-agent tool list excludes `finish_work`.
- [ ] Spawned subagents still always receive `finish_work`.
- [ ] Main-agent system prompt does not instruct `finish_work` use.
- [ ] A missing tool in delegation-only mode returns a clear policy/tool-availability error or is prevented by prompt/tool configuration.
- [ ] Subagent cancellation summaries do not claim user cancellation unless user cancellation occurred.
- [ ] Focused tests or reproducible manual checks cover main tool list, subagent tool list, and cancellation reason mapping.
