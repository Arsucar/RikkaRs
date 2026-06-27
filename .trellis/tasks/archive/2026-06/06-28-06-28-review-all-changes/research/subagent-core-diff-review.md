# Research: Subagent 核心模块未提交改动代码审查

- **Query**: 审查 SubagentHost / Profile / Registry / Tools / SubagentModelTest 的 git diff（release/rikka-arsucar 工作区）
- **Scope**: internal（diff + 关联调用点 ChatService、PreferencesStore、migrate）
- **Date**: 2026-06-28

## 审查摘要

| 文件 | 风险 | 结论 |
|------|------|------|
| `SubagentHost.kt` | 中 | 进度节流与协程作用域有竞态/语义细节；生命周期与取消处理总体正确 |
| `SubagentProfile.kt` | 低~中 | 全局 profile 合并语义变更，调用方必须传入 global 列表 |
| `SubagentRegistry.kt` | 中 | 内置解析完全依赖 `globalProfiles` + 迁移；空 global 时行为变化 |
| `SubagentTools.kt` | 低 | metadata 增强合理；payload 与 metadata 重复 |
| `SubagentModelTest.kt` | 中 | 迁移测试到位；新字段与 Host 运行时行为覆盖不足 |

---

## 1. `SubagentHost.kt`

### 改动要点

- `resolveProfile` 增加 `settings.globalSubagentProfiles`。
- `runToCompletion`：`SupervisorJob` + `Dispatchers.IO` 的 `progressScope`，对 `onProgress` 做签名 + 120ms 节流，在 `finally` 中 `progressScope.cancel()`。
- `buildTranscript`：`Reasoning.createdAt`、`ToolCall.executed`、独立 `truncateToolOutput`。

### Issues / 建议

1. **进度回调与结构化并发（中）**  
   - 节流闭包内 `progressScope.launch { cb(messages) }` 会在 `fold` 结束、`finally` 取消作用域后仍可能有已调度任务执行。`cancel()` 不保证立刻打断正在运行的 `cb`。  
   - **建议**：在 `cb` 内检查 `progressScope.isActive`，或改用 `coroutineScope` + 单协程 `Channel` 串行投递，或在 `finally` 后 `join`/`cancelAndJoin`（若引入 Job 引用）。

2. **节流签名语义（低~中）**  
   - `signature = messages.sumOf { if (role==ASSISTANT) parts.size else 0 }` 在「同一 part 数、工具 output 流式更新」时可能不触发刷新，仅依赖 120ms 时间窗。  
   - **建议**：纳入 tool output 长度或最后一条 assistant 文本 hash（若 UI 需要更细粒度）。

3. **子代理生命周期（低，正面）**  
   - `spawn`：`depth >= maxDepth`、`profile == null` 早退；`buildChildTools` 失败降级 `emptyList()` 并打日志；`CancellationException` 重新抛出；其它异常 `getOrElse` 返回 `SubagentResult(succeeded=false)`。  
   - 每次 `runToCompletion` 新建 `progressScope` 并在 `finally` 取消，避免泄漏（continuation 循环会多次创建 scope，符合「单次 run」边界）。

4. **`executed` 字段语义（低）**  
   - `part.isExecuted` 在 `UIMessagePart.Tool` 中为 `output.isNotEmpty()`，不等于「工具执行成功」。未执行工具若已有空 output 可能为 false；与 transcript 展示「是否已有结果」一致，但与字面 executed 可能混淆。  
   - **建议**：文档或 UI 侧按「has output」理解，或未来区分 `failed`/`pending`。

5. **`createdAt`（低）**  
   - `part.createdAt.toEpochMilliseconds()` 与 `Reasoning` 的 `Instant` 类型一致；`SubagentTranscriptStep.Reasoning.createdAt` 为可空，序列化向后兼容。

6. **测试缺口（中）**  
   - 本 diff 未包含对节流、`progressScope` 清理、`truncateToolOutput` 的单元测试（`SubagentRuntimeTest` 仍只测默认 `buildTranscript`）。

---

## 2. `SubagentProfile.kt`

### 改动要点

- `SubagentTranscriptStep.Reasoning` 增加 `createdAt: Long? = null`。
- `SubagentTranscriptStep.ToolCall` 增加 `executed: Boolean = true`。
- `mergeSubagentProfiles(custom, global, disabledGlobal)`：不再直接合并 `SubagentRegistry.BUILTIN_PROFILES`，改为合并调用方传入的 `global`。

### Issues / 建议

1. **破坏性 API 语义（中）**  
   - 旧签名 `disabledBuiltin` 隐含内置列表；新签名若 `global = emptyList()` 则合并结果**不含** explore/coder/reviewer，除非 `custom` 覆盖。  
   - **现状**：`ChatService` 已传入 `settings.globalSubagentProfiles`；`SubagentRegistry.allProfiles` 同样传入 global。  
   - **建议**：在 `mergeSubagentProfiles` KDoc 中明确「global 通常来自 Settings（含迁移后的 builtin）」；对 `global.isEmpty()` 的调用路径做一次 repo 全量 grep（当前生产路径已对齐）。

2. **序列化兼容（低，正面）**  
   - 新字段均有默认值，旧 JSON round-trip 仍可通过（与 `SubagentModelTest` 中未带新字段的样例一致）。

---

## 3. `SubagentRegistry.kt`

### 改动要点

- 删除 `builtinByName`；`resolveProfile(name, assistant, globalProfiles)` 顺序：assistant 自定义 → `disabledGlobalSubagents` 拦截 global → `globalProfiles` 查找。
- `allProfiles` 委托新 `mergeSubagentProfiles`。

### Issues / 建议

1. **解析依赖迁移（中）**  
   - 未迁移且 `globalSubagentProfiles` 为空时，`resolveProfile("explore", assistant)` **返回 null**（除非 assistant 上有同名 custom）。  
   - **缓解**：`PreferencesStore` 在 settings flow 上 `map { migrateSubagentBuiltinsIfNeeded(settings) }`；测试覆盖迁移 idempotent。  
   - **建议**：确认 `init == true` 的 dummy Settings 路径不会在生产 spawn 前绕过迁移（当前 migrate 在 `init || subagentBuiltinMigrated` 时跳过，dummy 仅测试用）。

2. **`disabledGlobal` 与 custom（低，正面）**  
   - 自定义 `assistant.subagentProfiles` 同名优先返回，**不受** `disabledGlobalSubagents` 影响（与「用户显式覆盖」一致）。

3. **风格**  
   - 文件末尾缺少换行（diff 显示 `\ No newline at end of file`）。

4. **调用点一致性（低，正面）**  
   - `SubagentHost`、`ChatService`（spawn + nested）、`AssistantSubagentProfilePage` 均传入 `settings.globalSubagentProfiles`。

---

## 4. `SubagentTools.kt`

### 改动要点

- `spawn_subagent` 执行结果：`UIMessagePart.Text` 增加 `metadata`（`subagent_transcript`、`subagent_profile`、`subagent_steps`、`subagent_succeeded`、`subagent_streaming=false`）。

### Issues / 建议

1. **工具输出与 UI 契约（低，正面）**  
   - metadata 便于 Chat UI 展示 transcript，与 streaming 任务方向一致；`subagent_streaming = false` 明确终态。

2. **数据重复（低）**  
   - `text` 仍为完整 `SubagentResult` JSON，metadata 再编码一遍 transcript。体积与序列化成本略增。  
   - **建议**：若 UI 只读 metadata，可考虑缩短 text 或约定单一来源（属产品决策，非 blocker）。

3. **类型安全（低）**  
   - `ListSerializer(SubagentTranscriptStep.serializer())` 每次 execute 新建，可提为 `companion` 常量。

4. **错误处理**  
   - `spawn` lambda 由 `ChatService` 提供，profile null 时返回失败 `SubagentResult`；本文件未改 spawn 签名，流程与 Host 一致。

---

## 5. `SubagentModelTest.kt`

### 改动要点

- Registry 测试改为显式传入 `global`（`BUILTIN_PROFILES` 作 global 列表）。
- 新增 `migrateSubagentBuiltinsIfNeeded` 多场景测试。

### Issues / 建议

1. **迁移测试（低，正面）**  
   - 覆盖：复制 builtin、disabledBuiltin → disabledGlobal、幂等、保留已有 disabledGlobal、已迁移跳过。与 `PreferencesStore` 实现一致。

2. **新 transcript 字段覆盖不足（中）**  
   - `subagentResult_roundTrip` / `subagentTranscriptStep_polymorphicRoundTrip` 未断言 `createdAt`、`executed`；默认值使测试仍绿，但回归对新字段无效。  
   - **建议**：增加带 `createdAt`、非默认 `executed` 的 round-trip；可选 `buildTranscript` 对 `truncateToolOutput` 的断言（可放在 `SubagentRuntimeTest`）。

3. **`resolveProfile` 边界（中）**  
   - 缺少：`globalProfiles = emptyList()` 且未迁移场景、`disabledGlobal` 不屏蔽 assistant custom 的用例。

4. **测试归属**  
   - 迁移测试放在 `SubagentModelTest` 可接受；若 datastore 层另有测试文件，可考虑避免重复。

5. **文件末尾**  
   - 同样无 trailing newline。

---

## 关联代码（未在 diff 内，审查上下文）

| 路径 | 说明 |
|------|------|
| `PreferencesStore.kt` `migrateSubagentBuiltinsIfNeeded` | 与 Registry 新语义配套；合并 disabled 到 `disabledGlobalSubagents` |
| `ChatService.kt` | `mergeSubagentProfiles` / `resolveProfile` 已传 global；`onProgress` → `updateSubagentProgress` |
| `SubagentRuntimeTest.kt` | 未随 diff 更新以覆盖新 transcript 字段 |

---

## Caveats / Not Found

- 未审查 `GenerationHandler` 取消传播是否与 `progressScope` 交互（超出指定文件范围）。
- 未运行 `./gradlew test` 验证本 diff 下测试是否全部通过。