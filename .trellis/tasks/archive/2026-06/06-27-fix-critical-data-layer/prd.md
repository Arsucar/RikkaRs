# Fix critical data layer: persistence + CAS + streaming cleanup

## Parent

`06-27-fix-modellist-subagent-prod`

## Goal

修复 3 个 Critical/Major 数据层问题，确保子代理配置持久化、并发安全、流式状态正确。

## Requirements

### R1: globalSubagentProfiles 持久化
- 在 `PreferencesStore.Keys` 中添加 `GLOBAL_SUBAGENT_PROFILES = stringPreferencesKey("global_subagent_profiles")`
- 在 `settingsFlowRaw.map` 读取：`globalSubagentProfiles = JsonInstant.decodeFromString(preferences[GLOBAL_SUBAGENT_PROFILES] ?: "[]")`
- 在 write 路径写入：`preferences[GLOBAL_SUBAGENT_PROFILES] = JsonInstant.encodeToString(settings.globalSubagentProfiles)`

### R2: CAS 循环防活锁
- `ChatService.updateConversationState` 中 `while(true)` 改为 `repeat(MAX_CAS_RETRIES)` 上限 50 次
- 超限时 Log.w 并返回，不抛异常

### R3: 子代理异常时 streaming metadata 清理
- `GenerationHandler` 中 tool 执行 onFailure 路径：对 `spawn_subagent` 工具，检查 output metadata 中 `subagent_streaming == true`，如果是则替换为 `subagent_streaming: false, subagent_succeeded: false`
- 或：在 `ChatService` 的 `handleMessageComplete` 结束时扫描最新 assistant message，清理残留 `subagent_streaming: true`

## Acceptance Criteria

- [ ] AC1: App 重启后 `globalSubagentProfiles` 配置仍在
- [ ] AC2: `updateConversationState` CAS 重试不超过 50 次
- [ ] AC3: 子代理崩溃/cancel 后 UI 不永久显示 spinner
