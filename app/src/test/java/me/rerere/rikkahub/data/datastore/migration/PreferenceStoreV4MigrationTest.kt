package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferenceStoreV4MigrationTest {
    @Test
    fun migrationCommitsAssistantsVersionAndLegacyKeyTogether() = runBlocking {
        val current = mutablePreferencesOf(
            SettingsStore.VERSION to 3,
            SettingsStore.ENABLE_WEB_SEARCH to true,
            SettingsStore.ASSISTANTS to """[{"name":"one"}]""",
        )

        val migrated = PreferenceStoreV4Migration().migrate(current)

        assertEquals(4, migrated[SettingsStore.VERSION])
        assertEquals(null, migrated[SettingsStore.ENABLE_WEB_SEARCH])
        val assistants = JsonInstant.parseToJsonElement(
            checkNotNull(migrated[SettingsStore.ASSISTANTS])
        ) as JsonArray
        assertEquals(true, assistants.single().jsonObject
            .getValue("enableWebSearch").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun legacyValueIsAppliedToAssistantsMissingTheField() {
        val migrated = migrateAssistantWebSearch(
            """[{"name":"one"},{"name":"two","enableWebSearch":false}]""",
            legacyEnabled = true,
        )
        val assistants = JsonInstant.parseToJsonElement(migrated) as JsonArray
        assertEquals(true, assistants[0].jsonObject["enableWebSearch"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(false, assistants[1].jsonObject["enableWebSearch"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun emptyAssistantStorageMigratesAndMalformedStorageFailsForRetry() {
        assertEquals("[]", migrateAssistantWebSearch("[]", legacyEnabled = true))
        assertThrows(Exception::class.java) {
            migrateAssistantWebSearch("not-json", legacyEnabled = true)
        }
    }
}
