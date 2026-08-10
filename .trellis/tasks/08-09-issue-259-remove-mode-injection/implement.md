# Implement: feat(#259+#242) 删除独立注入

## 前置

- prd.md / design.md
- issue #259 正文；**忽略 #242 tombstone 为最终方案**
- 确认 #258 Room 版本是否已到 51（决定本任务 51→52 vs 协调）

## Checklist（按 design 删除顺序）

### 1. Reference→Custom 迁移（先于删类型）

- [ ] PreferencesStore / preset 加载路径：Reference 解引用 modeInjections → Custom 快照
- [ ] 无效 modeInjectionId 策略固定 + 测试
- [ ] 迁移后清理 `mode_injections` 读写路径准备
- [ ] `PresetEntriesMigrationPersistenceTest` 重写为 snapshot 迁移
- [ ] 备份恢复路径同步迁移（若有 SettingsJsonMigrator）

### 2. Transformer / 运行时

- [ ] `PromptInjectionTransformer` 仅 entries；删直连 step1
- [ ] 删 `conversationModeInjectionIds` 上下文与 GenerationHandler 透传
- [ ] ChatService fork/传参清理
- [ ] 删 `PromptInjection.ModeInjection`；内部解析类型
- [ ] SubagentHost 字段清理
- [ ] `AssistantExtensionIds` Reference / prune 面清理

**文件线索（#259）**: Transformer.kt, GenerationHandler.kt, ChatService.kt, SubagentHost.kt, AssistantExtensionIds.kt

### 3. 模型与 DataStore

- [ ] 删 Settings.modeInjections 及 key 全路径（sanitize/restore/defaults/updatePreset…）
- [ ] 删 Assistant 两字段；改 migratedWithEntries
- [ ] 删 Conversation.modeInjectionIds

### 4. Room

- [ ] 若 DB=51：`Migration_51_52` 删 conversation 列
- [ ] 若 DB=50 且 #258 未合：与 #258 协调，**禁止双重 50→51**
- [ ] AppDatabase + DataSourceModule
- [ ] ConversationEntity

### 5. UI

- [ ] ExtensionSelector 5→4；ModeInjectionsContent；ModeInjectionEditSheet 调用删除
- [ ] AssistantExtensionsPage 小节
- [ ] PromptPage 绑定/Reference 创建（:426-720 等）
- [ ] AssistantPromptPage 开关
- [ ] FilesPicker 计数
- [ ] PresetDetailPage / PresetEntryUi Reference UI
- [ ] 字符串 6 语言 + 死键 + empty_presets 文案

### 6. 删 PresetEntry.Reference 类型

- [ ] 迁移测试全绿后删除 Reference 类与 @SerialName("reference")
- [ ] ExportSerializer 分支
- [ ] 序列化测试更新

### 7. Web

- [ ] SettingsRoutes / ConversationRoutes / WebDto / ConversationDiff

### 8. 测试清理与强化

- [ ] 删 `ModeInjectionDirectBindingDedupeTest`
- [ ] 改 `PromptInjectionTransformerTest`（去直连，强 entries）
- [ ] 其余引用清理
- [ ] grep 零命中（迁移临时代码除外）

### 9. 验证门

- [ ] `.\gradlew --no-daemon` 相关 test + `:app:compileDebugKotlin`（最终检查子代理）
- [ ] 手动：扩展面板 4 tab；预设注入仍工作；升级夹具
- [ ] 有设备 installDebug
- [ ] 关闭 #259 与 #242 时中英评论说明 Reference→Custom

## 明确不做

- tombstone 空壳长期保留
- 静默丢弃有目标的 Reference 内容
- 单独再开 #242 实现

## Rollback

按 phase 提交则：先回滚 UI/Web，数据迁移提交需谨慎；生产已迁移用户回滚代码可能导致 Custom 重复但不应丢内容。
