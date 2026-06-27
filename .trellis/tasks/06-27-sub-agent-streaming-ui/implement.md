# 子代理流式输出与think/summary块UI — 实施计划

## 执行顺序

### Phase 1: 数据模型 + 运行时基础设施

- [ ] **1.1** `SubagentProfile.kt` — 扩展 `SubagentTranscriptStep`
  - `Reasoning` 增加 `createdAt: Long? = null`
  - `ToolCall` 增加 `executed: Boolean = true`, `childTranscript: List<SubagentTranscriptStep>? = null`
  - 验证：`.\gradlew :ai:compileDebugKotlin` + 确认旧数据反序列化通过

- [ ] **1.2** `GenerationHandler.kt` — 增加 `ToolCallIdElement`
  - 添加 `ToolCallIdElement` class + `currentToolCallId()` + `withToolCallId()`
  - `executeSingleTool` L438: 包裹 `withToolCallId(tool.toolCallId) { toolDef.execute(args) }`
  - 验证：`.\gradlew :app:compileDebugKotlin`

- [ ] **1.3** `SubagentHost.kt` — 节流 onProgress
  - `runToCompletion` 中实现 120ms + signature throttle
  - 使用 `progressScope.launch` 异步发射
  - 验证：编译通过

### Phase 2: ChatService 接线

- [ ] **2.1** `ChatService.buildSubagentToolsForChat` — 传入 onProgress
  - 方法签名增加 `conversationId: Uuid?` 参数
  - `spawn` lambda 内读 `currentToolCallId()` + 构造 `onProgress` 回调
  - 嵌套 spawn（L1492–1525）同样处理
  - 调用处（L627–638）传入 `conversationId`
  - 验证：编译通过

- [ ] **2.2** `ChatService.updateSubagentProgress` — 新增方法
  - 签名：`private fun updateSubagentProgress(conversationId: Uuid, toolCallId: String?, profileName: String, subMessages: List<UIMessage>)`
  - 调用 `SubagentHost.buildTranscript(subMessages, truncateToolOutput = 2000)`
  - 构建 metadata JsonObject（5 keys）
  - 调用 `updateConversationState(conversationId)` 匹配并更新 tool part
  - 验证：编译通过 + 手动测试子代理执行有实时更新

- [ ] **2.3** `SubagentTools.kt` — 完成时写入 metadata
  - `execute` 返回 `listOf(UIMessagePart.Text(text = payload, metadata = finalMetadata))`
  - `finalMetadata` 包含完整 transcript + `subagent_streaming = false`
  - 验证：子代理完成后 UI 正确显示 summary + transcript

### Phase 3: UI 改造

- [ ] **3.1** `SubagentToolUIs.kt` — SpawnSubagentToolUI 支持流式渲染
  - 新增 `parseSubagentMetadata(context)` 读取 `metadata`
  - 流式态：标题动画 + 步骤时间线（从 metadata transcript 渲染）
  - 完成态：从 metadata 读 transcript 或回落 JSON 解析
  - 兼容旧消息：`metadata == null` 走现有路径
  - 验证：手动测试流式卡片 + 旧消息兼容

- [ ] **3.2** 流式步骤 UI 组件
  - Reasoning 步骤：Sparkles icon + "Thinking" 标签 + 可展开文本
  - ToolCall 步骤：executed=false 显示 pending；executed=true 正常显示
  - 文本步骤：直接展示
  - 验证：视觉审查

### Phase 4: ThinkTagTransformer 多块支持

- [ ] **4.1** `ThinkTagTransformer.kt` — 支持多 `考量...考量` 块
  - `THINKING_REGEX.find()` 改为 `THINKING_REGEX.findAll()`
  - 每个 match 生成独立 `UIMessagePart.Reasoning`
  - 流式中 `finishedAt = null` 直到闭合标签
  - 验证：多 think 块消息正确拆分显示

### Phase 5: 集成验证

- [ ] **5.1** 端到端手动测试
  - 单子代理流式执行
  - 并行子代理独立更新
  - 嵌套子代理（depth > 1）
  - 旧消息兼容
  - `streamOutput=false` 无回归
  - 验证：`.\gradlew :app:installDebug` + 真机测试

---

## Validation Commands

```bash
.\gradlew :ai:compileDebugKotlin          # Phase 1.1
.\gradlew :app:compileDebugKotlin         # Phase 1.2–2.3
.\gradlew :app:installDebug               # Phase 3–5
```

## Risk / Rollback Points

| Risk | Mitigation |
|------|------------|
| `updateSubagentProgress` 频繁调用导致 UI 卡顿 | 120ms throttle + async launch |
| 并行子代理 toolCallId 匹配错误 | `ToolCallIdElement` 在 `withContext` 中传递，与 coroutine 绑定 |
| 旧 `SubagentResult` JSON 无 metadata | 保留 fallback 解析路径 |
| metadata 被意外发送到 AI provider | `SubagentTools` 不读 metadata；provider 层序列化只取 `text` |

## Files to Modify

| File | Change |
|------|--------|
| `ai/.../SubagentProfile.kt` | `SubagentTranscriptStep` 新字段 |
| `app/.../GenerationHandler.kt` | `ToolCallIdElement` + `withToolCallId` |
| `app/.../SubagentHost.kt` | throttle onProgress |
| `app/.../SubagentTools.kt` | 完成 metadata |
| `app/.../ChatService.kt` | `updateSubagentProgress` + wiring |
| `app/.../SubagentToolUIs.kt` | 流式 UI |
| `app/.../ThinkTagTransformer.kt` | 多块支持 |
