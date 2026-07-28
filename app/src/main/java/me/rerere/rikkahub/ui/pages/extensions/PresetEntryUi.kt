package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptDef
import me.rerere.rikkahub.data.ai.prompts.BuiltinPromptRegistry
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.inPresetDisplayOrder
import kotlin.uuid.Uuid

internal fun PresetEntry.Reference.hasValidTarget(
    modeInjections: List<PromptInjection.ModeInjection>,
): Boolean = modeInjections.any { it.id == modeInjectionId }

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
 * entries-aware 条目计数（issue #182）。
 *
 * 新 [Preset.entries] 模型下统计已启用条目数；旧模型（无 entries）回退到
 * [Preset.effectiveInjectionIds] 的大小。避免对新模型恒显示 0。
 */
internal fun Preset.displayEntryCount(): Int =
    if (hasEntries()) entries.count { it.enabled } else effectiveInjectionIds().size

/**
 * entries-aware 已启用条目展示名列表（issue #182）。
 *
 * - Custom：使用 name
 * - Builtin：使用 builtinKey
 * - Reference：解析全局 [modeInjections] 中对应注入的 name
 *
 * 空白名会被过滤。按 order 升序排列。旧模型回退到 [Preset.effectiveInjectionIds]
 * 对应的全局注入名。
 */
internal fun Preset.displayEntryNames(
    modeInjections: List<PromptInjection.ModeInjection>,
): List<String> = if (hasEntries()) {
    entries
        .filter { it.enabled }
        .inPresetDisplayOrder()
        .mapNotNull { entry ->
            when (entry) {
                is PresetEntry.Custom -> entry.name.takeIf { it.isNotBlank() }
                is PresetEntry.Builtin -> entry.builtinKey.takeIf { it.isNotBlank() }
                is PresetEntry.Reference -> modeInjections
                    .firstOrNull { it.id == entry.modeInjectionId }
                    ?.name
                    ?.takeIf { it.isNotBlank() }
            }
        }
} else {
    modeInjections
        .filter { it.id in effectiveInjectionIds() }
        .map { it.name }
        .filter { it.isNotBlank() }
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
            is PresetEntry.Reference -> entry.copy(order = order)
        }
    }.associateBy { it.id }
    return entries.map { entry -> remapped[entry.id] ?: entry }
}

/**
 * 当前预设尚未占用的内置模板 key（issue #182）。
 *
 * 返回 [BuiltinPromptRegistry.all] 中未被其他（启用或禁用）[PresetEntry.Builtin] 条目占用的 key，
 * 按注册表声明顺序返回。新增时排除全部已占用 key；编辑时通过 [editingEntryId] 保留当前 key，
 * 同时排除兄弟条目已占用的 key。
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
 *
 * 仅当两条目属于同一子类型（都 Custom 或都 Builtin/Reference）时才在该组内重排并
 * 重算 order（0..n）；跨组或找不到任一条目时原样返回。用户显式重排 Custom 后清除
 * 迁移期 legacyPriority（与 [movePresetEntryInGroup] 一致），其他组条目原位不动。
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
            is PresetEntry.Reference -> entry.copy(order = order)
        }
    }.associateBy { it.id }
    return entries.map { entry -> remapped[entry.id] ?: entry }
}
