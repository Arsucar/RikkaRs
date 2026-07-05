# Implement: fix subagent max_steps mismatch

## 执行清单

### Step 1: SubagentProfile.kt — maxSteps 改 nullable + merge 继承
- [ ] `maxSteps: Int = 32` → `maxSteps: Int? = null`（line 69）
- [ ] `mergeInheritedFrom` 增加 `maxSteps = maxSteps ?: base.maxSteps`（line 189-195）
- [ ] SubagentResult 新增 `@SerialName("max_steps") val maxSteps: Int? = null`（line 100-114，放在 truncated 附近）

### Step 2: SubagentHost.kt — runToCompletion 信号 + fallback 文案
- [ ] `runToCompletion`：计算 `effectiveMaxSteps = (profile.maxSteps ?: 32).coerceIn(1, 256)`，替换 line 369 的 `profile.maxSteps.coerceIn(1, 256)`
- [ ] `runToCompletion`：generateText 结束后计算 `assistantDelta` 和 `maxStepsReached`
- [ ] `RunCompletion` data class 新增 `maxStepsReached: Boolean = false`（line 507-512）
- [ ] `RunCompletion` 返回处填充 `maxStepsReached`（line 394-399）
- [ ] `spawnBody`：主任务 run 后 `maxStepsReached = run.maxStepsReached`（line 195 附近）；续写段不更新 maxStepsReached
- [ ] `spawnBody`：最终 SubagentResult 的 `truncated = truncated || maxStepsReached`，新增 `maxSteps = effectiveMaxSteps`（line 247-258）
- [ ] `spawnBody`：失败路径 SubagentResult 也填 `maxSteps`（line 286-294，可选）
- [ ] `buildFallbackSummary` 签名改为 `(transcript, maxStepsReached)`，文案分支（line 572-599）
- [ ] `summary.ifBlank { buildFallbackSummary(transcript, maxStepsReached) }`（line 249）

### Step 3: SubagentTools.kt — slimPayload + metadata + subagent_steps
- [ ] slimPayload 新增 `max_steps`（line 125-135）：`put("max_steps", JsonPrimitive(result.maxSteps ?: 32))`
- [ ] slimPayload 新增 `truncated`：`put("truncated", JsonPrimitive(result.truncated))`
- [ ] slimPayload 移除 `steps`（line 130）和 `tool_call_count`（line 133）
- [ ] finalMetadata 新增 `subagent_max_steps`、`subagent_truncated`（line 115-124）
- [ ] finalMetadata `subagent_steps` 从 `result.steps` 改为 `result.toolLoopSteps`（line 118）
- [ ] finalMetadata 保留 `subagent_tool_calls`（UI 计步统计）
- [ ] applyPatch `maxSteps = int("max_steps") ?: maxSteps` 类型兼容确认（line 294）

### Step 4: SubagentHost.kt — askBtw maxSteps 兜底
- [ ] `askBtw` line 316 `maxSteps = 1` 不变（Int 字面量兼容 Int?）
- [ ] 确认无其他 `profile.maxSteps` 裸用未兜底

### Step 5: grep 验证使用点
- [ ] `grep -r "maxSteps" app/` 确认所有使用点已兜底 nullable
- [ ] 确认 SubagentRegistry 内置 profile 构造不受影响

### Step 6: 编译验证
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon`

### Step 7: 安装到设备
- [ ] `adb devices` 确认设备
- [ ] `./gradlew :app:installDebug --no-daemon`

## 验证命令

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

## Review Gates

- Step 1-2 完成后：确认 maxSteps nullable 不破坏序列化
- Step 3 完成后：确认 slimPayload 字段完整
- Step 6：编译通过

## Rollback

- git checkout 各文件，恢复 maxSteps: Int = 32
