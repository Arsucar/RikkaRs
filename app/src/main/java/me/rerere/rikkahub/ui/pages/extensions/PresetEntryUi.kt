package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptDef
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.inPresetDisplayOrder
import kotlin.uuid.Uuid

internal fun PresetEntry.Builtin.switchBuiltinKey(
    newKey: String,
    definition: BuiltinPromptDef?,
): PresetEntry.Builtin = copy(
    builtinKey = newKey,
    overrideContent = null,
    overridePosition = null,
    position = definition?.defaultPosition ?: position,
    role = definition?.defaultRole ?: role,
)

internal fun BuiltinPromptDef.hasEditorVariables(): Boolean = supportedVariables.isNotEmpty()

/**
 * config-only Builtin：不进入通用对话注入路径（#245 / #182）。
 * `enabled` 仍门控专用功能 override，但不得计入「启用注入条目」展示。
 */
internal fun PresetEntry.isConfigOnlyBuiltin(): Boolean =
    this is PresetEntry.Builtin && BuiltinPromptRegistry[builtinKey]?.injectable != true

/** 是否计入启用条目展示（通用注入语义，排除 config-only Builtin）。 */
internal fun PresetEntry.countsTowardDisplayEntries(): Boolean =
    enabled && !isConfigOnlyBuiltin()

/**
 * entries-aware 条目计数（issue #182 / #245 / #259）。
 *
 * 新 [Preset.entries] 模型下统计已启用且会参与通用注入的条目数；
 * config-only Builtin 不计入。旧模型回退到 [Preset.effectiveInjectionIds] 的大小。
 */
internal fun Preset.displayEntryCount(): Int =
    if (hasEntries()) entries.count { it.countsTowardDisplayEntries() } else effectiveInjectionIds().size

/**
 * entries-aware 已启用条目展示名列表（issue #182 / #245 / #259）。
 *
 * - Custom：使用 name
 * - Builtin：仅 injectable 的 builtinKey
 *
 * 空白名会被过滤。按 order 升序排列。
 */
internal fun Preset.displayEntryNames(): List<String> = if (hasEntries()) {
    entries
        .filter { it.countsTowardDisplayEntries() }
        .inPresetDisplayOrder()
        .mapNotNull { entry ->
            when (entry) {
                is PresetEntry.Custom -> entry.name.takeIf { it.isNotBlank() }
                is PresetEntry.Builtin -> entry.builtinKey.takeIf { it.isNotBlank() }
            }
        }
} else {
    emptyList()
}

/** 同类型条目组内移动；用户显式重排 Custom 后由 order 接管，清除迁移期 legacyPriority。 */
internal fun movePresetEntryInGroup(
    entries: List<PresetEntry>,
    entryId: kotlin.uuid.Uuid,
    delta: Int,
): List<PresetEntry> {
    val targetEntry = entries.firstOrNull { it.id == entryId } ?: return entries
    val group = entries
        .filter { it::class == targetEntry::class }
        .sortedBy { it.order }
    val index = group.indexOfFirst { it.id == entryId }
    val targetIndex = index + delta
    if (index < 0 || targetIndex !in group.indices) return entries

    val reordered = group.toMutableList().apply {
        add(targetIndex, removeAt(index))
    }
    val remapped = reordered.mapIndexed { order, entry ->
        when (entry) {
            is PresetEntry.Custom -> entry.copy(order = order, legacyPriority = null)
            is PresetEntry.Builtin -> entry.copy(order = order)
        }
    }.associateBy { it.id }
    return entries.map { entry -> remapped[entry.id] ?: entry }
}

/**
 * 当前预设尚未占用的内置模板 key（issue #182）。
 */
internal fun availableBuiltinKeys(
    entries: List<PresetEntry>,
    editingEntryId: Uuid? = null,
): List<String> {
    val currentKey = entries
        .filterIsInstance<PresetEntry.Builtin>()
        .firstOrNull { it.id == editingEntryId }
        ?.builtinKey
    val usedByOtherEntries = entries
        .filterIsInstance<PresetEntry.Builtin>()
        .filterNot { it.id == editingEntryId }
        .map { it.builtinKey }
        .toSet()
    return BuiltinPromptRegistry.all.keys.filter { key ->
        key == currentKey || key !in usedByOtherEntries
    }
}

/**
 * 拖拽重排：将 [fromId] 条目移动到 [toId] 条目所在位置（issue #182）。
 */
internal fun reorderPresetEntryByTarget(
    entries: List<PresetEntry>,
    fromId: Uuid,
    toId: Uuid,
): List<PresetEntry> {
    if (fromId == toId) return entries
    val fromEntry = entries.firstOrNull { it.id == fromId } ?: return entries
    val toEntry = entries.firstOrNull { it.id == toId } ?: return entries
    if (fromEntry::class != toEntry::class) return entries

    val group = entries
        .filter { it::class == fromEntry::class }
        .sortedBy { it.order }
    val fromIndex = group.indexOfFirst { it.id == fromId }
    val toIndex = group.indexOfFirst { it.id == toId }
    if (fromIndex < 0 || toIndex < 0) return entries

    val reordered = group.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
    val remapped = reordered.mapIndexed { order, entry ->
        when (entry) {
            is PresetEntry.Custom -> entry.copy(order = order, legacyPriority = null)
            is PresetEntry.Builtin -> entry.copy(order = order)
        }
    }.associateBy { it.id }
    return entries.map { entry -> remapped[entry.id] ?: entry }
}
