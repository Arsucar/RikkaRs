# Technical Design：#182 预设可编辑条目 —— 审查修复

> 代码锚点截至 release/rikka-arsucar 工作树（#182 实现已落地、未提交）。实现子代理须自行 grep 复核行号/签名后再改。
> 本任务修复的是**已落地实现**中的缺陷，不是从零设计特性；原始特性 spec 见 `07-25-issue-182-preset-editable-entries/`。

## 核心设计决定：Builtin 条目全部 config-only（注入路径跳过）

用户澄清：**预设是让用户显式化、集中维护自己配置的地方**，不是单纯的「注入内容容器」。
因此 `BuiltinPromptRegistry` 里注册的内置提示词**都应保留为可查看/可编辑的配置项**。

**最终架构定调（2026-07-27，后续用户决定覆盖早期“不接线”方案）**：

- `TransformerContext` 持有 `assistant`、`settings` 和 `workspaceCwd`，专用 transformer 可读取当前助手关联的 preset。
- 4 个模板全部 `injectable=false`，`PromptInjectionTransformer` 不把它们注入普通对话。
- config-only 的覆盖通过共享纯函数 `resolveBuiltinOverride` 解析，由各专用消费点使用；宏和动态数据仍由消费点负责。
- 不改变 transformer pipeline 顺序，也不把 workspace/memory 运行时数据塞进通用 `PromptInjectionTransformer`。

**实现方式**：给 `BuiltinPromptDef` 增加显式标记（建议 `val injectable: Boolean = false`），当前 4 个 key 全部 `injectable=false`。
`resolvePresetEntry` 对 config-only 的 Builtin 返回 null（不注入、不报错）；UI 用该标记显示「配置项/不注入对话」徽标。
保留 `injectable` 维度（而非直接对所有 Builtin 返回 null），是为将来真正接线可注入型时留出扩展点，不必回改 sealed 结构。

> 注意：这与原始 spec 的 AC4 字面（"回复草稿、建议回复……可在预设中引用"）冲突。
> 以用户澄清为准：**保留为可编辑配置**，但**全部不注入对话**。prd.md 已记录此偏离。
>
专用消费契约：

- `reply_draft`：preset override 作为 `buildInputDraftPrompt` 的模板；无覆盖回退默认常量。
- `suggestion`：启用且非空的 preset override 优先于 `settings.suggestionPrompt`。
- `workspace_guide`：覆盖模板在 `WorkspaceReminderTransformer` 内替换 `workspace_name` / `cwd`。
- `memory_table_guide`：覆盖只控制引导文案；`memory_tables` 数据块实时生成，模板无宏时也追加数据块。

## 迁移修复（保守：锁定不回归）

修复目标：惰性迁移后，现有用户的注入行为**完全不变**。涉及 4 个缺陷：

1. **重复注入**（`PromptInjectionTransformer.kt:138` step1b vs step1）：
   迁移后 Custom 快照 id 与全局直连绑定 id 相同，两条路径各注入一次。
   修复：collectInjections 对 entries 路径产出的 injection 与 step1 的直连集合**按 id 去重**
   （或迁移时排除已被 assistant 直连绑定的 id——但迁移在 Preset 层不知道 assistant，故去重放在 collectInjections 更内聚）。
   保留旧语义：同一 id 只注入一次。

2. **丢弃全局 enabled**（`Assistant.kt:293`）：`migratedWithEntries` 只按 `disabledEntryIds` 设 enabled。
   旧路径 `filter { it.enabled && ... }` 会跳过全局关闭项。
   修复：迁移时 `enabled = (id !in disabledEntryIds) && injection.enabled`，锁定旧行为。
   （注意快照语义：迁移后脱钩全局，此处只锁定迁移当刻的 enabled 状态。）

3. **priority→order 排序翻转**（`PromptInjectionTransformer.kt:207` `priority = -order`）：
   迁移条目 priority 被压到 ≤0，与直连注入/lorebook 的真实 priority 混排时顺序翻转。
   修复方向（保守）：迁移时保留原始 priority 参与全局排序，或让 resolvePresetEntry 对**迁移来源**的 Custom
   用其原 priority 而非 -order。需要一个能区分「迁移快照」与「用户新建条目」的信号
   （如 Custom 增加可选 `legacyPriority: Int? = null`，迁移写入，resolvePresetEntry 优先用它；
   新建条目为 null 时才用 -order）。评估序列化兼容（新增可空字段，默认 null，向后兼容）。
   **实现子代理需补注入顺序单测锁定混合来源场景**（preset 迁移条目 + assistant 直连 + lorebook 同 position）。

4. **迁移结果从不持久化 + 有效 id 过滤先于迁移**（`PreferencesStore.kt:472-477`）：
   `migratedWithEntries` 在读取 flow 的 normalizer 里计算但不写回，每次 emission 重跑；
   且 `modeInjectionIds.filter { valid }` 在 `.migratedWithEntries()` 之前，全局注入被删后先过滤再快照→内容永久丢失。
   修复：
   - 调整顺序——**先迁移快照（用未过滤的 modeInjectionIds 命中全局内容），再对残留旧字段做 valid 过滤**；
     快照一旦生成即脱钩，后续删全局不影响已快照内容。
   - 参考 `scheduleSubagentBuiltinMigrationPersist`（`PreferencesStore.kt:502`）模式，
     迁移发生后调度一次性持久化（幂等、AtomicBoolean 去重），避免每次 emission 重算。

## UI 修复

5. **PresetCard / ExtensionContent 统计对 entries-only 预设显示 0**
   （`PromptPage.kt:312/377/392`、`ExtensionContent.kt:72`、`PromptPage.kt:514` enabledInPreset）：
   `effectiveInjectionIds()` / `modeInjectionIds` 对新模型恒空。
   修复：给 `Preset` 加一个统一的「展示用条目数/名称」入口，`hasEntries()` 时用 `entries`（enabled 计数 + 名称），
   否则回退旧 `effectiveInjectionIds()`。所有三个展示点改用它。名称对 Builtin 用 key（或 displayName），
   Custom 用 name，Reference 解析全局名。

6. **Reference 编辑弹窗对失效引用默认选中第一项**（`PresetDetailPage.kt:542`）：
   `firstOrNull{id==...} ?: modeInjections.first()` 让 UI 显示首项为已选，draft 仍留死 id，确认后静默不注入。
   修复：失效时**不伪装选中**——显示未选中/占位提示（如「引用已失效，请重新选择」），
   或把 draft 的 modeInjectionId 同步为实际展示项。确认按钮在 modeInjectionId 无效时禁用或标警。

7. **切换 builtinKey 保留旧 key 的 override**（`PresetDetailPage.kt:479`）：
   `copy(builtinKey = it)` 不重置 `overrideContent`/`overridePosition`，导致新 key 注入旧文本。
   修复：切换 key 时重置 `overrideContent = null`、`overridePosition = null`
   （并可用新 def 的 defaultRole/defaultPosition 重置 position/role）。

8. **moveInGroup 破坏跨组 order 相等条目的注入顺序**（`PresetDetailPage.kt:162`）：
   `others + renumbered` 把整组挪到 `entries` 末尾，改变全局稳定排序的 tie-break。
   根因：UI 每组独立 0..n 编号 order，transformer 却按单一全局 `-order` 排序，跨组 order 冲突。
   修复方向（择一，实现子代理评估）：
   - (a) 让注入排序在**组内**进行（按 position 分组后再按 entry 在其类型组内的 order），跨组不比较 order；或
   - (b) moveInGroup 保持 `entries` 列表相对顺序稳定（只在原位置重排该组元素，不移动到末尾）。
   推荐 (b)：`preset.entries.map { 若属于该组则取 renumbered 对应项，否则原样 }`，保持非目标条目位置不变。

9. **最终交互范围**：
   - Builtin 新增菜单排除已占用 key；编辑选择器保留当前 key并排除兄弟条目占用 key。
   - 使用 `sh.calvin.reorderable` 做同类型组内拖拽；跨组拖拽 no-op。
   - 详情页隐藏 Reference 区，但不删除模型、已有数据或 PromptPage 旧入口。
   - 编辑/删除进入溢出菜单；删除先按目标 ID/名称二次确认；标签使用可换行布局，避免窄屏横向挤压。

## 2026-07-28 连续编辑与变量区修复

### Preset latest-value mutation

- `PresetDetailPage` 持有页面会话内的乐观 preset，DataStore 对同 ID 的普通回显不覆盖当前编辑状态。
- UI 不再传完整 `Settings/Preset` 快照，而是传 `(Preset) -> Preset` 相对变换；删除/toggle/edit/reorder
  均按稳定 entry ID 作用于传入的最新 preset。
- `PromptVM` 用 Mutex 串行执行当前页面的 preset mutations。
- `SettingsStore.updatePreset` 在单次 `dataStore.edit` 中解码最新 `PRESETS`，迁移目标后执行 transform，
  只写 `PRESETS`。旧 flow fallback 仅在持久化 key 缺失时使用。
- 当前交互模型是单页面编辑器；未来若支持多窗口或云端实时编辑同一 preset，需要版本号和 pending-op rebase，
  不得用无条件 flow 回显覆盖本地草稿。

### Editor variable metadata

- `dynamic` 只控制 `BuiltinPromptRegistry.resolveContent` 是否解析双花括号宏。
- `supportedVariables` 独立表达编辑器可插入、且专用消费点真实支持的变量原文。
- reply-draft：`{locale}`、`{content}`、`{user_instruction}`；suggestion：`{locale}`、`{content}`。
- memory：`{{memory_tables}}`；workspace：`{{workspace_name}}`、`{{cwd}}`。
- chips 的显示只看 `supportedVariables.isNotEmpty()`，不能要求 `dynamic=true`。

## 清理项（低优先，随手做）

- 删 `BuiltinPromptDef.displayNameRes`（`BuiltinPromptRegistry.kt:17`，从未被读）——
  **除非**修复项 5 决定用它渲染 Builtin 展示名，则改为真正接线。
- `dynamic` 可由 `supportedVariables.isNotEmpty()` 推导（`:25`）——评估是否收敛为计算属性。
- `VAR_MEMORY_TABLES`（`:55`）直接复用 `MemoryTableInjectionTransformer.MEMORY_TABLE_MACRO`（internal 同模块）。
- `presetPositionLabel`/`presetRoleLabel`/`usesStandaloneMessage`（`PresetDetailPage.kt:645-668`）
  与 `PromptPage.kt:965-1002` 逐字重复——提取到共享文件（如 `InjectionUi.kt`）或把 PromptPage 版本改 internal 复用。
- `RouteActivity.kt`：删重复 import `PresetDetailPage`，`git diff -w` 后回退纯空白 churn。
- `name`/`description` 每键持久化（`PresetDetailPage.kt:185/192`）：改本地 draft + 失焦/防抖持久化。
- 三个 `filterIsInstance+sortedBy`（`PresetDetailPage.kt:147-149`）包 `remember(preset.entries)`。

## 规范修复（CLAUDE.md）

- `BuiltinPromptRegistry.kt:84-86` 超 120 字符行长（`.editorconfig` `max_line_length=120`）——换行拆分（注意 raw string 内容不要改变注入语义）。
- i18n：`preset_detail_*` 仅 en/zh。用户此前未显式要求全量本地化；**若本次要求全量**则补 ja/zh-rTW/ko-rKR/ru，用 `locale-tui-localization` skill。否则不动（CLAUDE.md：未显式要求本地化时优先功能）。
- 图标 HugeIcons vs CLAUDE.md 的 Lucide 规则：同目录 9 个兄弟页面均用 HugeIcons，判为文档陈旧，**不改代码**；如需可另开 doc 修订。

## 兼容性与验证

- 所有模型字段新增（如 `injectable`、`legacyPriority?`）必须带默认值 + `@SerialName`（如适用），旧 JSON 向后兼容。
- 单测：迁移混合来源顺序锁定、去重、全局 enabled 继承、4 个 config-only Builtin 不注入且不泄漏宏、
  专用 override 消费、Builtin 新增/编辑去重、move/reorder 跨组顺序不变。
- `--no-daemon :app:compileDebugKotlin`（跳过 web-ui：`-x :web:buildWebUi`）+ `:app:testDebugUnitTest`。
- 有设备时 `:app:installDebug` 真机验收。
