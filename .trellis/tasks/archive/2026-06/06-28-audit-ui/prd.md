# UI 一致性修复（夜间审计批次 D）

## Goal

修复夜间审计（`06-28-nightly-audit`）中 **批次 D：UI 一致性** 范围内的 P1/P2 项：ModelList 与 provider tag 心智一致、助手扩展绑定在全局删除后不残留孤立 ID、`ThinkTagTransformer` 多块解析可回归、子代理/设置相关图标补齐 TalkBack `contentDescription`。

**不在本任务范围**：P0、ChatService stale streaming、Subagent `remember(context.tool)`、title/suggestion 回退文案（E-P2-1/2）、tag 大小写（E-P2-3）、ModelList 空分组（E-P2-5）。

## 背景与来源

- 总报告：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`（批次 D #11–#13）
- 研究：`research/subagent-b-ui.md`、`research/subagent-e-provider-ext.md`

## Requirements

### FR-1（P1）ModelList：tag 筛选时收藏区与 tag 联动

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`：`favoriteModels` 计算约 **322–331**；`tagFilteredProviders` 约 **339–342** |
| **现状** | 选中 `selectedModelListTag` 后，下方 provider 列表经 `tagFilteredProviders` 过滤，但 `favoriteModels` 仅按 `modelType` + 全局收藏解析，**不**要求所属 provider 满足 `tags.contains(selectedModelListTag)`。 |
| **需改** | 当 `selectedModelListTag != null` 时，收藏条目仅保留「解析出的 `provider` 属于当前 tag 过滤结果」的项（与 `tagFilteredProviders` 同一规则：`provider.tags.contains(selectedModelListTag)`）。`selectedModelListTag == null` 时行为与现网一致。 |
| **验收标准** | 1）某 provider 打 tag `A`，收藏其下模型；在 ModelList 选 tag `B` 时，该收藏**不出现**；选 tag `A` 时出现。2）清除 tag 筛选后收藏恢复全量（仍受 `modelType` 约束）。3）收藏拖拽排序逻辑不因过滤而抛异常或写回非法 ID。 |

### FR-2（P1）助手扩展 ID：全局删除后 prune 孤立绑定

| 项 | 说明 |
|----|------|
| **位置** | `AssistantExtensionsPage.kt` **112–173**（toggle 写回）；`AssistantDetailVM.kt` **163–178**（`update` 直写助手，无 ID 校验）；对照 **`PreferencesStore.kt` 351–361**（`settingsFlow` 映射层已 filter）、**`QuickMessagesVM.kt` 44–56**（删除快捷消息时主动 prune 全助手） |
| **现状** | Web `SettingsRoutes` 更新助手会拒绝未知 ID；原生扩展页只展示仍存在的全局项，但 **在扩展列表删除 mode injection / lorebook（及未走 prune 的路径）时**，助手上的 `modeInjectionIds` / `lorebookIds` / `quickMessageIds` 可能残留。`settingsFlow` 仅在**读 settings 流**时清理，不保证删除写盘路径与 QuickMessages 一致。 |
| **需改** | 对齐 `QuickMessagesVM.updateQuickMessages`：在 **删除** `modeInjections` / `lorebooks` / `quickMessages`（及批量更新列表）的写 `settingsStore.update` 路径中，对所有 `assistants` 将三类 ID 集合与新的有效 ID 集求交并写回。可选：在 `AssistantDetailVM.update` 对三类 ID 做防御性 filter（与当前 `settings` 全局列表一致），避免 UI toggle 写入已删 ID。 |
| **验收标准** | 1）助手已绑定某 quick message / mode injection / lorebook；在扩展管理页删除该全局项并保存 settings 后，打开该助手扩展 Tab，`selectedIds` 不含已删 ID；导出/重启后 `Assistant` JSON 仍干净。2）行为与 Web API 校验语义一致（无未知 ID）。3）不破坏 `PreferencesStore` 现有 `settingsFlow` 清理逻辑。 |

### FR-3（P1）ThinkTagTransformer：多块 think 解析单测

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformer.kt` **10–46**（`THINKING_REGEX`、`splitThinkTaggedText`）；**`visualTransform` / `onGenerationFinish` 49–99** |
| **现状** | 仓库内 **无** `ThinkTagTransformerTest`；多块、`</think>` 未闭合流式块、`finishedAt` 依赖 `onGenerationFinish` 等边界仅靠手工回归。 |
| **需改** | 在 `app/src/test/...` 新增 JVM 单测（可测 `splitThinkTaggedText` 若抽为 `internal`/`@VisibleForTesting`，或通过 `visualTransform`/`onGenerationFinish` 公开入口）。覆盖至少：**单块闭合**、**多块交替 Text/Reasoning**、**仅开头 `<think>` 无闭合尾段**、**无 think 标签纯文本**、**onGenerationFinish 为未闭合块补 `finishedAt`**。 |
| **验收标准** | 1）`./gradlew :app:test`（或模块级 test 任务）新增用例全部通过。2）用例断言 part 序列类型与关键 `Reasoning.finishedAt` 语义，而非仅快照整段字符串。3）文档/注释中注明：子代理转录路径**不一定**经过本 transformer（不强制改子代理，但测试锁定主聊天 ASSISTANT 文本契约）。 |

### FR-4（P2）SubagentToolUIs：步骤图标 contentDescription

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt`：**381**（ToolCall 行内 Icon）、**435**（Reasoning CoT）、**466**（ToolCall CoT）、**520**、**550**（Text/Summary CoT） |
| **现状** | 上述 `Icon(..., contentDescription = null)`；相邻 `Text`/`stringResource` 有部分语义，图标单独不可聚焦朗读。 |
| **需改** | 为每类步骤图标设置 `contentDescription = stringResource(...)`：优先复用已有 **`subagent_step_thinking`**、**`subagent_step_text`**；工具调用可用 **`subagent_tool_ui_tool_call`** 的简短语义或新增专用 a11y key（若现有字符串含过长占位符则新增 `subagent_step_tool` 类 key，并补 `values/strings.xml`，非英文 locale 可后续 locale-tui，本任务至少 en + zh）。 |
| **验收标准** | 1）TalkBack 聚焦步骤图标能读出非空描述（与步骤类型一致）。2）不改变现有视觉布局与流式行为。 |

### FR-5（P2）AssistantSubagentPage：操作图标 contentDescription

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantSubagentPage.kt`：**142, 146, 230, 241, 264, 267** |
| **现状** | Refresh / Add / Delete / Copy 等 `Icon(..., contentDescription = null)`。 |
| **需改** | 使用已有或新增字符串：`common_refresh`、`common_delete`、`common_copy`；Add 使用项目内既有 add 文案（如 `extensions_page_add_subagent` 或 `common_add` 若存在），与 `PropertyEditor` / 其他设置页模式一致。 |
| **验收标准** | 六个图标位均有本地化 `contentDescription`；按钮 `onClick` 行为不变。 |

### FR-6（P2）ExtensionSubagentsPage：操作图标 contentDescription

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionSubagentsPage.kt`：**77, 80, 139, 142** |
| **现状** | Add / More / Navigate / Delete 为 `contentDescription = null`。 |
| **需改** | Add → 与扩展子代理文案一致；More → **`skills_page_more_actions`** 或等价；Navigate → 进入详情（可复用 `common_open` 或新增 `extensions_subagents_open`）；Delete → **`common_delete`**。 |
| **验收标准** | 四处图标 TalkBack 可读；与 FR-5 用语风格一致。 |

### FR-7（P2）SettingProviderDetailPage：标签删除 chip 无障碍

| 项 | 说明 |
|----|------|
| **位置** | `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` **308–329**，尤其 trailing `IconButton` 内 Icon **324** |
| **现状** | 删除 tag 的 `HugeIcons.Cancel01` 为 `contentDescription = null`。 |
| **需改** | 为删除按钮设置 `contentDescription`（如 `stringResource(R.string.common_delete)` 或带 tag 上下文的 `provider_tag_remove` 新 key + `values/strings.xml`）。 |
| **验收标准** | 聚焦删除控件时 TalkBack 宣布删除语义；点击仍仅从 `internalProvider.tags` 移除对应 tag，保存流不变。 |

## 约束

- 仅改 PRD 所列文件及必要 `strings.xml` / 测试文件；不扩 scope 到 ImgGen i18n、LogPage、Web JWT 等其它审计项。
- 新增 string key 时遵循仓库命名前缀习惯；用户未要求时 **不** 强制跑全量 locale-tui，但 en 默认 `values/strings.xml` 必须完整。
- 完成 app 模块改动后按 `AGENTS.md` 做 `:app:installDebug` 真机验收（有设备时）。

## Acceptance Criteria（汇总）

- [ ] **P1** ModelList tag 与收藏联动（FR-1）
- [ ] **P1** 全局扩展删除后助手 ID 无残留（FR-2）
- [ ] **P1** ThinkTagTransformer 单测覆盖多块与未闭合场景（FR-3）
- [ ] **P2** SubagentToolUIs 步骤图标 a11y（FR-4）
- [ ] **P2** AssistantSubagentPage 操作图标 a11y（FR-5）
- [ ] **P2** ExtensionSubagentsPage 操作图标 a11y（FR-6）
- [ ] **P2** SettingProviderDetailPage 标签删除 a11y（FR-7）
- [ ] 相关单元测试通过；无新增 Lint 错误（`./gradlew :app:test` + 视情况 `lint`）

## Notes

- `PreferencesStore` 已在 `settingsFlow` 映射中 filter 扩展 ID；FR-2 重点是 **写路径** 与 QuickMessages 删除行为对齐，避免仅依赖惰性读流清理。
- 子代理 B 中 P1 `remember(context.tool)`、P1 stale streaming 归属其它子任务（如 `06-28-audit-state`），本 PRD 不重复立项。
- 复杂实现（扩展 prune 抽公共函数、ThinkTag 可测性重构）可在 `design.md` / `implement.md` 中展开后再 `task.py start`。