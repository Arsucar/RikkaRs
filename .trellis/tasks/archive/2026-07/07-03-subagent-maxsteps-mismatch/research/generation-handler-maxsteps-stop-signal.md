# Research: GenerationHandler.generateText and maxSteps stop observability

- Query: Whether subagent layer can detect run exhausted maxSteps after generateText / runToCompletion
- Scope: internal (app + ai modules)
- Date: 2026-07-03

## Findings

### 1. GenerationHandler.kt path and generateText

| File Path | Description |
|---|---|
| app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt | App-layer tool loop |

Return type: Flow<GenerationChunk>; sealed interface has only GenerationChunk.Messages(messages: List<UIMessage>). No stopReason, truncated, or stepsUsed on chunks or flow completion.

### 2. maxSteps loop (lines ~132-351)

for (stepIndex in 0 until maxSteps) {
  isLastStep = stepIndex >= maxSteps - 1
  if (isLastStep) inject MAX_STEPS_PROMPT user message and disable all tools
  generateInternal; if no pending tool calls -> break
  if tool approval pending -> break
  execute tools; if finish_work -> break
}

No flag when loop ends because all maxSteps iterations completed vs early break.

### 3. maxSteps callers

- ChatService.kt ~651: omits maxSteps -> default 256
- SubagentHost runToCompletion ~369: profile.maxSteps.coerceIn(1, 256)
- SubagentHost continuation ~227: maxSteps = 1
- SubagentHost ask helper ~316: maxSteps = 1
- SubagentRegistry builtins: 48/64/24
- SubagentTools.kt ~294: max_steps arg override

### 4. ToolCallBudgetStop

SubagentHost.kt ~40: private object ToolCallBudgetStop : Exception()
~383-389: if countToolCalls >= profile.maxToolCalls then truncated=true and throw; caught in runToCompletion.
SubagentResult.truncated means maxToolCalls budget, NOT maxSteps.

### 5. runToCompletion post-generateText

~359-399: only finalMessages, lastAssistantText summary, truncated from tool budget.
spawn ~249: buildFallbackSummary assumes max steps when summary blank (~572+).

### 6. stopReason / finishReason

Provider-level finishReason in ai module (OpenAI/Claude/Google). Not used by GenerationHandler for agent loop. No StopReason.MAX_STEPS enum in repo.

### Answer

Subagent layer CANNOT reliably tell maxSteps exhaustion after runToCompletion today. Would need new signal from GenerationHandler (loop exit reason) distinct from truncated (tool calls).

## Caveats

Prior file github-issue-32-subagent-maxsteps.md in same research/ folder.
