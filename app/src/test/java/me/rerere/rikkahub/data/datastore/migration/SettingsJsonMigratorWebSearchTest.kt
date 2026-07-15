package me.rerere.rikkahub.data.datastore.migration

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsJsonMigratorWebSearchTest {
    @Test
    fun oldBackupRootFlagMovesToEveryAssistant() {
        val migrated = SettingsJsonMigrator.migrate(
            """{"enableWebSearch":true,"assistants":[{"name":"one"},{"name":"two"}]}"""
        )
        val root = JsonInstant.parseToJsonElement(migrated).jsonObject
        assertFalse("enableWebSearch" in root)
        assertEquals(
            listOf(true, true),
            root.getValue("assistants").jsonArray.map {
                it.jsonObject.getValue("enableWebSearch").jsonPrimitive.content.toBoolean()
            },
        )
    }
}
