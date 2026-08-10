package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.rikkahub.data.model.LegacyModeInjection
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PresetEntriesMigrationPersistenceTest {
    @Test
    fun `migration snapshots legacy ids to custom and clears mode_injections key`() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            name = "Snapshot",
            content = "snapshot content",
            priority = 7,
        )
        val legacyPreset = Preset(modeInjectionIds = setOf(injection.id))
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(listOf(injection)),
            SettingsStore.PRESETS to JsonInstant.encodeToString(listOf(legacyPreset)),
            SettingsStore.DYNAMIC_COLOR to false,
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertTrue(migrated.hasEntries())
        assertEquals(
            "snapshot content",
            (migrated.entries.single() as PresetEntry.Custom).content,
        )
        assertTrue(migrated.modeInjectionIds.isEmpty())
        assertFalse(preferences[SettingsStore.DYNAMIC_COLOR]!!)
        assertTrue(preferences[SettingsStore.PRESET_ENTRIES_MIGRATED]!!)
        assertNull(preferences[SettingsStore.MODE_INJECTIONS])
        assertFalse(preferences.migratePresetEntriesIfNeeded())
    }

    @Test
    fun `migration marker does not block a later imported legacy preset`() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            content = "imported legacy content",
        )
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(listOf(injection)),
            SettingsStore.PRESETS to JsonInstant.encodeToString(
                listOf(Preset(modeInjectionIds = setOf(injection.id)))
            ),
            SettingsStore.PRESET_ENTRIES_MIGRATED to true,
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertEquals("imported legacy content", migrated.entries.single().let {
            (it as PresetEntry.Custom).content
        })
        assertNull(preferences[SettingsStore.MODE_INJECTIONS])
    }

    @Test
    fun `reference entries snapshot to custom with equivalent content`() {
        val injectionId = Uuid.random()
        val injection = LegacyModeInjection(
            id = injectionId,
            name = "Ref Target",
            content = "referenced body",
            priority = 3,
        )
        val entryId = Uuid.random()
        // Build raw JSON with reference discriminator so sealed decode fails and JSON migrator runs.
        val rawPresets = """
            [{
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
        """.trimIndent()
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(listOf(injection)),
            SettingsStore.PRESETS to rawPresets,
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        val custom = migrated.entries.single() as PresetEntry.Custom
        assertEquals("referenced body", custom.content)
        assertEquals("Ref Target", custom.name)
        assertEquals(entryId, custom.id)
        assertNull(preferences[SettingsStore.MODE_INJECTIONS])
    }

    @Test
    fun `invalid reference is dropped when target missing`() {
        val rawPresets = """
            [{
              "id":"${Uuid.random()}",
              "name":"orphan-ref",
              "description":"",
              "modeInjectionIds":[],
              "disabledEntryIds":[],
              "entries":[{
                "type":"reference",
                "id":"${Uuid.random()}",
                "enabled":true,
                "order":0,
                "position":"after_system_prompt",
                "injectDepth":4,
                "role":"user",
                "modeInjectionId":"${Uuid.random()}"
              }],
              "entriesVersion":1
            }]
        """.trimIndent()
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(emptyList<LegacyModeInjection>()),
            SettingsStore.PRESETS to rawPresets,
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertTrue(migrated.entries.isEmpty())
    }

    @Test
    fun `missing preset store persists the generated default snapshot`() {
        val injection = LegacyModeInjection(
            id = Uuid.random(),
            content = "default snapshot",
        )
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(listOf(injection)),
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertEquals(DEFAULT_PRESET_ID, migrated.id)
        assertEquals(
            "default snapshot",
            (migrated.entries.single() as PresetEntry.Custom).content,
        )
        assertNull(preferences[SettingsStore.MODE_INJECTIONS])
    }

    @Test
    fun `orphan mode injections not referenced by presets are absorbed into default preset`() {
        val referenced = LegacyModeInjection(
            id = Uuid.random(),
            name = "In Preset",
            content = "referenced body",
            priority = 5,
        )
        val orphan = LegacyModeInjection(
            id = Uuid.random(),
            name = "Orphan Only",
            content = "orphan body",
            priority = 1,
        )
        val existingPreset = Preset(
            id = Uuid.random(),
            name = "Existing",
            modeInjectionIds = setOf(referenced.id),
        )
        val preferences = mutablePreferencesOf(
            SettingsStore.MODE_INJECTIONS to JsonInstant.encodeToString(listOf(referenced, orphan)),
            SettingsStore.PRESETS to JsonInstant.encodeToString(listOf(existingPreset)),
        )

        assertTrue(preferences.migratePresetEntriesIfNeeded())

        val migrated = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!)
        assertNull(preferences[SettingsStore.MODE_INJECTIONS])

        val existing = migrated.first { it.id == existingPreset.id }
        assertEquals("referenced body", (existing.entries.single() as PresetEntry.Custom).content)

        val default = migrated.first { it.id == DEFAULT_PRESET_ID }
        assertEquals("orphan body", (default.entries.single() as PresetEntry.Custom).content)
        assertEquals(orphan.id, default.entries.single().id)
    }

    @Test
    fun `invalid presets json does not wipe via migratePresetsJsonWithReferences emptyList`() {
        try {
            migratePresetsJsonWithReferences("not-json-at-all", emptyList())
            org.junit.Assert.fail("expected error on invalid presets JSON")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Failed to parse presets JSON"))
        }
    }

    @Test
    fun `preset update deletes from latest stored state without reverting sibling edits`() {
        val edited = PresetEntry.Custom(content = "latest content")
        val deleteTarget = PresetEntry.Custom(content = "delete me")
        val target = Preset(
            entries = listOf(edited, deleteTarget),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val untouched = Preset(name = "Untouched", entriesVersion = PRESET_ENTRIES_VERSION)
        val staleFallback = target.copy(
            entries = listOf(edited.copy(content = "content before opening page"), deleteTarget)
        )
        val preferences = mutablePreferencesOf(
            SettingsStore.PRESETS to JsonInstant.encodeToString(listOf(target, untouched)),
            SettingsStore.DYNAMIC_COLOR to false,
        )

        assertTrue(
            preferences.writePresetUpdate(
                presetId = target.id,
                fallbackPresets = listOf(staleFallback, untouched),
            ) { latest ->
                latest.copy(entries = latest.entries.filterNot { it.id == deleteTarget.id })
            }
        )

        val stored = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!)
        val updatedTarget = stored.first { it.id == target.id }
        assertEquals(listOf("latest content"), updatedTarget.entries.map { (it as PresetEntry.Custom).content })
        assertEquals(untouched, stored.first { it.id == untouched.id })
        assertFalse(preferences[SettingsStore.DYNAMIC_COLOR]!!)
    }

    @Test
    fun `sequential preset updates compose from the latest persisted value`() {
        val edited = PresetEntry.Custom(content = "before")
        val deleteTarget = PresetEntry.Custom(content = "delete me")
        val preset = Preset(
            entries = listOf(edited, deleteTarget),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val preferences = mutablePreferencesOf(
            SettingsStore.PRESETS to JsonInstant.encodeToString(listOf(preset)),
        )

        assertTrue(
            preferences.writePresetUpdate(preset.id, listOf(preset)) { latest ->
                latest.copy(
                    entries = latest.entries.map {
                        if (it.id == edited.id) (it as PresetEntry.Custom).copy(content = "after") else it
                    }
                )
            }
        )
        assertTrue(
            preferences.writePresetUpdate(preset.id, listOf(preset)) { latest ->
                latest.copy(entries = latest.entries.filterNot { it.id == deleteTarget.id })
            }
        )

        val stored = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertEquals("after", (stored.entries.single() as PresetEntry.Custom).content)
    }
}
