# 修复 #182 预设可编辑条目实现的缺陷

承接 `07-25-issue-182-preset-editable-entries`（原始特性 spec）。特性已落地实现（工作树未提交），
代码审查（8 角度查找 + 独立验证）确认 10 个缺陷。本任务修复它们。

## 定调（用户拍板）

- **预设的本质**：不是「简单注入条目容器」，而是让用户**显式化、可维护自己配置**的地方。
  因此 `reply_draft` / `suggestion` 这类功能内部 meta-prompt 应**保留在注册表**作为可查看/可编辑配置，
  **不能**因为「注入进对话没意义」而删除。
- **真正的 bug**：注入路径 `resolvePresetEntry` 无差别地把所有 `enabled` 条目都拼进对话系统提示。
  正确模型是给内置模板引入**可注入性（injectability）**区分。
- **迁移排序回归**：按**保守方案**修复——迁移锁定现有注入排序不回归，不追求「完美的全局优先级融合」。

## 核心模型：全部 config-only（注入路径跳过）

**最终架构定调（2026-07-27，后续用户决定覆盖早期“不接线”方案）**：

- `TransformerContext` 实际持有 `assistant`、`settings` 和 `workspaceCwd`；专用 transformer 可以读取当前助手关联的预设。
- 4 个内置模板仍全部是 **config-only**：`PromptInjectionTransformer.resolvePresetEntry` 一律跳过，避免
  workspace/memory 双注入、动态宏泄漏，以及把 reply-draft/suggestion meta-prompt 塞进普通对话。
- “config-only”只表示**不进入通用对话注入路径**，不表示编辑值无效。用户后续拍板让覆盖内容回流到各自专用消费点。
- 公共纯函数 `resolveBuiltinOverride(assistant, presets, builtinKey)` 负责选择启用且非空的覆盖；运行时宏仍由持有数据的消费点解析。

| key | 性质 | 注入路径行为 | 真实消费点 |
| --- | --- | --- | --- |
| `workspace_guide` | config-only | **跳过** | `WorkspaceReminderTransformer` 读取覆盖并替换 workspace 宏 |
| `memory_table_guide` | config-only | **跳过** | `MemoryTableInjectionTransformer` 读取引导文案覆盖，数据块仍运行时生成 |
| `reply_draft` | config-only | **跳过** | `buildInputDraftPrompt` 使用覆盖模板 |
| `suggestion` | config-only | **跳过** | 建议流程按“启用非空预设覆盖 > 全局设置”取模板 |

config-only 条目在 PresetDetailPage 里**可见可编辑**；`resolvePresetEntry` 对 Builtin 返回 null，
各专用消费点则读取覆盖并在自己的运行时上下文里完成占位符/动态数据处理。

> 边界：不改变 `TransformerContext` 结构和 transformer pipeline 顺序；只在既有专用消费点读取 preset override。

## 缺陷清单（审查确认，按严重度）

### 正确性

1. **配置型内置条目被错误注入**（`BuiltinPromptRegistry.kt:156` + `PromptInjectionTransformer.kt:189`）
   `reply_draft`/`suggestion` 为 `dynamic=false`，`resolveContent` 原样返回含 `{content}`/`{locale}` 单花括号的模板并注入系统提示。
   → 按上表：注入路径对配置型跳过。

2. **内置配置条目泄漏未解析宏**（`PromptInjectionTransformer.kt:46`）
   初版尝试在通用注入路径解析 `{{memory_tables}}`/`{{workspace_name}}`，但当前上下文没有对应运行时数据，
   且专用 transformer 已负责真实注入。→ 4 个 Builtin 全部按 config-only 跳过，既不双注入也不泄漏宏。

3. **迁移后同一注入重复注入两次**（`PromptInjectionTransformer.kt:138`）
   旧路径用 Set 合并去重；迁移后 step1（直连绑定）+ step1b（entries 快照，同 id）各注入一次。
   → 迁移展开与直连绑定之间按 id 去重（保持只注入一次）。

4. **迁移丢弃全局 ModeInjection.enabled**（`Assistant.kt:293`）
   `migratedWithEntries` 只按 `disabledEntryIds` 设 enabled；全局 `enabled=false` 的注入被快照成 `enabled=true`。
   → 迁移时 `enabled = (id !in disabledEntryIds) && globalInjection.enabled`（保守：与旧路径 `filter { it.enabled }` 一致）。

5. **迁移 priority→order 导致相对直连注入排序翻转**（`PromptInjectionTransformer.kt:207`）
   `priority = -order`（恒 ≤0），迁移条目排到直连注入（正 priority）之后。
   → 保守方案：迁移时把原 priority 也保留/映射，使 `sortedByDescending` 结果与迁移前一致（锁定不回归）。补混合来源单测。

6. **迁移不持久化 + 有效 id 过滤先于迁移导致快照丢内容**（`PreferencesStore.kt:476`）
   `migratedWithEntries()` 只在读取 flow 计算、从不写回；且 `modeInjectionIds` 先被有效性过滤再迁移，
   升级后未编辑 preset 就删掉全局注入 X → X 内容永久从 preset 消失。
   → 迁移在过滤之前完成快照（快照语义应捕获删除前内容）；并参照 `scheduleSubagentBuiltinMigrationPersist` 一次性持久化迁移结果。

### UI / 交互

7. **entries-only 预设卡片显示「0 条目」/ 计数错误**（`PromptPage.kt:392`、`ExtensionContent.kt:72`、`PromptPage.kt:312/377/514`）
   `PresetCard`/`ExtensionContent` 仍用 `effectiveInjectionIds()` 统计，对 entries 模型恒空。
   → 统计改为基于 `entries`（enabled 条目数 / 条目名）。

8. **Reference 编辑弹窗对失效引用默认选中第一项**（`PresetDetailPage.kt:542`）
   `selectedOption = ... ?: modeInjections.first()` 显示首项已选，但 draft 保留死 Uuid，确认后静默不注入。
   → 失效引用应显式提示未选中/需重选，或确认时校验。

9. **Builtin 切 builtinKey 残留旧 key 的 override**（`PresetDetailPage.kt:479`）
   切 key 只改 `builtinKey`，`overrideContent`/`overridePosition` 原样带过去。
   → 切 key 时清空 override（或重置为新 def 默认）。

10. **moveInGroup 跨组重排翻转未触碰条目的注入顺序**（`PresetDetailPage.kt:162`）
    每组独立 order 0..n，transformer 按单一全局 `-order` 稳定排序，`others + renumbered` 改变列表位置。
    → 排序键消除跨组歧义（如 order 全局唯一，或排序时带组内稳定 tiebreak），使可视顺序与注入顺序一致。

### 后续 UI 定调

- Builtin 区加号使用去重选择器；编辑时保留当前 key，但排除兄弟条目已经占用的 key，禁止产生重复 Builtin 配置。
- 条目排序使用项目既有 `sh.calvin.reorderable` 拖拽，仅允许同类型组内重排。
- PresetDetailPage 只展示 Builtin + Custom；Reference 数据模型和已有数据保留，PromptPage 旧入口暂不删除。
- 卡片危险操作收进溢出菜单，删除按条目 ID/名称二次确认；标签允许换行，避免窄屏被固定按钮挤压。

### 2026-07-28 用户复现补充

11. **删除一个条目会把其他条目恢复为进入页面前的内容**：详情页把 Compose 捕获的整份
    `Settings/Preset` 快照异步回写；拖拽 callback 还会长期捕获首次 entries。删除确认扩大竞态窗口，
    旧 DataStore emission 或旧 callback 随后覆盖较新编辑。
    → 页面使用会话内乐观 preset；所有动作提交为相对 transform。`SettingsStore` 在单次 DataStore edit
    中读取最新 `PRESETS`，只更新目标 preset，并由 `PromptVM` 串行执行同页 mutation。

12. **大部分 Builtin 没有魔法变量区**：UI 把 `dynamic`（注册表是否解析双花括号）误当成
    “模板是否有编辑器变量”。reply-draft/suggestion 实际使用单花括号变量，但注册表没有登记。
    → `supportedVariables` 表达专用消费点支持的变量原文；变量区只依据该列表。四个模板分别保留
    `{...}` / `{{...}}` 的真实语法。

## 次要清理（可选，随手做）

- G 组：`displayNameRes` 死字段；`dynamic` 可由 `supportedVariables.isNotEmpty()` 推导；`overridePosition` 双字段冗余。
- Reuse：`presetPositionLabel`/`presetRoleLabel`/`usesStandaloneMessage` 与 PromptPage.kt 逐字重复 → 提 internal 复用。
- 性能：name/description 每键全量写 DataStore（可加本地 draft/debounce）；分组 filter+sort 未 `remember`。
- 规范：`preset_detail_*` 仅 en/zh，缺 ja/zh-rTW/ko-rKR/ru；`BuiltinPromptRegistry.kt:84-86` 超 120 行长；RouteActivity 重复 import + 空白 churn。

## Acceptance Criteria

- [x] AC-Fix1：配置型内置条目（reply_draft/suggestion）在预设中可见可编辑，但不注入进对话系统提示。
- [x] AC-Fix2：workspace/memory_table 等配置型内置条目不进入通用注入路径，不双注入、不泄漏字面宏、不报错。
- [x] AC-Fix3：迁移后同一全局注入至多注入一次（去重）。
- [x] AC-Fix4：全局禁用的注入迁移后仍不注入。
- [x] AC-Fix5：迁移不改变现有注入的相对排序（含混合来源：直连 + preset）。
- [x] AC-Fix6：迁移在全局注入被删前完成快照；迁移结果持久化一次，不每次读取重算。
- [x] AC-Fix7：预设卡片/扩展页正确显示 entries 条目数与名称。
- [x] AC-Fix8：失效 Reference 在编辑弹窗不伪装成已选中有效项。
- [x] AC-Fix9：切换 builtinKey 不残留旧 key 的覆盖内容/位置。
- [x] AC-Fix10：组内重排不影响其他组条目的注入顺序；可视顺序 == 注入顺序。
- [x] AC-Fix11：4 个 Builtin 的非空启用覆盖由各自专用流程消费；suggestion、workspace、memory 的优先级/宏/动态数据契约不回归。
- [x] AC-Fix12：新增和编辑 Builtin 都不能产生重复 key；编辑选择器保留当前 key 并排除兄弟条目占用的 key。
- [x] AC-Fix13：删除条目必须先显示包含目标名称的确认对话框，取消/返回不得修改预设。
- [x] AC-Fix14：连续编辑、开关、拖拽、新增和删除基于最新持久化 preset 合成，不得回滚其他条目。
- [x] AC-Fix15：四个 Builtin 均展示各自支持的魔法变量，且单/双花括号与专用消费点完全一致。

## Verification

- 注入单测：4 个 Builtin 全部跳过且不泄漏宏、去重、全局 enabled 继承、混合来源排序锁定、跨组重排稳定。
- 迁移单测：持久化、删除前快照、幂等。
- 专用消费单测：override 解析、reply-draft 模板、workspace 宏、memory 引导文案 + 动态数据块。
- UI helper 单测：entries 计数、失效 Reference、Builtin 新增/编辑去重、拖拽同组重排和跨组 no-op。
- 最新值写入单测：stale fallback 不覆盖兄弟条目；连续编辑 + 删除在持久层顺序合成。
- 变量契约单测：精确断言四个 Builtin 的变量列表；`dynamic=false` 仍可展示专用变量。
- `:app:compileDebugKotlin -x :web:buildWebUi` + `:app:testDebugUnitTest`（`--no-daemon`）。
- 有设备时 `:app:installDebug` 真机验收。

UI 验收矩阵：覆盖空/正常/长文本/多条目/重复 key 防护；启用/禁用/失效 Reference；窄屏、滚动、
大字体与中英文；拖拽、编辑、删除、返回/取消。自动测试覆盖纯函数与保存约束，设备阶段核验布局和手势。
