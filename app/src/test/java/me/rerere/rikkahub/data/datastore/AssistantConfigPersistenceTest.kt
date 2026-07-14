package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantConfigPersistenceTest {
    @Test
    fun assistantConfigWriterReplacesOnlyTheTargetAssistant() {
        val target = Assistant(id = Uuid.random(), name = "Before")
        val untouched = Assistant(id = Uuid.random(), name = "Untouched")
        val preferences = mutablePreferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(target, untouched)),
            SettingsStore.COMPRESS_TARGET_TOKENS to 3_333,
            SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES to 21,
            SettingsStore.SELECT_ASSISTANT to untouched.id.toString(),
        )

        preferences.writeAssistantConfig(
            assistant = target.copy(
                name = "After",
                autoCompressEnabled = true,
                autoCompressThresholdTokens = 12_000,
                autoCompressKeepRecentMessages = 8,
            ),
            fallbackAssistants = emptyList(),
        )

        val assistants = JsonInstant.decodeFromString<List<Assistant>>(
            checkNotNull(preferences[SettingsStore.ASSISTANTS])
        )
        assertEquals("After", assistants.first { it.id == target.id }.name)
        assertTrue(assistants.first { it.id == target.id }.autoCompressEnabled)
        assertEquals(untouched, assistants.first { it.id == untouched.id })
    }

    @Test
    fun assistantConfigWriterPreservesUnrelatedPreferenceKeys() {
        val target = Assistant(id = Uuid.random(), name = "Before")
        val selectedId = Uuid.random()
        val preferences = mutablePreferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(target)),
            SettingsStore.COMPRESS_TARGET_TOKENS to 4_444,
            SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES to 17,
            SettingsStore.SELECT_ASSISTANT to selectedId.toString(),
        )

        preferences.writeAssistantConfig(
            assistant = target.copy(name = "After"),
            fallbackAssistants = emptyList(),
        )

        assertEquals(4_444, preferences[SettingsStore.COMPRESS_TARGET_TOKENS])
        assertEquals(17, preferences[SettingsStore.COMPRESS_KEEP_RECENT_MESSAGES])
        assertEquals(selectedId.toString(), preferences[SettingsStore.SELECT_ASSISTANT])
    }

    @Test
    fun assistantConfigWriterUsesFallbackOnlyWhenAssistantStorageIsAbsent() {
        val target = Assistant(id = Uuid.random(), name = "Before")
        val preferences = mutablePreferencesOf()

        preferences.writeAssistantConfig(
            assistant = target.copy(name = "After"),
            fallbackAssistants = listOf(target),
        )

        val assistants = JsonInstant.decodeFromString<List<Assistant>>(
            checkNotNull(preferences[SettingsStore.ASSISTANTS])
        )
        assertEquals(listOf("After"), assistants.map { it.name })
        assertFalse(assistants.single().autoCompressEnabled)
    }
}
