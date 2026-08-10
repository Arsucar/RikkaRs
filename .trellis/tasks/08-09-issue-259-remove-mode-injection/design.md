# Design: feat(#259+#242) 删除独立注入 + Reference→Custom

## 权威关系

| 来源 | 地位 |
|---|---|
| #259 | 功能与 AC 权威 |
| #242 | **已推翻** tombstone；仅历史范围参考 |
| 父 PRD | #259 覆盖 #242；Reference→Custom |

## 边界

| In | Out |
|---|---|
| 删 mode injection 全链路 | 删 RegexInjection / 世界书 |
| Reference→Custom 一次性迁移 | tombstone 空壳长期保留 |
| Room 删 conversation 列 | 改 #258 workspaces 列语义 |

## 删除顺序（推荐，降低半残编译窗口）

### Phase D1 — 迁移逻辑先于删除类型

1. 在 PreferencesStore 加载/迁移路径实现 **Reference→Custom**：
   - 对每个 preset entry，若为 Reference：查 `modeInjections` 目标，复制 content/position/role/depth 等为 Custom
   - 目标缺失：跳过或落空 Custom 策略需固定（建议：丢弃该 entry 并 log，或保留 disabled Custom 占位——**优先丢弃无效引用并测试锁定**）
2. 迁移完成后写回 presets，再清除 `mode_injections` key（或随后删除读写使 key 自然消失）
3. 单测锁定：含 Reference 的 fixture → 加载后仅 Custom，内容等效

### Phase D2 — 执行链路改仅 entries

1. `PromptInjectionTransformer.collectInjections`：删除 step1 直连（约 :96-133）
2. 删除 `TransformerContext.conversationModeInjectionIds` 与 GenerationHandler 透传（约 8 处 :118-619）
3. ChatService 传参 / fork 复制清理（:927/:1876/:3030-3040）
4. `resolvePresetEntry` 对 `PromptInjection.ModeInjection` 依赖改为内部类型；删除 `ModeInjection` 子类
5. SubagentHost 构造 Assistant 去掉 2 绑定字段（:788-790）

### Phase D3 — 数据模型字段删除

1. `Settings.modeInjections` + DataStore key 读写/sanitize/restore/updatePresetInjection/withModeInjectionsPreservingPresetSnapshots/DEFAULT_MODE_INJECTIONS
2. `Assistant.modeInjectionIds` / `allowConversationPromptInjection`；`migratedWithEntries` 去全局快照依赖（Assistant.kt:288-340）
3. `Conversation.modeInjectionIds` + **Room migration 删列**

### Phase D4 — Room 版本

| 条件 | Migration |
|---|---|
| #258 已落地 version=51 | **Migration_51_52**：`ConversationEntity` 删 `mode_injection_ids`（或等价列名） |
| #258 未落地仍 version=50 | 可暂用 50→51，但 **合并时必须与 #258 协调**，禁止两迁移同称 50→51 |

父任务顺序：**#258 先** → 本任务用 **51→52**。

文件：`ConversationEntity`, `AppDatabase`, `DataSourceModule`, `migrations/Migration_51_52.kt`（名称随实际版本）

### Phase D5 — UI 删除

| 区域 | 文件/点 |
|---|---|
| 扩展面板 tab | ExtensionSelector tab4、pager 5→4、ModeInjectionsContent、ModeInjectionEditSheet |
| 助手扩展 | AssistantExtensionsPage 独立注入小节 |
| PromptPage | 绑定列表/新建 Reference/编辑 sheet（:426-720 段） |
| 助手提示词 | AssistantPromptPage 开关 :244-248 |
| FilesPicker | 注入计数 :175-178 |
| Preset UI | PresetDetailPage Reference 选择器/校验；PresetEntryUi Reference 分支 |
| 字符串 | 8 在用键 ×6 语言；死键清理；empty_presets 改写 |

### Phase D6 — Web

- `SettingsRoutes` POST assistant injections
- `ConversationRoutes` injections + 消息初始注入
- ConversationDiff；WebDto modeInjectionIds 字段

### Phase D7 — 测试

| 动作 | 文件 |
|---|---|
| 整删 | `ModeInjectionDirectBindingDedupeTest.kt` |
| 删直连用例、强化 entries | `PromptInjectionTransformerTest.kt` |
| 按 Reference→Custom 重写 | `PresetEntriesMigrationPersistenceTest.kt` |
| 清引用 | `PresetEntrySerializationTest`, `DraftContextConfigTest`, `ConversationRepositorySyncOpsTest`, `PresetEntryUiTest` 等 |
| 新增 | Reference→Custom 升级路径 + 备份恢复 ignoreUnknownKeys/迁移 |

### Phase D8 — 导出

- `ExportSerializer` Reference remap 删除；导出不再产出 Reference

## 数据流（目标）

```text
Preset entries (Builtin | Custom only)
  → PromptInjectionTransformer entries expand
  → request messages
```

无 modeInjections 全局表、无 assistant/conversation modeInjectionIds。

## 兼容

1. 旧 settings JSON 含未知 key：ignoreUnknownKeys
2. sealed Reference discriminator：必须先迁移再删类，否则 SerializationException（#242 已警告）
3. 备份恢复：迁移函数须在 restore 路径同样执行（SettingsJsonMigrator / BackupRestorer 镜像若存在）
4. encodeDefaults 全量写盘会抹旧 key——迁移须在首次写盘前完成

## 风险

| 风险 | 缓解 |
|---|---|
| 漏删编译点 ~20 文件 | 全仓 grep 清单；CI compile |
| Reference 内容丢失 | 禁止 tombstone/丢弃；迁移测试 |
| 与 #258 迁移号冲突 | 本设计默认 51→52 |
| Web 客户端破坏 | 发版说明 + 同步改 web-ui 若同仓 |

## 回滚

高风险大删除；应用 git revert 整提交序列。迁移向前；不提供 down migration。
