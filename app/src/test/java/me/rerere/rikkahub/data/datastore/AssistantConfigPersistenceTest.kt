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

    @Test
    fun assistantConfigWriterUpdatesSearchForOnlyTheTargetAssistant() {
        val target = Assistant(id = Uuid.random(), enableWebSearch = false)
        val untouched = Assistant(id = Uuid.random(), enableWebSearch = false)
        val preferences = mutablePreferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(target, untouched)),
        )

        preferences.writeAssistantConfig(target.copy(enableWebSearch = true), emptyList())

        val assistants = JsonInstant.decodeFromString<List<Assistant>>(
            checkNotNull(preferences[SettingsStore.ASSISTANTS])
        )
        assertTrue(assistants.first { it.id == target.id }.enableWebSearch)
        assertFalse(assistants.first { it.id == untouched.id }.enableWebSearch)
    }

    @Test
    fun assistantConfigWriterPersistsWorkspaceBindingTransitionsOnlyForTarget() {
        val workspaceA = Uuid.random()
        val workspaceB = Uuid.random()
        val target = Assistant(id = Uuid.random(), workspaceId = null)
        val untouched = Assistant(id = Uuid.random(), workspaceId = workspaceA)
        val transitions = listOf<Uuid?>(workspaceA, null, workspaceB)
        val preferences = mutablePreferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(target, untouched)),
        )

        transitions.forEach { workspaceId ->
            val current = JsonInstant.decodeFromString<List<Assistant>>(
                checkNotNull(preferences[SettingsStore.ASSISTANTS])
            ).first { it.id == target.id }
            preferences.writeAssistantConfig(current.copy(workspaceId = workspaceId), emptyList())
            val stored = JsonInstant.decodeFromString<List<Assistant>>(
                checkNotNull(preferences[SettingsStore.ASSISTANTS])
            )
            assertEquals(workspaceId, stored.first { it.id == target.id }.workspaceId)
            assertEquals(workspaceA, stored.first { it.id == untouched.id }.workspaceId)
        }
    }

    @Test
    fun workspaceBindingWriterUsesLatestStoredAssistantAndChangesOnlyWorkspace() {
        val assistantId = Uuid.random()
        val oldWorkspace = Uuid.random()
        val newWorkspace = Uuid.random()
        val staleSnapshot = Assistant(id = assistantId, name = "Stale", workspaceId = oldWorkspace)
        val latestStored = staleSnapshot.copy(name = "Latest", enableWebSearch = true)
        val preferences = mutablePreferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(latestStored)),
        )

        val result = preferences.writeAssistantWorkspaceBinding(
            assistantId = assistantId,
            workspaceId = newWorkspace,
            fallbackAssistants = listOf(staleSnapshot),
        )

        val stored = JsonInstant.decodeFromString<List<Assistant>>(
            checkNotNull(preferences[SettingsStore.ASSISTANTS])
        ).single()
        assertEquals(AssistantWorkspaceBindingUpdateResult.UPDATED, result)
        assertEquals("Latest", stored.name)
        assertTrue(stored.enableWebSearch)
        assertEquals(newWorkspace, stored.workspaceId)
    }

    @Test
    fun workspaceBindingWriterReportsMissingAssistantWithoutWriting() {
        val stored = Assistant(id = Uuid.random(), name = "Stored")
        val originalJson = JsonInstant.encodeToString(listOf(stored))
        val preferences = mutablePreferencesOf(SettingsStore.ASSISTANTS to originalJson)

        val result = preferences.writeAssistantWorkspaceBinding(
            assistantId = Uuid.random(),
            workspaceId = Uuid.random(),
            fallbackAssistants = emptyList(),
        )

        assertEquals(AssistantWorkspaceBindingUpdateResult.NOT_FOUND, result)
        assertEquals(originalJson, preferences[SettingsStore.ASSISTANTS])
    }
}
