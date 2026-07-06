# Implementation Plan

## Checklist

- [x] Inspect current main-agent tool construction in `ChatService`.
- [x] Remove main-agent `createFinishWorkTool()` registration from the `enableSubagents` branch.
- [x] Confirm `SubagentPermissionBuilder` still injects `finish_work`.
- [x] Audit `FinishWorkTool.systemPrompt` aggregation path.
- [x] Audit `SubagentHost` cancellation result mapping.
- [x] Align delegation-only prompt/tool availability.
- [x] Add or update focused tests where test seams exist.

## Validation

- [x] `.\gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.subagent.SubagentRuntimeTest" --tests "me.rerere.rikkahub.data.ai.subagent.SubagentPermissionTest" --no-daemon`
- [x] Static check: `ChatService.kt` no longer references `createFinishWorkTool` or `finish_work`.
- [x] Static check: `SubagentPermissionBuilder` still injects `createFinishWorkTool()`.
- [x] `.\gradlew :app:compileDebugKotlin --no-daemon`
- [x] `adb devices` found `100.99.129.110:5555` in `device` state.
- [x] `.\gradlew :app:installDebug --no-daemon`
