package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PRESET_ENTRIES_VERSION
import me.rerere.rikkahub.data.model.Preset
import me.rerere.rikkahub.data.model.PresetEntry
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.migratedWithEntries
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * #201: assistant.modeInjectionIds 与已绑定 preset entries 同 id 时清理直连，
 * 与 preset 无关的独立注入保留；幂等。
 */
class ModeInjectionDirectBindingDedupeTest {
    @Test
    fun `dedupe removes direct ids that match bound migrated preset entry ids`() {
        val sharedId = Uuid.random()
        val independentId = Uuid.random()
        val injections = listOf(
            PromptInjection.ModeInjection(id = sharedId, content = "from preset"),
            PromptInjection.ModeInjection(id = independentId, content = "independent"),
        )
        val preset = Preset(modeInjectionIds = setOf(sharedId))
            .migratedWithEntries(injections)
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId, independentId),
            presetIds = setOf(preset.id),
        )
        val validIds = injections.map { it.id }.toSet()

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = validIds,
        )

        assertEquals(setOf(independentId), cleaned.modeInjectionIds)
        assertTrue(preset.entries.any { it.id == sharedId })
    }

    @Test
    fun `dedupe removes direct ids that match bound legacy preset modeInjectionIds`() {
        val sharedId = Uuid.random()
        val independentId = Uuid.random()
        val legacyPreset = Preset(modeInjectionIds = setOf(sharedId))
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId, independentId),
            presetIds = setOf(legacyPreset.id),
        )
        val validIds = setOf(sharedId, independentId)

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(legacyPreset),
            validModeInjectionIds = validIds,
        )

        assertEquals(setOf(independentId), cleaned.modeInjectionIds)
        assertFalse(legacyPreset.hasEntries())
    }

    @Test
    fun `dedupe keeps direct id when bound preset entry is disabled`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap", enabled = false)),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = setOf(preset.id),
        )

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = setOf(sharedId),
        )

        assertEquals(setOf(sharedId), cleaned.modeInjectionIds)
    }

    @Test
    fun `dedupe keeps direct id disabled in legacy preset`() {
        val sharedId = Uuid.random()
        val legacyPreset = Preset(
            modeInjectionIds = setOf(sharedId),
            disabledEntryIds = setOf(sharedId),
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = setOf(legacyPreset.id),
        )

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(legacyPreset),
            validModeInjectionIds = setOf(sharedId),
        )

        assertEquals(setOf(sharedId), cleaned.modeInjectionIds)
    }

    @Test
    fun `dedupe removes direct id referenced by bound preset reference entry`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Reference(modeInjectionId = sharedId)),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = setOf(preset.id),
        )

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = setOf(sharedId),
        )

        assertTrue(cleaned.modeInjectionIds.isEmpty())
    }

    @Test
    fun `dedupe keeps direct id when preset entry is disabled but reference entry enabled`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(
                PresetEntry.Custom(id = sharedId, content = "snap", enabled = false),
                PresetEntry.Reference(modeInjectionId = sharedId, enabled = true),
            ),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = setOf(preset.id),
        )

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = setOf(sharedId),
        )

        assertTrue(cleaned.modeInjectionIds.isEmpty())
    }

    @Test
    fun `dedupe keeps direct ids when assistant does not bind the preset`() {
        val sharedId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snapshot")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId),
            presetIds = emptySet(),
        )

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = setOf(sharedId),
        )

        assertEquals(setOf(sharedId), cleaned.modeInjectionIds)
    }

    @Test
    fun `dedupe drops invalid mode injection ids`() {
        val validId = Uuid.random()
        val orphanId = Uuid.random()
        val assistant = Assistant(modeInjectionIds = setOf(validId, orphanId))

        val cleaned = assistant.withModeInjectionsDedupedAgainstPresets(
            presets = emptyList(),
            validModeInjectionIds = setOf(validId),
        )

        assertEquals(setOf(validId), cleaned.modeInjectionIds)
    }

    @Test
    fun `dedupe is idempotent`() {
        val sharedId = Uuid.random()
        val independentId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId, independentId),
            presetIds = setOf(preset.id),
        )
        val validIds = setOf(sharedId, independentId)

        val once = assistant.withModeInjectionsDedupedAgainstPresets(listOf(preset), validIds)
        val twice = once.withModeInjectionsDedupedAgainstPresets(listOf(preset), validIds)

        assertEquals(setOf(independentId), once.modeInjectionIds)
        assertEquals(once.modeInjectionIds, twice.modeInjectionIds)
    }

    @Test
    fun `cleaned modeInjectionIds round-trip through assistant serialization`() {
        val sharedId = Uuid.random()
        val independentId = Uuid.random()
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(id = sharedId, content = "snap")),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(sharedId, independentId),
            presetIds = setOf(preset.id),
        ).withModeInjectionsDedupedAgainstPresets(
            presets = listOf(preset),
            validModeInjectionIds = setOf(sharedId, independentId),
        )

        val decoded = JsonInstant.decodeFromString<Assistant>(JsonInstant.encodeToString(assistant))

        assertEquals(setOf(independentId), decoded.modeInjectionIds)
        assertEquals(setOf(preset.id), decoded.presetIds)
    }
}
