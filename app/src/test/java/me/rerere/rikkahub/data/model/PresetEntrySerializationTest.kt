package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PresetEntrySerializationTest {
    @Test
    fun `entries model round trips all entry variants`() {
        val preset = Preset(
            entriesVersion = PRESET_ENTRIES_VERSION,
            entries = listOf(
                PresetEntry.Builtin(builtinKey = "suggestion", overrideContent = "custom"),
                PresetEntry.Custom(name = "custom", content = "body", legacyPriority = 8),
                PresetEntry.Reference(modeInjectionId = Uuid.random(), role = MessageRole.ASSISTANT),
            ),
        )

        val decoded = JsonInstant.decodeFromString<Preset>(JsonInstant.encodeToString(preset))

        assertEquals(preset, decoded)
        assertTrue(decoded.hasEntries())
    }

    @Test
    fun `old entries json without version or legacy priority remains compatible`() {
        val preset = Preset(
            entries = listOf(PresetEntry.Custom(name = "old", content = "body")),
        )
        val encoded = JsonInstant.encodeToString(preset)
        val oldJson = encoded
            .replace(Regex(",?\"entriesVersion\":0"), "")
            .replace(Regex(",?\"legacyPriority\":null"), "")

        val decoded = JsonInstant.decodeFromString<Preset>(oldJson)

        assertTrue(decoded.hasEntries())
        assertFalse(decoded.entries.isEmpty())
        assertNull((decoded.entries.single() as PresetEntry.Custom).legacyPriority)
    }

    @Test
    fun `empty entries model does not fall back to legacy ids`() {
        val preset = Preset(
            modeInjectionIds = setOf(Uuid.random()),
            entriesVersion = PRESET_ENTRIES_VERSION,
        )

        assertTrue(preset.hasEntries())
        assertTrue(preset.entries.isEmpty())
    }

    @Test
    fun `old non-empty entries are normalized to explicit version`() {
        val preset = Preset(
            modeInjectionIds = setOf(Uuid.random()),
            entries = listOf(PresetEntry.Custom(content = "existing snapshot")),
        )

        val normalized = preset.migratedWithEntries(emptyList())

        assertEquals(PRESET_ENTRIES_VERSION, normalized.entriesVersion)
        assertTrue(normalized.modeInjectionIds.isEmpty())
        assertEquals("existing snapshot", (normalized.entries.single() as PresetEntry.Custom).content)
    }

    @Test
    fun `draftContext missing from json stays null for backward compatibility`() {
        val base = Preset(name = "legacy")
        val encoded = JsonInstant.encodeToString(base)
            .replace(Regex(",?\"draftContext\":null"), "")

        val decoded = JsonInstant.decodeFromString<Preset>(encoded)

        assertNull(decoded.draftContext)
        assertEquals("legacy", decoded.name)
    }

    @Test
    fun `draftContext exports with preset`() {
        val preset = Preset(
            name = "export",
            draftContext = DraftContextConfig(messageCount = 4, includeMedia = true),
        )
        val json = JsonInstant.encodeToString(preset)
        assertTrue(json.contains("\"draftContext\""))
        assertTrue(json.contains("\"messageCount\":4"))
        val decoded = JsonInstant.decodeFromString<Preset>(json)
        assertEquals(4, decoded.draftContext?.messageCount)
        assertTrue(decoded.draftContext?.includeMedia == true)
    }
}
