package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import kotlin.uuid.Uuid

fun Settings.withPrunedAssistantExtensionIds(): Settings {
    val validQuickMessageIds = quickMessages.map { it.id }.toSet()
    val validModeInjectionIds = modeInjections.map { it.id }.toSet()
    val validLorebookIds = lorebooks.map { it.id }.toSet()
    val validPresetIds = presets.map { it.id }.toSet()
    return copy(
        assistants = assistants.map { assistant ->
            assistant.copy(
                quickMessageIds = assistant.quickMessageIds.filter { it in validQuickMessageIds }.toSet(),
                modeInjectionIds = assistant.modeInjectionIds.filter { it in validModeInjectionIds }.toSet(),
                lorebookIds = assistant.lorebookIds.filter { it in validLorebookIds }.toSet(),
                presetIds = assistant.presetIds.filter { it in validPresetIds }.toSet(),
            )
        },
    )
}

fun Assistant.pruneExtensionIds(settings: Settings): Assistant {
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validPresetIds = settings.presets.map { it.id }.toSet()
    return copy(
        quickMessageIds = quickMessageIds.filter { it in validQuickMessageIds }.toSet(),
        modeInjectionIds = modeInjectionIds.filter { it in validModeInjectionIds }.toSet(),
        lorebookIds = lorebookIds.filter { it in validLorebookIds }.toSet(),
        presetIds = presetIds.filter { it in validPresetIds }.toSet(),
    )
}

/**
 * #201: 去掉与已绑定 preset 的 entry id（及未迁移旧 [Preset.modeInjectionIds]）重复的直连，
 * 消除「preset 路径 + assistant 直连」双路径残留；与 preset 无关的独立注入保留。
 */
internal fun Assistant.withModeInjectionsDedupedAgainstPresets(
    presets: List<Preset>,
    validModeInjectionIds: Set<Uuid>,
): Assistant {
    val boundPresetEntryIds = boundPresetInjectionIds(presetIds, presets)
    return copy(
        modeInjectionIds = modeInjectionIds
            .filter { it !in boundPresetEntryIds }
            .filter { it in validModeInjectionIds }
            .toSet(),
    )
}

internal fun boundPresetInjectionIds(
    presetIds: Set<Uuid>,
    presets: List<Preset>,
): Set<Uuid> = presetIds
    .flatMap { presetId ->
        val preset = presets.firstOrNull { it.id == presetId } ?: return@flatMap emptyList()
        // 只统计「实际经 preset 路径投递」的 id（#201）：
        // - 已迁移（entries）：仅启用条目算重复（禁用的条目不会注入，直连是唯一生效路径，须保留）；
        //   Reference 条目额外计入其引用的全局 id，消除 reference 路径双注入残留。
        // - 未迁移（旧 modeInjectionIds）：扣除 disabledEntryIds（effectiveInjectionIds）后同样算重复。
        val entryIds = preset.entries
            .filter { it.enabled }
            .flatMap { entry ->
                when (entry) {
                    is PresetEntry.Reference -> listOf(entry.id, entry.modeInjectionId)
                    else -> listOf(entry.id)
                }
            }
        entryIds + preset.effectiveInjectionIds()
    }
    .toSet()
