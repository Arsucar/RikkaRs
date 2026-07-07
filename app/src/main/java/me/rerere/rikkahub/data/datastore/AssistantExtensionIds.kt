package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant

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
