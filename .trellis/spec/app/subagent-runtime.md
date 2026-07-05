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

### 4. Validation & Error Matrix

- Main agent has `parallelToolExecution = true` -> root `ChatService` still calls `generateText` with a serial assistant copy.
- Child subagent has `parallelToolExecution = false` -> `GenerationHandler` executes that subagent's tool calls sequentially.
- Child subagent has `parallelToolExecution = true` -> `GenerationHandler` may execute that subagent's same-turn tool calls concurrently.
- Countdown threshold `null` -> inject automatic countdown reminders near the subagent tool budget.
- Countdown threshold `0` -> do not inject countdown reminders.
- Countdown total present with 1 executed tool and `stepIndex = 8` -> remaining is `total - 1`, not `total - 8`.
- `manage_subagent_profile` args include hidden `temperature`, `max_tokens`, or `inherit_tools` -> persisted profile keeps existing advanced values.
- Tool budget stop after an assistant message with text + tool call -> subagent still performs a no-tool summary pass before returning to the parent.

### 5. Good/Base/Bad Cases

- Good: root generation uses `assistant.copy(parallelToolExecution = false)`, while `SubagentHost` explicitly copies subagent control fields into child assistants.
- Base: subagent profile with no custom threshold gets automatic reminders.
- Bad: generic root generation reads `Assistant.parallelToolExecution` directly from the persisted assistant and parallelizes main-agent tools.
- Good: `applyPatch()` reads only fields exposed in `manage_subagent_profile.parameters()`.
- Bad: `applyPatch()` keeps accepting removed advanced fields because models can still send schema-hidden JSON keys.

### 6. Tests Required

- Unit test that disabled subagent parallel execution does not inject parallel guidance into `spawn_subagent` prompt/schema text.
- Unit test that enabled subagent parallel execution does inject parallel guidance.
- Unit test that countdown threshold `null` resolves to automatic and `0` resolves to disabled.
- Unit test that countdown remaining uses executed tool calls when `stepsCountdownTotal` is present.
- Unit test that `manage_subagent_profile` ignores schema-hidden advanced fields.
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
