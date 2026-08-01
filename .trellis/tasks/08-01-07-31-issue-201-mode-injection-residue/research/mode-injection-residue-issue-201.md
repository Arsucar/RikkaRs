# Research: issue #201 mode injection residue

- **Query**: 关闭预设/世界书/记忆后 system 消息仍残留旧"模式注入"的实现细节（只读）
- **Scope**: internal
- **Date**: 2026-08-01
- **Task**: `08-01-07-31-issue-201-mode-injection-residue`

## Findings

### 1. PromptInjectionTransformer — collectInjections 与 modeInjectionIds

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`

入口 `transform`（:26-38）从 `ctx.assistant` / `ctx.settings.modeInjections` / lorebooks / conversation IDs / presets 调用 `transformMessages` → `collectInjections`。

**effective ID 选择（:95-106）** — 非“无条件 OR”，而是二选一：

```kotlin
// :95-106
val effectiveModeInjectionIds = if (assistant.allowConversationPromptInjection) {
    conversationModeInjectionIds
} else {
    assistant.modeInjectionIds
}
val effectiveLorebookIds = if (assistant.allowConversationPromptInjection) {
    conversationLorebookIds
} else {
    assistant.lorebookIds
}
val effectivePresetIds = assistant.presetIds
```

要点：
- `allowConversationPromptInjection == false`（默认）时，**直接使用** `assistant.modeInjectionIds`，不看对话级、不看预设开关。
- `allowConversationPromptInjection == true` 时，**完全替换为** `conversationModeInjectionIds`（助手直连被忽略；见单测 `assistant mode injection should be ignored when conversation injection is allowed`）。
- `presetIds` **始终**来自 assistant，与 `allowConversationPromptInjection` 无关。

**Step 1 直连 + 旧预设展开（:109-125）**：

```kotlin
// :109-125
val presetInjectionIds = activePresets
    .filter { !it.hasEntries() }
    .flatMap { it.effectiveInjectionIds() }
    .toSet()
val allModeInjectionIds = effectiveModeInjectionIds + presetInjectionIds

val injectedIds = mutableSetOf<Uuid>()
modeInjections
    .filter { it.enabled && allModeInjectionIds.contains(it.id) }
    .forEach {
        injections.add(it)
        injectedIds.add(it.id)
    }
```

**Step 1b 新 entries 预设（:127-147）**：`hasEntries()` 的 preset 展开 `PresetEntry`；与 step1 按 `deduplicationId` 去重。

**Step 2 Lorebook（:149-165）**：`effectiveLorebookIds` + `lorebook.enabled` + entry 触发条件。

**应用位置（:241-278）**：`BEFORE_SYSTEM_PROMPT` / `AFTER_SYSTEM_PROMPT` 拼进 system 文本；无 system 时新建。

**任务提示中的“约 :127-133 无条件注入 assistant.modeInjectionIds”**：当前源码中，**无条件直连发生在 :95-99 + :115-125**（默认路径 `assistant.modeInjectionIds` 并入 `allModeInjectionIds` 后 filter enabled）。:127-147 是 entries 路径，不是 modeInjectionIds 直连。

---

### 2. Assistant.modeInjectionIds 字段

**声明**: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt:57`

```kotlin
val modeInjectionIds: Set<Uuid> = emptySet(),      // 关联的模式注入 ID
val presetIds: Set<Uuid> = emptySet(),             // 关联的预设 ID (见 issue #65)
val lorebookIds: Set<Uuid> = emptySet(),
...
val allowConversationPromptInjection: Boolean = false, // 允许对话级别绑定提示词注入
```

**语义**: 助手级“模式注入/独立注入”直接绑定全局 `Settings.modeInjections` 中的 ID。与 `presetIds`（预设容器）、`lorebookIds`（世界书）并列。

**写入 / 清空位置**:

| 位置 | 行为 |
|---|---|
| `ui/pages/assistant/detail/AssistantExtensionsPage.kt:239-243` | UI 勾选：`assistant.copy(modeInjectionIds = newIds)` |
| `data/datastore/PreferencesStore.kt:830-850` `updateAssistantInjections` | 批量写 mode/lorebook/quickMessage IDs |
| `web/routes/SettingsRoutes.kt:103-123` | Web API 更新助手 modeInjectionIds |
| `PreferencesStore.kt:468-470` settings flow | **仅过滤**无效 ID（仍在全局 modeInjections 中的保留），**不清空** |
| `data/datastore/AssistantExtensionIds.kt:14,29` | 同样只 prune 无效引用 |
| `data/ai/subagent/SubagentHost.kt:790` | 子代理构造时 `modeInjectionIds = emptySet()` |

**聊天页 ExtensionSelector 不写 modeInjectionIds**（无独立注入 tab，见第 5 点）。

**对话级平行字段**: `Conversation.modeInjectionIds`（`Conversation.kt:29`），仅在 `allowConversationPromptInjection` 时由 transformer 使用；ChatService 始终把 `conversation.modeInjectionIds` 传入 generation（:838, :1719）。

---

### 3. 迁移逻辑：#73 withDefaultPreset / #182 migratedWithEntries / migratePresetEntriesIfNeeded

#### 3a. `withDefaultPreset` — PreferencesStore.kt:1465-1478

```kotlin
private fun List<Preset>.withDefaultPreset(
    modeInjections: List<PromptInjection.ModeInjection>,
    shouldCreateDefault: Boolean,
): List<Preset> {
    if (!shouldCreateDefault || modeInjections.isEmpty() || any { it.id == DEFAULT_PRESET_ID }) return this
    return listOf(
        Preset(
            id = DEFAULT_PRESET_ID,
            name = "Default Preset",
            description = "Contains existing quick injections.",
            modeInjectionIds = modeInjections.map { it.id }.toSet(),
        )
    ) + this
}
```

- 条件：`!presetsStoreExists`（`shouldCreateDefault`）且有全局 modeInjections、尚无 DEFAULT_PRESET。
- 把**全部**全局 mode injection ID 塞进 Default Preset 的 **Preset.modeInjectionIds**（旧字段）。
- **不碰** `Assistant.modeInjectionIds`。

调用点：settings flow 清理阶段 `PreferencesStore.kt:422-425`。

#### 3b. `Preset.migratedWithEntries` — Assistant.kt:291-328

```kotlin
fun Preset.migratedWithEntries(modeInjections: List<...>): Preset {
    if (entriesVersion >= PRESET_ENTRIES_VERSION) return this
    if (entries.isNotEmpty()) {
        return copy(modeInjectionIds = emptySet(), disabledEntryIds = emptySet(), entriesVersion = ...)
    }
    val migrated = modeInjections
        .filter { it.id in modeInjectionIds }
        .sortedByDescending { it.priority }
        .mapIndexed { index, injection ->
            PresetEntry.Custom(
                id = injection.id,
                enabled = (injection.id !in disabledEntryIds) && injection.enabled,
                ...
                content = injection.content,  // 快照
                legacyPriority = injection.priority,
            )
        }
    return copy(
        modeInjectionIds = emptySet(),  // 清空 Preset 旧字段
        disabledEntryIds = emptySet(),
        entries = migrated,
        entriesVersion = PRESET_ENTRIES_VERSION,
    )
}
```

- 只迁移 **Preset** 的 `modeInjectionIds` → `PresetEntry.Custom` 内容快照。
- 迁移后 **Preset.modeInjectionIds 清空**。
- **不读、不写 Assistant.modeInjectionIds**。

#### 3c. `migratePresetEntriesIfNeeded` — PreferencesStore.kt:860-886

- 从 DataStore 读 MODE_INJECTIONS / PRESETS。
- 若无 PRESETS 但有 modeInjections：现场构造 Default Preset（`modeInjectionIds = 全部 injection ids`），再 `migratedWithEntries`。
- 只写 PRESETS + `PRESET_ENTRIES_MIGRATED` 标记。
- **不修改 assistants / Assistant.modeInjectionIds**。

#### 3d. settings flow 中的顺序 — PreferencesStore.kt:422-483

1. `withDefaultPreset`（可能创建带旧 modeInjectionIds 的 Default Preset）
2. 对每个 preset `migratedWithEntries`（快照 + 清空 Preset 旧字段）
3. assistants：`modeInjectionIds = filter { in validModeInjectionIds }` — **保留仍存在的全局 ID**

**现状结论**: 迁移把旧“全局快速注入”复制进 Default Preset entries，但若历史数据里助手仍持有 `modeInjectionIds`，或用户在助手扩展页勾过独立注入，**直连路径继续注入**；关闭 presetIds 不能关掉直连。

---

### 4. 其余注入源与开关（关闭三者后 modeInjectionIds 仍注入）

| 源 | 位置 | 开关 / 绑定 | 关闭后是否仍注入 modeInjectionIds |
|---|---|---|---|
| **助手直连 ModeInjection** | `collectInjections` :95-99, :120-125 | `assistant.modeInjectionIds`（默认）；或 conversation 当 `allowConversationPromptInjection` | **是**（独立于预设/世界书/记忆） |
| **旧模型预设** | :109-114 → 并入 allModeInjectionIds | `assistant.presetIds` + `!hasEntries()` + `effectiveInjectionIds()` | 关 preset 则此路径停；直连仍走 |
| **新 entries 预设** | :127-147 | `assistant.presetIds` + `hasEntries()` + `entry.enabled` | 关 preset 则停；直连仍走 |
| **Lorebook** | :149-165 | `effectiveLorebookIds` + book.enabled + entry 触发 | 关 lorebook 只停本源 |
| **普通记忆** | **不在** PromptInjectionTransformer | `GenerationHandler.kt:586-588`：`if (assistant.enableMemory) append(buildMemoryPrompt(...))` | 关 enableMemory 只停记忆 prompt |
| **记忆表** | ChatService :1674-1683 `MemoryTableInjectionTransformer` | memoryTableEnabled 计划 | 独立 |
| **语义记忆** | ChatService :1686-1687 `SemanticMemoryTransformer` | `settings.semanticMemoryConfig.enabled && assistant.enableSemanticMemory` | 独立 |

**用户如何关**：
- 预设：ExtensionSelector / AssistantExtensionsPage 取消 `presetIds`（聊天页预设 tab 单选 `setOf(id)` / 取消）。
- 世界书：取消 `lorebookIds`（或对话级 lorebookIds）。
- 记忆：助手设置 `enableMemory` / 语义记忆开关等。
- **独立模式注入**：仅 `AssistantExtensionsPage` 预设 tab 内 “独立注入” 区（:229-246）可改 `modeInjectionIds`；**聊天页 ExtensionSelector 无此 UI**。

**确认**: 关闭预设 + 世界书 + 记忆后，只要 `assistant.modeInjectionIds` 非空且对应全局 injection `enabled`，`collectInjections` step1 仍注入 → system 残留。

Transformer 注册：`ChatService.kt:363-371` 固定含 `PromptInjectionTransformer`；预览路径同一套 transformers（:1672-1691 + GenerationHandler transforms）。

---

### 5. ExtensionSelector 结构

**文件**: `ui/components/ui/ExtensionSelector.kt`

- `pagerState = rememberPagerState { 4 }`（:90）
- Tabs（:101-128）:
  1. **presets** — `PresetsContent`，写 `assistant.presetIds`（:138-162）
  2. **skills** — `SkillsContent`，写 `enabledSkills`（:165-194）
  3. **lorebooks** — `LorebooksContent`；若 `allowConversationPromptInjection` 写 conversation，否则 assistant（:82-88, :196-222）
  4. **quick_messages** — `QuickMessagesContent`，写 `quickMessageIds`（:225-248）

**无“独立注入 / modeInjection” tab。**

加 tab 模板（现有模式）:
1. `rememberPagerState { N }` 改为 N+1
2. `SecondaryScrollableTabRow` 加 `Tab(...)`
3. `HorizontalPager` `when (page)` 加分支
4. 内容组件参考 `ui/components/ai/ExtensionContent.kt` 的 `ModeInjectionsContent`（:98+）
5. 字符串 `R.string.extension_selector_tab_*`

**对比**: `AssistantExtensionsPage.kt` 在 **presets 页内嵌** 独立注入（:229-246），非独立 tab：

```kotlin
// AssistantExtensionsPage.kt:229-246
Text(... independent_injections ...)
ModeInjectionsContent(
    modeInjections = settings.modeInjections,
    selectedIds = assistant.modeInjectionIds,
    onToggle = { injId, checked ->
        val newIds = if (checked) assistant.modeInjectionIds + injId
        else assistant.modeInjectionIds - injId
        vm.update(assistant.copy(modeInjectionIds = newIds))
    },
    ...
)
```

FilesPicker 角标把 `modeInjectionIds` 计入扩展数（:174-184），但面板本身无法切换它们。

---

### 6. “查看对话上下文”预览（#109）

**存在**，CHANGELOG 有记载。

| 层 | 路径 | 行号 |
|---|---|---|
| 数据模型 | `data/ai/ContextPreview.kt` | `ContextPreview` :52-60；`toContextPreview` :62-86 |
| 构建 | `service/ChatService.kt` `buildContextPreview` | :1604-1617 |
| 实现细节 | 同上 `prepareGenerationRequest(..., mode = Preview)` | :1609-1616；transformers 含 PromptInjectionTransformer |
| VM | `ui/pages/chat/ChatVM.kt` | `loadContextPreview` :202+；`clearContextPreview` |
| UI | `ConversationContextInspector.kt` | 展示 messages/tools |
| 入口 | `ConversationMemoryTableDrawer.kt` + `ChatPage.kt:386-387` | onLoad/onClear |

预览与真实发送共用 `prepareGenerationRequest` / `GenerationHandler.prepareProviderInput`，因此 **modeInjectionIds 残留在预览 system 中同样可见**。

---

### 7. 相关单测

#### PromptInjectionTransformerTest

**路径**: `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformerTest.kt`（~2082 行）

结构（region）:
- Helpers（createAssistant 含 modeInjectionIds / allowConversation…）
- No injection / disabled / unlinked
- Preset binding（含 allowConversation 时仍注预设）
- Conversation vs assistant mode/lorebook（:262-361）
- 各 InjectionPosition
- Priority / Lorebook / Multiple
- `collectInjections`（:1160-1192）— 明确：linked+enabled 才收集
- `applyInjections` / `findSafeInsertIndex`
- **Preset entries #182**（:1374+）含 “direct binding and reference… inject once”（:1655）
- **Preset migration #182**（:1728+）含:
  - `migratedWithEntries should snapshot old modeInjectionIds as custom entries`
  - `direct binding and preset entry sharing one id should inject only once`（:1843）
  - 混源排序（:1932, :2007）

#### 迁移持久化

**路径**: `app/src/test/java/me/rerere/rikkahub/data/datastore/PresetEntriesMigrationPersistenceTest.kt`

- `migratePresetEntriesIfNeeded` 快照 + 清空 Preset.modeInjectionIds
- 标记不阻塞再导入 legacy
- `withModeInjectionsPreservingPresetSnapshots`
- 无 PRESETS 时生成 Default Preset 再迁移

#### 其他

- `PresetEntrySerializationTest.kt` — migrated 后 modeInjectionIds empty
- `ContextPreviewTest.kt` / `PreparedProviderRequestTest.kt` — 预览契约
- 无专门 “assistant.modeInjectionIds 在迁移后应清空” 的断言（与 issue 现象一致）

---

## 残留注入根因链（事实）

```
旧版：全局 Settings.modeInjections（“快速/模式注入”）
        │
        ├─► 用户/历史可能写入 Assistant.modeInjectionIds（直连绑定）
        │
        ├─► #73 withDefaultPreset：无 presets 存储时，全部 injection id → Default Preset.modeInjectionIds
        │         │
        │         └─► #182 migratedWithEntries / migratePresetEntriesIfNeeded：
        │                   Preset.modeInjectionIds → PresetEntry.Custom 内容快照
        │                   Preset.modeInjectionIds 清空
        │                   Assistant.modeInjectionIds 不变
        │
        └─► 运行时 collectInjections 双路径：
                  A) effectiveModeInjectionIds（默认 = assistant.modeInjectionIds）→ step1 全局列表查内容
                  B) assistant.presetIds → Default/其他 Preset entries（或旧 effectiveInjectionIds）→ step1/1b
                  同一 id 用 injectedIds / deduplicationId 去重，但两路径内容可同时存在（不同 id 或快照副本）
```

**用户关预设 / 世界书 / 记忆后仍见 system 残留**：
1. 路径 A 仍用非空 `assistant.modeInjectionIds` 注入（聊天 UI 无法关）。
2. 若 Default Preset 仍在 `presetIds` 中，路径 B 再注一份 entries 快照（关预设可停 B，停不了 A）。
3. 记忆与 PromptInjection 无关；关记忆不消 mode 注入。

## modeInjectionIds 行为依赖面（清理/去重最小影响面清单）

**运行时读取**:
- `PromptInjectionTransformer.collectInjections` :95-99, :115-125
- `GenerationHandler` 经 conversation IDs 传入（对话级替换路径）

**写入 / 持久化**:
- `AssistantExtensionsPage` 独立注入 UI
- `SettingsStore.updateAssistantInjections`
- Web `SettingsRoutes` / 助手 DTO
- settings flow 无效 ID prune（`PreferencesStore` + `AssistantExtensionIds`）

**展示 / 计数**:
- `FilesPicker` 扩展角标（含 modeInjectionIds.size）
- `ModeInjectionsContent` 组件

**对话级平行**:
- `Conversation.modeInjectionIds` + `allowConversationPromptInjection`
- Web ConversationRoutes 校验/更新
- ConversationRepository 序列化

**迁移相关（只动 Preset，不动 Assistant）**:
- `withDefaultPreset`, `migratedWithEntries`, `migratePresetEntriesIfNeeded`, `withModeInjectionsPreservingPresetSnapshots`

**测试契约依赖 “直连仍注入”**:
- PromptInjectionTransformerTest 大量 `createAssistant(modeInjectionIds = …)`
- 去重用例：`direct binding and preset entry sharing one id should inject only once`

## Caveats / Not Found

- 当前 active task script 返回 `(none)`；本报告写入任务目录 `08-01-07-31-issue-201-mode-injection-residue`（目录已存在）。
- 未找到把 `Assistant.modeInjectionIds` 在 #73/#182 迁移时批量清空或迁入 Default Preset 的代码。
- ExtensionSelector **没有**独立注入 tab；独立注入仅在助手详情扩展页。
- “记忆注入”不经过 PromptInjectionTransformer；issue 文案中的“记忆”与 mode 残留是不同管道。
- 行号基于 2026-08-01 工作区源码；后续改动可能漂移。
