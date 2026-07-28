package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PresetEntriesMigrationPersistenceTest {
    @Test
    fun `migration snapshots entries and preserves unrelated preferences`() {
        val injection = PromptInjection.ModeInjection(
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
        assertFalse(preferences.migratePresetEntriesIfNeeded())
    }

    @Test
    fun `migration marker does not block a later imported legacy preset`() {
        val injection = PromptInjection.ModeInjection(
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
    }

    @Test
    fun `global injection deletion snapshots legacy presets first`() {
        val injection = PromptInjection.ModeInjection(
            id = Uuid.random(),
            content = "must survive deletion",
        )
        val settings = Settings(
            modeInjections = listOf(injection),
            presets = listOf(Preset(modeInjectionIds = setOf(injection.id))),
        )

        val updated = settings.withModeInjectionsPreservingPresetSnapshots(emptyList())

        assertTrue(updated.modeInjections.isEmpty())
        assertEquals(
            "must survive deletion",
            (updated.presets.single().entries.single() as PresetEntry.Custom).content,
        )
    }

    @Test
    fun `missing preset store persists the generated default snapshot`() {
        val injection = PromptInjection.ModeInjection(
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
                fallbackModeInjections = emptyList(),
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
            preferences.writePresetUpdate(preset.id, listOf(preset), emptyList()) { latest ->
                latest.copy(
                    entries = latest.entries.map {
                        if (it.id == edited.id) (it as PresetEntry.Custom).copy(content = "after") else it
                    }
                )
            }
        )
        assertTrue(
            preferences.writePresetUpdate(preset.id, listOf(preset), emptyList()) { latest ->
                latest.copy(entries = latest.entries.filterNot { it.id == deleteTarget.id })
            }
        )

        val stored = JsonInstant.decodeFromString<List<Preset>>(preferences[SettingsStore.PRESETS]!!).single()
        assertEquals("after", (stored.entries.single() as PresetEntry.Custom).content)
    }
}
