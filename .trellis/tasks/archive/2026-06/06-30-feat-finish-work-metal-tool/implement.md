# Implement: `finish_work` meta-tool

## Preconditions

- [ ] `prd.md` reviewed
- [ ] `design.md` reviewed
- [ ] `python ./.trellis/scripts/task.py start` (status → `in_progress`) before app code changes

## Execution order

### Step 1 — `FinishWorkTool.kt` (foundation)

**File (new):** `app/src/main/java/me/rerere/rikkahub/data/ai/tools/FinishWorkTool.kt`

- [ ] `const val FINISH_WORK_TOOL_NAME = "finish_work"`
- [ ] `fun createFinishWorkTool(): Tool` per design: `parameters = { null }`, `needsApproval = { false }`, `systemPrompt` lambda, `execute` → `listOf(UIMessagePart.Text("Task completed."))`

**Validation**

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
```

**Rollback:** delete `FinishWorkTool.kt`.

**Acceptance:** compile passes; tool name constant exported for other modules.

---

### Step 2 — Unit tests for tool + loop helper (before or right after Step 3)

**Files**

- **New (preferred):** `app/src/test/java/me/rerere/rikkahub/data/ai/tools/FinishWorkToolTest.kt`
  - [ ] `createFinishWorkTool().execute({})` → single text part `"Task completed."`
  - [ ] `needsApproval` false for empty args
- **If Step 3 extracts helper:** same file or `GenerationHandlerFinishWorkTest.kt`
  - [ ] `shouldBreakAfterToolExecution` true only when executed list contains `finish_work`

**Validation**

```bash
.\gradlew test --no-daemon
```

**Review gate:** `trellis-check` on new tests only (no device).

**Rollback:** remove test files.

---

### Step 3 — `GenerationHandler` loop break

**File:** `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`

- [ ] Import `FINISH_WORK_TOOL_NAME` (and helper if extracted)
- [ ] After merging `executedTools` into last assistant message and `emit(GenerationChunk.Messages(...))` (~L292–310), **before** next `stepIndex`:
  ```kotlin
  if (executedTools.any { it.toolName == FINISH_WORK_TOOL_NAME }) {
      Log.i(TAG, "generateText: finish_work executed, terminating tool loop")
      break
  }
  ```
- [ ] (Optional) `internal fun shouldBreakAfterToolExecution(executed: List<UIMessagePart.Tool>): Boolean` — use in handler + Step 2 tests

**Validation**

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
.\gradlew test --no-daemon
```

**Review gate:** `trellis-check` — confirm break runs only when `executedTools` non-empty path; no change to “no tools → break” (~L205–208).

**Rollback:** revert handler block + helper; keep `FinishWorkTool.kt` if re-trying later.

---

### Step 4 — Subagent tool injection

**File:** `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt`

- [ ] Import `createFinishWorkTool`
- [ ] End of `buildSubagentTools`: `return (withSpawn + createFinishWorkTool()).distinctBy { it.name }`

**Validation**

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
```

**Rollback:** restore `return withSpawn.distinctBy { it.name }`.

---

### Step 5 — Builtin subagent prompts

**File:** `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentRegistry.kt`

- [ ] Append to each builtin profile `systemPrompt` (explore / coder / reviewer):
  > When the task is complete, write your final summary in the assistant message, then call `finish_work` to stop.

**Validation:** compile (same as Step 4).

**Rollback:** revert prompt strings only.

---

### Step 6 — Root chat optional tool

**File:** `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

- [ ] In root tool `buildList` (~L730–743), when `assistant.enableSubagents` is true: `add(createFinishWorkTool())` (alongside subagent tool assembly)
- [ ] When `enableSubagents == false`: do **not** register `finish_work`

**Validation**

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
```

**Rollback:** remove `add(createFinishWorkTool())` from `ChatService`.

---

### Step 7 — `SubagentPermissionTest` coverage

**File:** `app/src/test/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionTest.kt`

- [ ] Assert built tool names include `finish_work` when `excludedTools` is set
- [ ] Assert `finish_work` present when `inheritTools = false` (workspace-only base per design)

**Validation**

```bash
.\gradlew test --no-daemon
```

**Review gate:** `trellis-check` full app test slice + map to PRD AC.

**Rollback:** revert test additions.

---

### Step 8 — Integration / manual QA (optional JVM harness)

- [ ] If existing `GenerationHandler` integration test harness exists: one-step mock with `finish_work` tool call → no second step
- [ ] Else: **manual** — trivial `explore` subagent; verify early stop, summary from assistant text, tool output `"Task completed."`

**Validation:** manual notes in task or journal.

---

### Step 9 — Ship verification

```bash
.\gradlew test --no-daemon
.\gradlew lint --no-daemon
adb devices
.\gradlew :app:installDebug --no-daemon
```

**Review gate:** final `trellis-check` against `prd.md` acceptance criteria.

**Rollback (full feature):** per `design.md` Rollback — remove injections, handler break, registry prompts, delete `FinishWorkTool.kt` + tests.

---

## PRD acceptance mapping

| AC | Step(s) |
|----|---------|
| Subagent `finish_work` available, no approval | 1, 4, 7 |
| Call terminates loop; summary = assistant text | 3, 8 |
| Output `"Task completed."` | 1, 2 |
| Builtin systemPrompt guidance | 5 |
| No `finish_work` → unchanged behavior | 3 (no break) |
| Root agent with `enableSubagents` | 6 |
| Existing tests pass | 2, 7, 9 |

## `implement.jsonl` (spec context for sub-agents)

After first implementation pass, add entries for any spec files read (e.g. `.trellis/spec/...`); delete `_example` line in `implement.jsonl` / `check.jsonl` when populated.