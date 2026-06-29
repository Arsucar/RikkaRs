# Design: Fix critical data layer

## Changes

### D1: globalSubagentProfiles 持久化

**文件**: `PreferencesStore.kt`
- 在 `Keys` object 添加 `val GLOBAL_SUBAGENT_PROFILES = stringPreferencesKey("global_subagent_profiles")`
- 在 `settingsFlowRaw.map` 的 `Settings(...)` 构造中添加 `globalSubagentProfiles = JsonInstant.decodeFromString(preferences[GLOBAL_SUBAGENT_PROFILES] ?: "[]")`
- 在 `update` 方法的 write 路径添加 `preferences[GLOBAL_SUBAGENT_PROFILES] = JsonInstant.encodeToString(settings.globalSubagentProfiles)`
- **序列化兼容**: `SubagentProfile` 已有 `@Serializable`，默认值 `emptyList()` 对旧数据安全（字段缺失 → decodeFromString 为空 JSON 数组 `"[]"`）
- **新增 string keys 无 migration**: DataStore preferences 方案下旧版没有这个 key → fallback `"[]"` → emptyList

### D2: CAS 循环防活锁

**文件**: `ChatService.kt`
- `updateConversationState` 中 `while(true)` → `repeat(50)` 上限
- 重命名循环变量避免 shadow
- 超限 `Log.w(TAG, "CAS retry limit exceeded for conversation $conversationId")` 并 return

### D3: Streaming metadata 异常清理

**文件**: `ChatService.kt`
- 在 `handleMessageComplete` 结束时扫描最新 assistant message 中所有 `UIMessagePart.Tool`
- 对 `toolName == "spawn_subagent"` 且 `isStreamingSubagent(part) == true` 的 tool part：
  - 替换 output 中 text part 的 metadata：`subagent_streaming: false, subagent_succeeded: false`
- 用 `updateConversationState` CAS 写回

**备选方案**: 在 `GenerationHandler` tool execution onFailure 中标记 `subagent_streaming: false`——但这要求 GenerationHandler 知道 subagent 的语义，耦合度高。选 ChatService 扫描方案。

## Tradeoffs

- CAS 扫描方案在完成后会触发一次额外的 conversation state 更新，但仅在有残留 streaming 标记时
- 不修改 `SubagentTools.kt` 或 `SubagentHost.kt`——保持后端干净，只在 ChatService 层做清理
