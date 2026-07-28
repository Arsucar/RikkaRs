# Technical Design：预设可编辑条目 + System 注入排序（#182）

> 以下代码锚点为主代理调研核对结果（截至 release/rikka-arsucar）。实施子代理须自行 grep 复核行号/签名后再改。

## 现状（已核对）

- `Preset`（`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt:255`）当前仅为 ID 容器：
  `modeInjectionIds: Set<Uuid>` + `disabledEntryIds: Set<Uuid>`，`effectiveInjectionIds() = modeInjectionIds - disabledEntryIds`。
- `PromptInjection` sealed（同文件 :187）：`ModeInjection` / `RegexInjection`，字段 `id/name/enabled/priority/position/content/injectDepth/role`。**无 per-entry order**。
- `InjectionPosition` enum（:163）：5 值，带 `@SerialName`（`before_system_prompt` 等），迁移不可改序列名。
- `PromptInjectionTransformer`（`app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`）：
  - `collectInjections()`：从 `assistant.presetIds` 展开 `preset.effectiveInjectionIds()` → 过滤全局 `modeInjections` → 收集。
  - `transformMessages()`：`sortedByDescending { priority }` 后 `groupBy { position }`。**排序键是 priority，不是 order**。
  - `applyInjections()`：BEFORE/AFTER_SYSTEM 用 `joinToString("\n")` 拼进 system；TOP/BOTTOM/AT_DEPTH 走 `createMergedInjectionMessages` + `findSafeInsertIndex`。
- 内置提示词分散在 `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/`（Suggestion / Compress / TitleSummary / Translation / Ocr / LearningMode 等），**无统一注册表**；回复草稿模板在 `Suggestion.kt`（#177 引入 userInstruction）。
- 预设 UI 在 `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PromptPage.kt`：`PresetTab` + `PresetCard` + `PresetEditSheet`（BottomSheet 内嵌 ModeInjection 开关）。**无独立 PresetDetailPage**。
- 序列化/导出：`data/export/PresetSerializer`（`PromptPage.kt` 已 import）；Settings 存于 DataStore JSON（`data/datastore/PreferencesStore.kt`），旧数据靠字段默认值反序列化。

## 数据模型设计

在 `Assistant.kt`（或新建 `data/model/PresetEntry.kt`）新增：

```kotlin
@Serializable
sealed class PresetEntry {
    abstract val id: Uuid
    abstract val enabled: Boolean
    abstract val order: Int              // 组内排序键（新增，取代 priority 作为预设内排序）
    abstract val position: InjectionPosition
    abstract val injectDepth: Int
    abstract val role: MessageRole

    @Serializable @SerialName("custom")
    data class Custom(... name: String, content: String ...) : PresetEntry()

    @Serializable @SerialName("builtin")
    data class Builtin(... builtinKey: String, overrideContent: String? = null,
                       overridePosition: InjectionPosition? = null ...) : PresetEntry()

    @Serializable @SerialName("reference")
    data class Reference(... modeInjectionId: Uuid ...) : PresetEntry()
}
```

`Preset` 追加 `val entries: List<PresetEntry> = emptyList()`。旧字段保留供迁移。

## 迁移（关键风险点）

- 反序列化兼容：旧 JSON 无 `entries` → 默认 `emptyList()`。需要一处「懒迁移」把旧 `modeInjectionIds/disabledEntryIds` 展开为 `Custom` 快照：
  - 时机建议在读取 Settings 后的规整层（参考 `PreferencesStore` 现有 migration/normalize 逻辑），或首次进入 PresetDetailPage 时。
  - `Custom.enabled = id !in disabledEntryIds`；`content` 从全局 `modeInjections` 里对应项**快照**复制（脱钩全局）。
  - `order` 按原列表顺序赋 0..n。
- 迁移必须幂等：已含 `entries` 的预设不再重复迁移。
- 默认预设追加 Builtin 条目：与现有功能开关对齐（如仅在 workspace 绑定时默认开工作区向导）。

## 注入流程改造

`collectInjections` / `transformMessages` 需要从「ModeInjection ID 展开」改为「PresetEntry 展开」：

1. 遍历 `assistant.presetIds` 命中的 preset，取 `entries.filter { it.enabled }`。
2. 每个 entry 解析为可注入内容：
   - `Custom` → 直接用 content。
   - `Builtin` → 查 `BuiltinPromptRegistry[builtinKey]`，用 `overrideContent ?: default`，DYNAMIC 时替换宏；未知 key 跳过并 log；空 content 跳过。
   - `Reference` → 查全局 `modeInjections` 命中 `modeInjectionId`；被删则跳过（UI 标无效）。
3. 排序键从 `priority` 改为 `order`（同 position 组内）。**这会改变现有行为**：需保留对直接绑定的 ModeInjection（非预设路径）的 priority 排序兼容，或在迁移时把 priority 映射到 order。
4. `applyInjections` 的 system 拼接顺序须严格等于 entry.order（AC2 要求导出上下文可验证）。

`BuiltinPromptRegistry`（新建 `data/ai/prompts/BuiltinPromptRegistry.kt`）：

```kotlin
data class BuiltinPromptDef(
    val key: String, val defaultContent: String, val defaultRole: MessageRole,
    val dynamic: Boolean, val overridable: Boolean, val supportedVariables: List<String>,
)
object BuiltinPromptRegistry { val all: Map<String, BuiltinPromptDef> = ... }
```

至少注册：回复草稿、建议回复、记忆表向导、工作区向导（AC4 最低集）。

## UI 设计

- 新建 `ui/pages/extensions/PresetDetailPage.kt` + `PresetDetailVM`（或复用 `PromptVM`）。
- 导航：`PromptPage` 的 `PresetCard.onClick` 从打开 `PresetEditSheet` 改为导航到 PresetDetailPage（需在 App 导航图注册新 route）。
- 三区块 LazyColumn，各区块内用 `sh.calvin.reorderable`（`PromptPage.kt` 已依赖 `rememberReorderableLazyListState`）做组内拖拽。
- 编辑 BottomSheet 复用现有 `ModeInjectionEditSheet` 交互模式；变量 chips 从 `BuiltinPromptRegistry.supportedVariables` 生成。
- 全套 string 走 `values(-zh)/strings.xml`，key 前缀 `preset_detail_`。

## 兼容性与风险

- **排序语义变更**（priority→order）是最大回归面：直接绑定 ModeInjection 的助手、以及预设内条目，排序结果都可能变。迁移须把现有 priority 映射为稳定 order，并补注入顺序单测锁定行为。
- **序列化向后兼容**：新增 sealed 子类型必须带 `@SerialName`，且 `entries` 有默认值，避免旧 APK 数据反序列化失败。
- **动态宏失败**：降级为空块或默认块，绝不中断生成（AC6）。
- **导出**：`PresetSerializer` 需覆盖新 `entries` 字段；跨版本导入旧格式走同一迁移。

## 建议实施拆分（供 implement.md）

数据/domain（可先行、可测）→ 注入 transformer 改造（依赖数据模型）→ 迁移 + 序列化兼容（依赖数据模型）→ UI PresetDetailPage（依赖数据模型，UI 独立）→ 单测 + 本地化 + 联调。
