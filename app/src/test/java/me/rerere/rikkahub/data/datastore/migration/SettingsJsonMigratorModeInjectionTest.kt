package me.rerere.rikkahub.data.datastore.migration

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.LegacyModeInjection
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsJsonMigratorModeInjectionTest {

    @Test
    fun migrate_snapshotsReferencesAndAbsorbsOrphans_removesModeInjections() {
        val injectionId = Uuid.random()
        val orphanId = Uuid.random()
        val entryId = Uuid.random()
        val injection = LegacyModeInjection(
            id = injectionId,
            name = "Ref Target",
            content = "referenced body",
            priority = 3,
        )
        val orphan = LegacyModeInjection(
            id = orphanId,
            name = "Orphan",
            content = "orphan body",
            priority = 1,
        )
        val assistantId = Uuid.random()
        val raw = """
            {
              "modeInjections": ${JsonInstant.encodeToString(listOf(injection, orphan))},
              "assistants":[{
                "id":"$assistantId",
                "name":"a",
                "modeInjectionIds":["$orphanId"],
                "allowConversationPromptInjection":true
              }],
              "presets":[{
                "id":"${Uuid.random()}",
                "name":"with-ref",
                "description":"",
                "modeInjectionIds":[],
                "disabledEntryIds":[],
                "entries":[{
                  "type":"reference",
                  "id":"$entryId",
                  "enabled":true,
                  "order":0,
                  "position":"after_system_prompt",
                  "injectDepth":4,
                  "role":"user",
                  "modeInjectionId":"$injectionId"
                }],
                "entriesVersion":1
              }]
            }
        """.trimIndent()

        val migrated = SettingsJsonMigrator.migrate(raw)
        val root = JsonInstant.parseToJsonElement(migrated).jsonObject

        assertFalse("modeInjections" in root)
        val assistants = root.getValue("assistants").jsonArray
        assertFalse("modeInjectionIds" in assistants.single().jsonObject)
        assertFalse("allowConversationPromptInjection" in assistants.single().jsonObject)

        val presets = JsonInstant.decodeFromString<List<Preset>>(
            JsonInstant.encodeToString(root.getValue("presets")),
        )
        assertTrue(presets.isNotEmpty())
        val allCustom = presets.flatMap { it.entries }.filterIsInstance<PresetEntry.Custom>()
        assertTrue(allCustom.any { it.content == "referenced body" && it.id == entryId })
        assertTrue(allCustom.any { it.content == "orphan body" && it.id == orphanId })
        assertTrue(presets.all { it.entriesVersion >= PRESET_ENTRIES_VERSION })
        assertTrue(presets.all { it.modeInjectionIds.isEmpty() })
        // Restored presets must be sealed-decodable (no reference discriminator left).
        assertEquals(presets.size, root.getValue("presets").jsonArray.size)
    }

    @Test
    fun migrate_createsDefaultPresetWhenOnlyModeInjectionsPresent() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            content = "solo body",
        )
        val raw = """
            {
              "modeInjections": ${JsonInstant.encodeToString(listOf(injection))},
              "assistants":[{"name":"only"}]
            }
        """.trimIndent()

        val migrated = SettingsJsonMigrator.migrate(raw)
        val root = JsonInstant.parseToJsonElement(migrated).jsonObject
        assertFalse("modeInjections" in root)
        val presets = JsonInstant.decodeFromString<List<Preset>>(
            JsonInstant.encodeToString(root.getValue("presets")),
        )
        assertEquals(1, presets.size)
        assertEquals("solo body", (presets.single().entries.single() as PresetEntry.Custom).content)
    }
}
