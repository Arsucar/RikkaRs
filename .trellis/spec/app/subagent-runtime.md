# Subagent Runtime

## Scenario: Subagent Control Fields

### 1. Scope / Trigger

- Trigger: changing subagent settings, runtime tool-loop behavior, spawn tool schema/prompt text, or budget reminders.
- These fields cross UI, serialized `Assistant` / `SubagentProfile`, `ChatService`, `SubagentHost`, and `GenerationHandler`.

### 2. Signatures

- `Assistant.parallelToolExecution: Boolean`
- `Assistant.subagentMaxConcurrent: Int`
- `Assistant.stepsCountdownThreshold: Int?`
- `SubagentHost.spawn(parentAssistant: Assistant, ...)`
- `GenerationHandler.generateText(assistant: Assistant, stepsCountdownThreshold: Int? = null, ...)`
- `SubagentSessionRegistry.requestCancel(conversationId: Uuid, reason: String = SUBAGENT_STOPPED_REASON)`
- `SubagentHost.requestCancel(conversationId: Uuid, reason: String = SUBAGENT_STOPPED_REASON)`
- `SubagentContextCache.acquireForReuse(contextId, scope)`
- `SubagentResult.contextId: String?`
- `SubagentResult.contextStatus: SubagentStatus?`

### 3. Contracts

- `parallelToolExecution` is a subagent control-page setting. Root chat generation must keep the main agent's tool execution default/serial and must not use this field to parallelize main-agent tools.
- `SubagentHost.buildChildAssistant()` must pass subagent control fields from the parent assistant into the child assistant used for subagent generation.
- `stepsCountdownThreshold == null` means automatic countdown threshold for subagents.
- `stepsCountdownThreshold == 0` means countdown reminders are disabled.
- Positive `stepsCountdownThreshold` values are clamped to the effective subagent `maxToolCalls`.
- `createSubagentTools()` should only mention same-response parallel `spawn_subagent` behavior when the receiving agent will actually run tools in parallel.
- When `GenerationHandler.generateText()` receives `stepsCountdownTotal`, countdown reminders are for the explicit budget total and must subtract executed `UIMessagePart.Tool` parts, not generation-loop step indexes.
- `manage_subagent_profile` is an execution-time allowlist. The tool may only patch `description`, `system_prompt`, `model_id`, `max_tool_calls`, and `disable_tool_budget_stop`; schema-hidden advanced fields must be ignored even if present in hand-written JSON args.
- When a subagent run is stopped by `maxToolCalls`, `SubagentHost` must request a no-tool final budget summary even if the budget-exhausting assistant message already contains text. Text emitted before or beside a tool call is not a final post-tool summary.
- `finish_work` is a subagent loop terminator. Root `ChatService` must not add `createFinishWorkTool()` just because `Assistant.enableSubagents` is true; child tool lists must continue to receive it through `buildSubagentTools()`.
- Main-agent prompts must not contain `FinishWorkTool.systemPrompt` unless a future explicit main-agent finish-work feature is added.
- Delegation-only mode may expose read/context tools plus `spawn_subagent`, but its prompt must not claim write or shell execution tools are available when they are filtered out.
- Missing tool calls must return a clear tool-unavailable result instead of a stack trace when the model calls a tool that is not registered in the current assistant mode.
- Default subagent cancellation is a neutral stop (`SUBAGENT_STOPPED_REASON`). Only user stop paths such as `ChatService.stopGeneration()` may pass `SUBAGENT_USER_CANCEL_REASON`.
- A reusable subagent context stores the complete `List<UIMessage>`. `SubagentTranscriptStep` remains a truncated UI/audit projection and must not be used to resume generation.
- Cache ingress, storage, and returned contexts must use defensive message snapshots, including message/part lists, mutable part metadata holders, and nested `Tool.output` parts.
- Fresh subagent runs allocate a context id before provider generation. Every emitted message chunk must update the cache before UI progress callbacks run.
- Reuse atomically appends one new user task while acquiring the lease. Executed `UIMessagePart.Tool` values stay in history and must not be executed again.
- Context reuse is an atomic lease. A context in `RUNNING` state rejects concurrent reuse with `CONTEXT_IN_USE` instead of waiting.
- Reuse scope includes root conversation, parent assistant, workspace id/cwd, depth, profile, and workspace access. Scope mismatch must fail without exposing cached content.
- Context cache expiry is sliding (one hour by default) and eviction is access-order LRU (16 entries by default). `RUNNING` entries neither expire nor evict; temporary overflow is allowed until a terminal transition.
- Completion, provider/stream interruption, and user stop retain the latest full message snapshot with `COMPLETED`, `INTERRUPTED`, or `FAILED` status.
- Cancellation cleanup must persist `INTERRUPTED` under `NonCancellable` and then rethrow `CancellationException`; child-tool setup and other `runCatching` blocks must not swallow cancellation.
- Local validation/programming failures use `FAILED`; provider transport/stream failures use `INTERRUPTED`; recognized context-window errors use `FAILED` plus `CONTEXT_TOO_LONG` only for reuse calls.
- Context ids and statuses must be present in final tool payload/metadata. Streaming placeholder metadata must expose the id before the first assistant output so cancellation cannot hide it.
- Context-length normalization applies only to reuse and only to recognized provider context-window errors. Do not estimate tokens from character count.

### 4. Validation & Error Matrix

- Main agent has `parallelToolExecution = true` -> root `ChatService` still calls `generateText` with a serial assistant copy.
- Child subagent has `parallelToolExecution = false` -> `GenerationHandler` executes that subagent's tool calls sequentially.
- Child subagent has `parallelToolExecution = true` -> `GenerationHandler` may execute that subagent's same-turn tool calls concurrently.
- Countdown threshold `null` -> inject automatic countdown reminders near the subagent tool budget.
- Countdown threshold `0` -> do not inject countdown reminders.
- Countdown total present with 1 executed tool and `stepIndex = 8` -> remaining is `total - 1`, not `total - 8`.
- `manage_subagent_profile` args include hidden `temperature`, `max_tokens`, or `inherit_tools` -> persisted profile keeps existing advanced values.
- Tool budget stop after an assistant message with text + tool call -> subagent still performs a no-tool summary pass before returning to the parent.
- Main agent with `enableSubagents = true` -> tool list includes `spawn_subagent` and excludes `finish_work`.
- Spawned subagent tool list -> includes exactly one `finish_work`, even if parent tools also contain it.
- Delegation-only model calls filtered `workspace_shell` -> tool output says the tool is not available in this assistant mode and lists available tools.
- Internal/parent stop without explicit user reason -> subagent summary is not "Task cancelled by user".
- Explicit `ChatService.stopGeneration()` user stop -> reason remains `Generation cancelled by user`.
- Completed context -> can be leased again with the same owner scope and its full history.
- Provider fails after emitted chunks -> cached messages equal the latest emitted chunk and status is `INTERRUPTED`.
- Expired context -> reuse returns `CONTEXT_EXPIRED`; unknown id -> `CONTEXT_NOT_FOUND`.
- Cache over capacity -> evict the least-recently-used terminal entry, never a running entry.
- Two concurrent reuse attempts -> exactly one lease succeeds and the other returns `CONTEXT_IN_USE`.
- Reuse from another conversation/assistant/workspace/depth/profile/access scope -> `CONTEXT_SCOPE_MISMATCH`.

### 5. Good/Base/Bad Cases

- Good: root generation uses `assistant.copy(parallelToolExecution = false)`, while `SubagentHost` explicitly copies subagent control fields into child assistants.
- Base: subagent profile with no custom threshold gets automatic reminders.
- Bad: generic root generation reads `Assistant.parallelToolExecution` directly from the persisted assistant and parallelizes main-agent tools.
- Good: `ChatService` exposes `spawn_subagent` but not `finish_work`; `buildSubagentTools()` appends `createFinishWorkTool()` for child loops.
- Bad: root `enableSubagents` branch adds `createFinishWorkTool()`, which injects subagent-only finish instructions into ordinary chat.
- Good: default subagent stop reason is neutral; user cancellation is opt-in at the user stop callsite.
- Bad: every interrupted subagent run reports "Generation cancelled by user" regardless of the actual stop source.
- Good: `applyPatch()` reads only fields exposed in `manage_subagent_profile.parameters()`.
- Bad: `applyPatch()` keeps accepting removed advanced fields because models can still send schema-hidden JSON keys.
- Good: context reuse validates owner scope, appends the new user task, and flips the context to `RUNNING` in one cache lock.
- Base: a completed context is defensively snapshotted, leased by the same scope, and resumed from full `UIMessage` history.
- Bad: code reads a cached list, releases the lock, appends the task later, or stores `SubagentTranscriptStep`; concurrent callers can share a lease and truncated transcripts cannot resume execution.

### 6. Tests Required

- Unit test that disabled subagent parallel execution does not inject parallel guidance into `spawn_subagent` prompt/schema text.
- Unit test that enabled subagent parallel execution does inject parallel guidance.
- Unit test that countdown threshold `null` resolves to automatic and `0` resolves to disabled.
- Unit test that countdown remaining uses executed tool calls when `stepsCountdownTotal` is present.
- Unit test that `manage_subagent_profile` ignores schema-hidden advanced fields.
- Unit or focused static test that root subagent enablement excludes `finish_work`, while `buildSubagentTools()` still injects it for child loops.
- Unit test that default subagent cancellation reason is neutral and user-cancel wording requires `SUBAGENT_USER_CANCEL_REASON`.
- Focused check that missing/unregistered tool calls return clear unavailable-tool output.
- Unit tests for subagent context TTL, expiry tombstone bounds, LRU order, running-entry protection, concurrent leases, and scope validation.
- Unit tests for deep snapshot isolation, atomic task append, permission fingerprint stability, cancellation persistence/rethrow, and failure classification.
- Unit tests that tool schema accepts `reuse_context_id` and result payload/metadata round-trip `context_id` plus status.
- Unit tests that interrupted histories retain executed tool input/output and reuse does not replay those tools.
- Compile check: `.\gradlew :app:compileDebugKotlin --no-daemon`.

### 7. Wrong vs Correct

#### Wrong

```kotlin
generationHandler.generateText(
    assistant = assistant,
    tools = rootTools,
)
```

This lets the subagent control-page parallel setting affect the main agent.

#### Correct

```kotlin
generationHandler.generateText(
    assistant = assistant.copy(parallelToolExecution = false),
    tools = rootTools,
)
```

Subagent runtime then receives the persisted control fields through `SubagentHost.buildChildAssistant()`.

#### Wrong

```kotlin
maxToolCalls = if ("max_tool_calls" in params) int("max_tool_calls") else maxToolCalls
temperature = flt("temperature") ?: temperature
inheritTools = bool("inherit_tools") ?: inheritTools
```

This lets schema-hidden fields remain writable.

#### Correct

```kotlin
return copy(
    description = str("description") ?: description,
    systemPrompt = str("system_prompt") ?: systemPrompt,
    maxToolCalls = if ("max_tool_calls" in params) {
        int("max_tool_calls")?.coerceIn(1, 256) ?: maxToolCalls
    } else {
        maxToolCalls
    },
    disableToolBudgetStop = bool("disable_tool_budget_stop") ?: disableToolBudgetStop,
)
```

The execution contract matches the public tool schema.

#### Wrong

```kotlin
if (assistant.enableSubagents) {
    add(createFinishWorkTool())
    addAll(buildSubagentToolsForChat(...))
}
```

This exposes a subagent loop terminator and its system prompt to the main agent.

#### Correct

```kotlin
if (assistant.enableSubagents) {
    addAll(buildSubagentToolsForChat(...))
}
```

`buildSubagentTools()` appends `createFinishWorkTool()` only when constructing child subagent tool lists.

#### Wrong

```kotlin
fun requestCancel(conversationId: Uuid, reason: String = "Generation cancelled by user")
```

This makes internal stops look like user cancellations.

#### Correct

```kotlin
fun requestCancel(conversationId: Uuid, reason: String = SUBAGENT_STOPPED_REASON)
```

User-facing stop callsites pass `SUBAGENT_USER_CANCEL_REASON` explicitly.

#### Wrong

```kotlin
val cached = cache.get(contextId)
val resumed = cached.messages + UIMessage.user(task)
cache.markRunning(contextId)
```

The read, task append, and state transition are separate operations. Two callers can both reuse the same context, and a mutable message list can change after it is cached.

#### Correct

```kotlin
val acquired = cache.acquireForReuse(
    contextId = contextId,
    scope = expectedScope,
    messagesToAppend = listOf(UIMessage.user(task)),
)
```

The cache validates TTL/status/scope, deep-snapshots history, appends the task, refreshes LRU/TTL, and changes status to `RUNNING` in one `Mutex` critical section.
