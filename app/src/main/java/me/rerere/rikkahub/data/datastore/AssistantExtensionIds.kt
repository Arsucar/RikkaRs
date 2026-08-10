package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Preset
import kotlin.uuid.Uuid

fun Settings.withPrunedAssistantExtensionIds(): Settings {
    val validQuickMessageIds = quickMessages.map { it.id }.toSet()
    val validLorebookIds = lorebooks.map { it.id }.toSet()
    val validPresetIds = presets.map { it.id }.toSet()
    return copy(
        assistants = assistants.map { assistant ->
            assistant.copy(
                quickMessageIds = assistant.quickMessageIds.filter { it in validQuickMessageIds }.toSet(),
                lorebookIds = assistant.lorebookIds.filter { it in validLorebookIds }.toSet(),
                presetIds = assistant.presetIds.filter { it in validPresetIds }.toSet(),
            )
        },
    )
}

fun Assistant.pruneExtensionIds(settings: Settings): Assistant {
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validPresetIds = settings.presets.map { it.id }.toSet()
    return copy(
        quickMessageIds = quickMessageIds.filter { it in validQuickMessageIds }.toSet(),
        lorebookIds = lorebookIds.filter { it in validLorebookIds }.toSet(),
        presetIds = presetIds.filter { it in validPresetIds }.toSet(),
    )
}

/**
 * 计算「经预设路径投递」的注入 id 集合（#182 entries 语义）：
 * - 已迁移（entries）：仅启用条目的 entry.id
 * - 未迁移（旧 modeInjectionIds）：扣除 disabledEntryIds 后同样算
 *
 * #259: 不再用于直连去重（直连路径已删）；保留给 UI 计数/展示若需要。
 */
internal fun boundPresetInjectionIds(
    presetIds: Set<Uuid>,
    presets: List<Preset>,
): Set<Uuid> = presetIds
    .flatMap { presetId ->
        val preset = presets.firstOrNull { it.id == presetId } ?: return@flatMap emptyList()
        val entryIds = preset.entries
            .filter { it.enabled }
            .map { it.id }
        entryIds + preset.effectiveInjectionIds()
    }
    .toSet()
