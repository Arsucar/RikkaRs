# issue-218 design

## 边界

| 层 | 改动 |
|----|------|
| UI | `ExtensionContent` 可选本地 optimistic；或保持纯派生、由上层 StateFlow 即时更新 |
| ChatPage / ChatVM | 预设 toggle 改调新 partial API，不再 `updateSettings(全量 copy)` |
| AssistantExtensionsPage / AssistantDetailVM | 次路径同步 |
| PreferencesStore | 新增 `updateAssistantPresetIds` 或 `toggleAssistantPreset(assistantId, presetId, enabled)` |

## 契约

### PreferencesStore（推荐）

```kotlin
suspend fun toggleAssistantPreset(
    assistantId: Uuid,
    presetId: Uuid,
    enabled: Boolean,
)
```

- `updateMutex.withLock` + `dataStore.edit` 内只改 assistants 列表中对应项的 `presetIds`
- 成功后 `settingsFlow.value = ...` 或 `syncSettingsFlowFromStore()`（与现有 partial 方法一致）
- **禁止**先 `settingsFlow.value.copy(...)` 再 `update(全量)` 无 mutex 的外部拼装

### UI 乐观策略（二选一，推荐 B）

| | 方案 A 本地 mutableStateOf | 方案 B VM/Store 即时更新 StateFlow |
|--|--|--|
| 即时性 | 强 | 强（若 value 在 edit 前/后立即赋） |
| 多入口同步 | 弱（需 key 重置） | 强 |
| 与 #202 | 可混用 | 更贴 partial 写 |

**推荐 B**：partial 方法内在 mutex 下先算 next Settings，`settingsFlow.value = next`（或 edit 成功后立刻赋值，现有 `updateUnlocked` 模式是 edit 成功后赋值——延迟来自 writeFullSettings 重编码。partial 只写少量 key 后延迟可接受；若仍需极致即时，可在 edit 前 `settingsFlow.value = optimistic` 再 edit，失败再 sync 回滚）。

对照现有 `updateAssistantWebSearch` 等实现选同一风格，避免两套语义。

### ChatPage 调用

```
onToggle(presetId, checked) →
  vm.toggleAssistantPreset(assistantId, presetId, checked)
```

删除对 `setting.copy(assistants = ...)` + `updateSettings` 的路径。

## 数据流

```
Switch onCheckedChange
  → ChatVM.toggleAssistantPreset
  → PreferencesStore.toggleAssistantPreset (mutex + partial edit + flow 更新)
  → settingsFlow 订阅方重组（仅依赖 assistant.presetIds 的组件）
```

## 取舍

- 是否改 ModeInjection/Lorebook 同类开关：同根因，**建议同 PR 一并**（同一 ExtensionContent 模式），验收写进 implement；若时间紧可只做 Presets 并在 PR 注明。
- ChatPage L134 全屏重组：先靠 partial 写降延迟；仍掉帧再开 follow-up。

## 兼容 / 回滚

- 无 schema 变更。
- 回滚：还原调用点 + 删除新 store 方法。

## 风险

- 乐观与磁盘不一致：失败必须 sync 回滚。
- 与 `updateAssistantConfig` 并发：统一走 `updateMutex`。
