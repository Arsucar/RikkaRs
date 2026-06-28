# 执行计划：`06-28-audit-ui`（批次 D · UI 一致性）

> 对应 PRD：`.trellis/tasks/06-28-audit-ui/prd.md`  
> 本任务为轻量交付：**仅 `implement.md`**，不单独写 `design.md`（逻辑边界在下列条目中已写明）。

## 范围与优先级

| 批次 | PRD | 优先级 | 主要文件 |
|------|-----|--------|----------|
| E-P1-1 / FR-1 | ModelList tag 与收藏一致 | P1 | `ModelList.kt` |
| E-P1-2 / FR-2 | 扩展 ID 全局删除后 prune | P1 | `PromptVM.kt`、`AssistantDetailVM.kt`（可选单测） |
| E-P1-3 / FR-3 | ThinkTag 多块单测 | P1 | `ThinkTagTransformer.kt`、`ThinkTagTransformerTest.kt`（新建） |
| FR-4～FR-7 | 子代理/设置页 a11y | P2 | `SubagentToolUIs.kt`、`AssistantSubagentPage.kt`、`ExtensionSubagentsPage.kt`、`SettingProviderDetailPage.kt`、`strings.xml` |

**明确不做**：P0 stale streaming、`remember(context.tool)`、E-P2 tag 大小写、ModelList 空分组、ImgGen i18n 等（见 PRD 范围外）。

---

## 有序检查清单

### Phase 0：准备（串行，~5 min）

- [ ] **0.1** 确认任务目录与 PRD：`python ./.trellis/scripts/task.py current`（或等价）指向 `06-28-audit-ui`；`task.py start` 后再改代码。
- [ ] **0.2** 实现前读 spec（按需）：`python ./.trellis/scripts/get_context.py --mode packages` → `app` UI / datastore 相关条目。

---

### Phase 1：P1 功能（可并行）

#### 1.1 FR-1 — ModelList 收藏与 tag 筛选一致（E-P1-1）

**文件**：`app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`

| 步骤 | 位置 | 具体改动 |
|------|------|----------|
| 1.1.1 | **L322–331** `favoriteModels` 的 `remember` | 依赖增加 `selectedModelListTag`：`remember(..., selectedModelListTag)`。 |
| 1.1.2 | **L322–331** `mapNotNull` 内 | 在得到 `provider` 之后、返回 `model to provider` 之前：若 `selectedModelListTag != null` 且 `!provider.tags.contains(selectedModelListTag)`，则 `return@mapNotNull null`。`selectedModelListTag == null` 时行为与现网一致。 |
| 1.1.3 | **L359+** `selectedModelPosition` | `remember` 的 key 已含 `favoriteModels`；1.1.2 后收藏列表随 tag 变化，一般无需改公式；改完后在 IDE 确认滚动定位仍合理。 |
| 1.1.4 | 收藏拖拽 **L426–442** | **不**改 `settings.favoriteModels` 持久化逻辑；仅 UI 隐藏不符合 tag 的收藏项。 |

**手工验收**：Provider 带 tag `A`，收藏其下模型；选 tag `B` 时收藏区不显示；选 `A` 时显示；清除 tag 筛选后收藏恢复（仍受 `modelType` 约束）。

---

#### 1.2 FR-2 — 助手扩展孤立 ID prune（E-P1-2）

**现状（已核对源码）**：

- `QuickMessagesVM.kt` **L44–56**：`updateQuickMessages` 已在写盘时 prune 全助手 `quickMessageIds`（对齐 PRD）。
- `PreferencesStore.kt` **L343–361**：`settingsFlow` 映射层对三类 ID **读时 filter**；**写路径** `PromptVM.updateSettings`（`PromptVM.kt` L17–20）仅 `settingsStore.update(settings)`，**无** prune。
- `AssistantExtensionsPage.kt` **L112–173**：toggle 经 `vm.update(assistant.copy(...))` 写回；`AssistantDetailVM.update`（**L163–178**）不校验 ID 是否仍存在于全局列表。

| 步骤 | 文件:行 | 具体改动 |
|------|---------|----------|
| 1.2.1 | 新建或复用工具函数 | 在 `app/src/main/java/me/rerere/rikkahub/data/datastore/`（建议 `AssistantExtensionIds.kt` 或 `SettingsExtensions.kt`）增加 **纯函数**：`fun Settings.withPrunedAssistantExtensionIds(): Settings`，对 `assistants` 中每个助手：`quickMessageIds` ∩ `quickMessages.id`、`modeInjectionIds` ∩ `modeInjections.id`、`lorebookIds` ∩ `lorebooks.id`（均为 `Set`）。**不**改 `PreferencesStore` 现有 `settingsFlow` map 逻辑。 |
| 1.2.2 | `PromptVM.kt` **L17–20** | `updateSettings` 内：`settingsStore.update { current -> incoming.withPrunedAssistantExtensionIds() }` 或先 merge 再 prune（保证传入的 `modeInjections`/`lorebooks` 列表更新后，助手绑定同步求交写盘）。与 `QuickMessagesVM` 语义一致：列表变短即 prune。 |
| 1.2.3 | `AssistantDetailVM.kt` **L163–178** `update` | **防御性**（PRD 可选但建议做）：写回前对当前 `assistant` 调用与 1.2.1 相同的三类 ID filter（基于 `settings.value` 全局列表），避免 UI toggle 写入已删 ID。 |
| 1.2.4 | `AssistantExtensionsPage.kt` | **无需改 toggle 逻辑**（除非实测仍脏数据；则依赖 1.2.3）。 |

**可选单测**：`app/src/test/.../AssistantExtensionIdsTest.kt` — 给定助手含孤立 UUID，prune 后集合仅保留有效 ID。

**手工验收**：助手绑定某 mode injection / lorebook / quick message → 在 Prompts / Quick Messages 删除全局项并保存 → 再开该助手扩展 Tab，`selectedIds` 无已删 ID；重启后 settings 仍干净。

---

#### 1.3 FR-3 — ThinkTagTransformer 单测（E-P1-3）

**文件**：`app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformer.kt`（**L10–46** `splitThinkTaggedText`，**L49–99** transformer）

| 步骤 | 改动 |
|------|------|
| 1.3.1 | 将 `splitThinkTaggedText` 改为 **`internal`**（同 module 测试可见），或保留 `private` 仅通过 `visualTransform` / `onGenerationFinish` 测（后者更脆、需构造 `UIMessage` + `TransformerContext`）。**推荐**：`internal fun splitThinkTaggedText` + 直接单测。 |
| 1.3.2 | 新建 `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformerTest.kt`，风格对齐 `TimeReminderTransformerTest.kt`。 |
| 1.3.3 | 用例（至少）：① 无 think 标签 → 单 `Text`；② 单块闭合 `<think>...</think>` → `Reasoning` + 可选前后 `Text`，闭合块 `finishedAt != null`（传入 `finishedAtOnClose`）；③ 多块交替；④ 仅开头 `<think>` 无 `</think>` → 尾段 `Reasoning.finishedAt == null`；⑤ `onGenerationFinish` 对未闭合块补 `finishedAt`（对 ASSISTANT 含 think 的 `Text` part 跑 finish，断言 Reasoning `finishedAt` 非空）。 |
| 1.3.4 | 测试注释一行说明：子代理转录路径**不一定**经过本 transformer（与 `06-27-sub-agent-streaming-ui` research 一致），本单测覆盖**主聊天 ASSISTANT 文本**契约。 |

**不强制**：子代理 UI 路径接入 ThinkTag（超出本 PRD）。

---

### Phase 2：P2 无障碍（可并行，4 个文件 + strings）

#### 2.1 FR-4 — `SubagentToolUIs.kt`

**文件**：`app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/SubagentToolUIs.kt`

| 行号（当前） | 场景 | 改动 |
|--------------|------|------|
| **~381** | 非 CoT `ToolCall` 行 Icon | `contentDescription = stringResource(R.string.subagent_step_tool)`（见 2.5 新增 key）或简短语义；勿用带 `%1$s` 占位的长 `subagent_tool_ui_tool_call` 作 CD。 |
| **~435** | CoT Reasoning Icon | `stringResource(R.string.subagent_step_thinking)` |
| **~466** | CoT ToolCall Icon | `stringResource(R.string.subagent_step_tool)` |
| **~520、~550** | CoT Text / Summary Icon | `stringResource(R.string.subagent_step_text)` |

（审计行号 403/454/485/537 与当前文件 **381/435/466/520/550** 略有偏移，以 grep `contentDescription = null` 为准。）

#### 2.2 FR-5 — `AssistantSubagentPage.kt`

| 行号 | Icon | `contentDescription` |
|------|------|----------------------|
| **142, 230** | Refresh03 | `R.string.common_refresh` |
| **146** | Add01 | `R.string.extensions_page_add_subagent`（或项目已有 add key） |
| **241, 267** | Delete01 | `R.string.common_delete` |
| **264** | Copy01 | `R.string.common_copy` |

#### 2.3 FR-6 — `ExtensionSubagentsPage.kt`

| 行号 | Icon | `contentDescription` |
|------|------|----------------------|
| **77** | Add01 | `R.string.extensions_page_add_subagent` |
| **80** | MoreVertical | `R.string.skills_page_more_actions` |
| **139** | ArrowRight01 | 新增 `extensions_subagents_open`（见 2.5） |
| **142** | Delete01 | `R.string.common_delete` |

#### 2.4 FR-7 — `SettingProviderDetailPage.kt`

| 行号 | 改动 |
|------|------|
| **308–329** 内 **~324** | tag chip 删除 `Icon`：`contentDescription = stringResource(R.string.common_delete)`（或 `provider_tag_remove` 若需更贴切；PRD 允许二选一，优先 `common_delete` 少增 key）。 |

#### 2.5 字符串资源（本任务至少 en + zh）

| Key | `values/strings.xml` | `values-zh/strings.xml` |
|-----|----------------------|-------------------------|
| `subagent_step_tool` | 新增，如 `Tool call` | 新增，如 `工具调用` |
| `extensions_subagents_open` | 新增，如 `Open subagent profile` | 新增，如 `打开子代理配置` |
| `subagent_step_thinking` / `subagent_step_text` | 已有 **L1372–1373** | **补 zh**（当前 zh 可能缺失，与 `06-28-audit-i18n` 一致） |

---

### Phase 3：验证与收尾（串行）

- [ ] **3.1** 单元测试：`.\gradlew :app:test --tests "me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformerTest"`，再全量 `.\gradlew :app:test`。
- [ ] **3.2** 编译：`.\gradlew :app:compileDebugKotlin`。
- [ ] **3.3** Lint（可选但 PRD 建议）：`.\gradlew :app:lint`（或项目惯例 `.\gradlew lint`），处理本任务触及文件的新告警。
- [ ] **3.4** 真机（AGENTS.md）：`adb devices` 有 `device` 时 `.\gradlew :app:installDebug`。
- [ ] **3.5** 对照 PRD **Acceptance Criteria** 逐项勾选；跑 `trellis-check` 技能流程（若仓库要求）。

---

## 并行化建议

```
Phase 0 ──► Phase 1 三路并行 ──► Phase 2 四路并行 ──► Phase 3
              ├─ 1.1 ModelList
              ├─ 1.2 Extension prune (+ 1.2.1 公共函数先合入或一人独占)
              └─ 1.3 ThinkTag 测试
              Phase 2: SubagentToolUIs | AssistantSubagentPage | ExtensionSubagentsPage | SettingProviderDetailPage+strings
```

- **1.2.1** 若多人做 1.2，应先合入 prune 函数，再改 `PromptVM` / `AssistantDetailVM`。
- **Phase 2** 与 **Phase 1** 无硬依赖，可在 P1 编译通过后并行；strings 变更由任一人统一提交避免冲突。

---

## 验证步骤（摘要）

| FR | 自动 | 手工 |
|----|------|------|
| FR-1 | 编译通过 | ModelList tag A/B 与收藏显示 |
| FR-2 | 可选 `AssistantExtensionIdsTest` | 删全局扩展项后助手 Tab |
| FR-3 | `ThinkTagTransformerTest` 全绿 | — |
| FR-4～7 | 编译 + grep 无新增 `contentDescription = null`（上述行位） | TalkBack 抽样读屏 |

---

## 回滚计划

| 阶段 | 回滚方式 | 风险 |
|------|----------|------|
| 整任务 | `git revert` 单次合并提交，或按 Phase 分 commit 便于 `revert` 单段 | 低 |
| FR-1 | 还原 `ModelList.kt` `favoriteModels` 块 | 仅 UI 筛选 |
| FR-2 | 还原 `PromptVM` / `AssistantDetailVM` / 删除 prune 工具文件；**不**动 `PreferencesStore` flow | 孤立 ID 可能再现（与改前一致） |
| FR-3 | 删除测试文件；`splitThinkTaggedText` 恢复 `private` | 无运行时影响 |
| FR-4～7 | 还原各页 `Icon` CD 与新增 strings | 仅 a11y |

**数据**：本任务不写 destructive migration；prune 仅缩小 ID 集合，回滚代码不会自动恢复已 prune 的 ID（可接受，与 Web API 语义一致）。

---

## 实现顺序推荐（单人）

1. 1.2.1 → 1.2.2 → 1.2.3（写盘正确性优先）  
2. 1.1（小改、易验）  
3. 1.3（单测）  
4. 2.5 strings → 2.1～2.4  
5. Phase 3  

---

## 参考（已读）

- PRD：`.trellis/tasks/06-28-audit-ui/prd.md`
- 研究：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-b-ui.md`、`subagent-e-provider-ext.md`
- 对照：`QuickMessagesVM.kt` L44–56、`PreferencesStore.kt` L343–361、`ThinkTagTransformer.kt` 全文