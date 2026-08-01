# bug(#201): 清理模式注入直接绑定残留

## Goal

关闭预设/世界书/记忆后 system 消息不再残留旧「模式注入」；聊天页扩展面板新增可见「独立注入」开关且立即生效（含对话级绑定）；迁移清理与已迁移 preset entries 重复的 `assistant.modeInjectionIds` 直连，消除双路径并存。

## Requirements

- **迁移清理**：settings flow 清理阶段（`PreferencesStore.kt:461-484`），对每个 assistant 的 `modeInjectionIds`，移除「其 id 同时是该 assistant 已绑定 preset 的 entry id」的直接绑定（#182 迁移后 `PresetEntry.Custom(id = injection.id, ...)` 保留全局 id，按 id 精确去重），消除双路径。
- **UI 开关**：`ExtensionSelector` 增加第 5 个 tab「独立注入」，复用 `ModeInjectionsContent`；`allowConversationPromptInjection` 时读写 `conversation.modeInjectionIds`（经 onUpdateConversation），否则读写 `assistant.modeInjectionIds`。
- **行为**：`PromptInjectionTransformer` 逻辑不变（直连是合法独立注入功能）；残留问题通过「迁移去重 + 可见开关」解决。
- **对话级**：`allowConversationPromptInjection=true` 时开关切换 conversation 绑定，关闭后下次发送生效。

## Acceptance Criteria

- [ ] 迁移后：assistant 直接绑定中「与已绑定 preset entries 同 id」的项被清除；不重复、不误删与 preset 无关的独立注入。
- [ ] `PresetEntriesMigrationPersistenceTest` 或新增迁移测试断言该清理。
- [ ] `ExtensionSelector` 有「独立注入」tab（5 tabs），关闭即清空/移除绑定。
- [ ] 默认（三项全关 + 无独立注入绑定）时 system 无残留注入（`PromptInjectionTransformerTest` 现有契约不回归）。
- [ ] 中/英文案（`extension_selector_tab_mode_injections` 等）。

## Notes

- 根因链：#73 `withDefaultPreset` 全量复制 → #182 `migratedWithEntries` 快照 entries 且清空 Preset 旧字段，但从不清理 `Assistant.modeInjectionIds` → `collectInjections`（`PromptInjectionTransformer.kt:95-125`）默认路径直连注入。
- 行为依赖面：`AssistantExtensionsPage.kt:239-243`（独立注入 UI）、`SettingsStore.updateAssistantInjections`、Web `SettingsRoutes.kt:103-123`、`Conversation.modeInjectionIds` 平行路径均保留。
