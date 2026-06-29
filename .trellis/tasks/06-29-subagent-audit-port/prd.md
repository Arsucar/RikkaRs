# PRD: Port subagent tool-call auditing & explore prompt hardening

## 背景

sub 仓库（linklink256/rikkahub-sub）在 commit `0336558c` 做了"subagent 结构性只读 + 工具调用审计"重构。但分析后确认：我们 fork 的 subagent 权限模型已经走了一条**更先进**的路线（`WorkspaceAccess` 白名单：NONE/READ_ONLY/FULL，见 `SubagentProfile.kt:25-40` 与 `SubagentPermissionBuilder.kt`），sub 那套 `excludedTools` 黑名单 + `FILE_MUTATING_TOOLS` 常量对我们是**降级且冗余**的。

因此不做整体 cherry-pick，只挑选**与权限模型正交、纯增量**的两块改动回流。

## 范围

### In Scope（移植）

1. **`SubagentResult.toolCallCount` 审计字段**
   - `SubagentProfile.kt`：`SubagentResult` 新增 `@SerialName("tool_call_count") val toolCallCount: Int = 0`（向后兼容，默认 0）。
   - `SubagentHost.kt`：新增 `countToolCalls(messages)` 私有方法（统计 assistant 消息中 `UIMessagePart.Tool` 的 part 数），在 `spawn` 构造 `SubagentResult` 时调用。
   - `SubagentTools.kt`：在父代理可见的 `JsonObject` payload 里加 `put("tool_calls", JsonPrimitive(result.toolCallCount))`，让父代理能审计子代理是否真干了活。
   - **不传完整 transcript 给父代理**（保持 sub 的隔离原则：上下文成本 + prompt-injection 防护），只传 count。

2. **explore profile systemPrompt 强化**
   - `SubagentRegistry.kt` 的 `explore` profile systemPrompt 追加两段约束：
     - **"Verify, don't assert"**：报告根因前必须说明跑过什么验证命令/测试及其输出 —— 直击"假设包装成结论"的痛点。
   - **不照搬** sub 的 "Read-only discipline" 段：我们 `workspaceAccess = READ_ONLY` 已经在工具白名单层结构性地堵掉了写工具（`workspace_write_file` / `workspace_edit_file` 不在 READ_ONLY 工具集里），不需要再在 prompt 里说"NEVER run write/exec"。保留 `workspace_shell` 是因为 explore 强依赖 grep/cat/ls，其只读由现有机制保证。

### Out of Scope（明确不移植）

- `SubagentProfile.FILE_MUTATING_TOOLS` / `FULLY_READONLY_EXCLUDED_TOOLS` 命名常量 —— 与 `WorkspaceAccess` 白名单语义重复，引入会造成两套权限模型打架。
- `explore.excludedTools = FILE_MUTATING_TOOLS` —— 我们 explore 已是 `READ_ONLY`，结构性文件只读已成立。
- `reviewer.excludedTools = FULLY_READONLY_EXCLUDED_TOOLS` —— 我们 reviewer 已有等价 `excludedTools = {write, edit, shell}`（`SubagentRegistry.kt:49-53`）。
- sub 的 `SubagentProfileBuiltinTest`（测的是 sub 的常量契约，我们权限模型不同，测了也没意义；如需测试应另写基于 `WorkspaceAccess` 的契约测试）。
- sub 删除孤儿测试 `SubagentToolDigestTest` —— 我们 fork 没有这个测试文件。

## 验收标准

- [ ] `SubagentResult` 序列化/反序列化在无 `tool_call_count` 字段时向后兼容（旧对话数据不丢、不崩）。
- [ ] `SubagentHost.spawn` 正确统计并填充 `toolCallCount`（= 所有 assistant 消息中 `UIMessagePart.Tool` part 数之和）。
- [ ] 父代理收到的 subagent 工具结果 JSON 中包含 `tool_calls` 字段。
- [ ] explore systemPrompt 含 "Verify, don't assert" 段落，**不**含 sub 的 "Read-only discipline" 段落。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :app:testDebugUnitTest --no-daemon --tests "*subagent*"` 通过（不新增失败）。
- [ ] 安装到设备 `.\gradlew :app:installDebug --no-daemon` 成功，手动触发一次 explore subagent 调用确认 `tool_calls` 字段出现在父代理上下文。

## 约束

- 不改动 `WorkspaceAccess` / `WorkspaceApproval` / `SubagentPermissionBuilder` 的现有逻辑。
- 不改动 reviewer / coder profile。
- `countToolCalls` 实现须与 sub 一致（按 assistant 消息的 Tool part 计数），避免后续与上游/sub 对账时语义漂移。
