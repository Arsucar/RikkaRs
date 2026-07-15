package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[SettingsStore.VERSION] ?: 0) < 4

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[SettingsStore.ASSISTANTS] = migrateAssistantWebSearch(
            assistantsJson = prefs[SettingsStore.ASSISTANTS] ?: "[]",
            legacyEnabled = prefs[SettingsStore.ENABLE_WEB_SEARCH] == true,
        )
        prefs.remove(SettingsStore.ENABLE_WEB_SEARCH)
        prefs[SettingsStore.VERSION] = 4
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

internal fun migrateAssistantWebSearch(assistantsJson: String, legacyEnabled: Boolean): String =
    JsonInstant.encodeToString(
        JsonArray(
            (JsonInstant.parseToJsonElement(assistantsJson) as JsonArray).map { element ->
                val assistant = element as? JsonObject ?: return@map element
                if ("enableWebSearch" in assistant) assistant else JsonObject(
                    assistant.toMutableMap().apply {
                        put("enableWebSearch", JsonPrimitive(legacyEnabled))
                    }
                )
            }
        )
    )
