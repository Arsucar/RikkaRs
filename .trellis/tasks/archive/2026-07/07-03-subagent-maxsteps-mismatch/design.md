# Design: fix subagent max_steps mismatch

## 涉及文件

| 文件 | 改动 |
|------|------|
| `app/.../subagent/SubagentProfile.kt` | maxSteps 改 nullable；mergeInheritedFrom 继承 maxSteps |
| `app/.../subagent/SubagentRegistry.kt` | resolveProfile 后无需改（merge 已塌缩）；确认内置值不变 |
| `app/.../subagent/SubagentHost.kt` | runToCompletion 计算 maxStepsReached；RunCompletion 加字段；buildFallbackSummary 加参数；spawnBody 汇总 truncated；maxSteps 使用处兜底 |
| `app/.../subagent/SubagentTools.kt` | slimPayload + finalMetadata 新增 max_steps/truncated；subagent_steps 改用 toolLoopSteps；applyPatch maxSteps 兼容 |
| `app/.../service/ChatService.kt` | 无需改（流式 subagent_steps 已是循环步数，语义一致） |
| `app/.../ui/.../SubagentToolUIs.kt` | 可选：移除 subagent_steps fallback；保留无害（两键已一致） |

## 设计决策

### D1: maxSteps 改 nullable（Int? = null）

**现状**：`maxSteps: Int = 32`，与 `maxToolCalls: Int? = null`、`temperature: Float? = null` 等可选覆盖字段不一致。非空默认 32 无法区分"用户显式设 32"和"未设置"。

**方案**：改为 `maxSteps: Int? = null`。null = 未设置，继承 base。

- `mergeInheritedFrom`：`maxSteps = maxSteps ?: base.maxSteps`
- 使用处 `profile.maxSteps.coerceIn(1, 256)` → `(profile.maxSteps ?: 32).coerceIn(1, 256)`
- 续写 `profile.copy(maxSteps = 1)` → 类型兼容（Int 字面量提升为 Int?），保持不变
- `applyPatch`：`maxSteps = int("max_steps") ?: maxSteps` → int() 返回 Int?，maxSteps 是 Int?，兼容；未传参时保持原值（null 或显式值）
- 内置 profile（SubagentRegistry）显式设 64/48/24，Int → Int? 兼容

**序列化兼容**：kotlinx.serialization 对 `Int? = null` 默认省略 null（`encodeDefaults` 视配置）。旧 DataStore JSON 无 maxSteps 字段 → 反序列化为 null → 继承 base。新数据 maxSteps=null 不序列化或序列化为 null，向后兼容。

### D2: maxSteps 用尽信号（不改 GenerationHandler）

**现状**：`GenerationHandler.generateText` 返回 `Flow<GenerationChunk>`，无 stop reason。循环 `for (stepIndex in 0 until maxSteps)`，跑满 maxSteps 轮后结束（最后一轮注入 MAX_STEPS_PROMPT 禁工具，产生 assistant 消息后无工具调用 break）。

**方案**：在 `runToCompletion` 中推断。循环每轮产生一个 assistant 消息。本次 run 的 assistant 增量 = `run.messages.count{ASSISTANT} - preAssistantCount`。若 `assistantDelta >= effectiveMaxSteps`，则跑满。

- `RunCompletion` 新增 `maxStepsReached: Boolean`
- `runToCompletion` 计算并填充
- 边界：finish_work 提前 break → assistantDelta < maxSteps → maxStepsReached=false（正确）
- 边界：无工具调用提前 break → assistantDelta < maxSteps → false（正确）

```kotlin
val effectiveMaxSteps = (profile.maxSteps ?: 32).coerceIn(1, 256)
// ... generateText ...
val assistantDelta = finalMessages.count { it.role == MessageRole.ASSISTANT } -
    initialMessages.count { it.role == MessageRole.ASSISTANT }
val maxStepsReached = assistantDelta >= effectiveMaxSteps
```

### D3: truncated 语义扩展

**现状**：`SubagentResult.truncated` 仅来自 `ToolCallBudgetStop`（maxToolCalls 用尽）。

**方案**：`truncated` 扩展为"因任一预算上限截断"。spawnBody 汇总：
```kotlin
truncated = truncated || run.truncated || run.maxStepsReached
```
不新增字段，符合 issue 期望（父模型看 truncated 即知是否撞上限）。UI 不读 truncated，无破坏。

### D4: buildFallbackSummary 文案

**现状**：`summary.ifBlank { buildFallbackSummary(transcript) }`，无条件"Max steps reached"。

**方案**：`buildFallbackSummary(transcript, maxStepsReached)`：
- maxStepsReached=true：保留"(Max steps reached — auto-generated summary from transcript)"
- maxStepsReached=false：改用"(Subagent produced no text summary — auto-generated from transcript)"
- 空 transcript：maxStepsReached 用"(subagent ran out of steps with no output)"，否则"(subagent produced no output)"

调用处：`summary.ifBlank { buildFallbackSummary(transcript, maxStepsReached) }`。需在 spawnBody 把 maxStepsReached 传到调用点。

### D5: slimPayload + finalMetadata 对齐

slimPayload 新增 + 移除：
```kotlin
put("max_steps", JsonPrimitive(effectiveMaxSteps))   // 生效上限
put("truncated", JsonPrimitive(result.truncated))     // 是否截断
// 移除 steps（续写段数）— 对父模型无决策价值，与 tool_loop_steps 并列易混淆
// 移除 tool_call_count — 程序内部计步统计，保留在 finalMetadata 供 UI，不暴露父模型
```
最终顺序：profile_name, summary, succeeded, error?, max_steps, tool_loop_steps, truncated, transcript_size, usage?
（移除 steps 和 tool_call_count — 前者续写段数无决策价值，后者作为程序内部计步统计保留在 finalMetadata 供 UI，不暴露给父模型）

finalMetadata 新增：
```kotlin
put("subagent_max_steps", JsonPrimitive(effectiveMaxSteps))
put("subagent_truncated", JsonPrimitive(result.truncated))
```

`effectiveMaxSteps` 需传入 SubagentTools.execute。spawn 闭包返回 SubagentResult，但 SubagentResult 不含 maxSteps。两个选择：
- A: SubagentResult 新增 `maxSteps: Int? = null`（生效上限），spawnBody 填充
- B: spawn 闭包外部再 resolveProfile 取 maxSteps

选 A：SubagentResult 新增 `maxSteps: Int? = null`（序列化兼容，默认 null）。spawnBody 用 resolved profile 的 `(profile.maxSteps ?: 32).coerceIn(1, 256)` 填充。SubagentTools 从 `result.maxSteps` 取。

### D6: subagent_steps 语义统一

最终结果 `subagent_steps` 从 `result.steps` 改为 `result.toolLoopSteps`：
```kotlin
put("subagent_steps", JsonPrimitive(result.toolLoopSteps))  // 循环步数，与流式一致
put("subagent_tool_loop_steps", JsonPrimitive(result.toolLoopSteps))
```
两键一致，UI fallback 不跳变。流式（ChatService）已是循环步数，无需改。

### D7: SubagentHost 续写段 maxSteps

`profile.copy(maxSteps = 1)` 续写段：effectiveMaxSteps=1，assistantDelta 通常=1 → maxStepsReached=true。但续写段不算"主任务跑满"。需在续写段不累加 maxStepsReached，或续写段 maxStepsReached 不汇入最终 truncated。

**方案**：仅主任务 run（第一次 runToCompletion）的 maxStepsReached 汇入。续写段（summary continuation）的 maxStepsReached 忽略（它本就是 maxSteps=1 的单轮续写，不算截断）。

```kotlin
// 主任务
var maxStepsReached = run.maxStepsReached
// 续写循环内
maxStepsReached = maxStepsReached  // 不更新，忽略续写段
// 最终
truncated = truncated || maxStepsReached
```

## 数据流

```
resolveProfile(name) → profile(maxSteps=null|显式)
  └ mergeInheritedFrom → maxSteps 塌缩为非空（继承 base）
spawnBody:
  effectiveMaxSteps = (profile.maxSteps ?: 32).coerceIn(1,256)
  runToCompletion(effectiveMaxSteps) → RunCompletion(maxStepsReached)
  续写段(忽略 maxStepsReached)
  SubagentResult(truncated = toolBudget || maxStepsReached, maxSteps = effectiveMaxSteps)
SubagentTools.execute:
  slimPayload(max_steps=result.maxSteps, truncated=result.truncated, tool_loop_steps, steps)
  finalMetadata(subagent_max_steps, subagent_truncated, subagent_steps=toolLoopSteps)
```

## 兼容性

- SubagentProfile.maxSteps: Int → Int?，JSON 向后兼容（旧数据无字段 → null）
- SubagentResult.maxSteps: 新增字段，默认 null，向后兼容
- SubagentResult.truncated: 语义扩展但字段不变，UI 不依赖，安全
- slimPayload: 新增字段，父模型向后兼容（多字段不影响解析）
- applyPatch: int("max_steps") ?: maxSteps 类型兼容

## 风险

- maxSteps 改 nullable 影响所有使用 `profile.maxSteps` 的地方，需全部兜底。grep 确认使用点。
- maxStepsReached 推断依赖"循环每轮产生一个 assistant 消息"假设。若 GenerationHandler 在某些路径不产生 assistant 消息（如纯错误），assistantDelta 可能 < maxSteps 即使跑满。需确认 generateInternal 总产生 assistant 消息（即使错误也应有 assistant 响应）。
