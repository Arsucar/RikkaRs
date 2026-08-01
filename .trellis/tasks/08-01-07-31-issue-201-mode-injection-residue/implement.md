# 执行计划：清理模式注入直接绑定残留

## 步骤

1. **迁移去重**：`PreferencesStore.kt` settings flow assistant 过滤段（:461-484）加 boundPresetEntryIds 计算 + modeInjectionIds 去重 filter。
2. **UI tab**：`ExtensionSelector.kt` 5 tabs + 独立注入分支（复用 ModeInjectionsContent，对话级按 useConversationInjections）。
3. **文案**：`extension_selector_tab_mode_injections` 中/英/日 strings。
4. **测试**：
   - 迁移：`PresetEntriesMigrationPersistenceTest` 或新增——assistant 直连 id 与已绑定 preset entry id 相同时被清理；与 preset 无关直连保留；幂等。
   - transformer：现有 `PromptInjectionTransformerTest` 契约不回归（collectInjections 逻辑未改）。
   - 序列化：assistant 清理后 settings round-trip 正确。
5. 实现阶段不编译（最后一个检查子代理允许）。

## 验证

- 最终一次合并 Gradle：编译 + 相关 JVM 单测。
- 安装验收（父任务）。
