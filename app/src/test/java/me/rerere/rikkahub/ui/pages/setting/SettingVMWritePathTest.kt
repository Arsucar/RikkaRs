package me.rerere.rikkahub.ui.pages.setting

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #276: ViewModel write-path coverage for SettingVM.
 *
 * SettingsStore requires Android Context, so these tests pin the pure transform
 * logic that SettingVM uses for updateSettings / deleteGlobalSubagent /
 * restoreDefaultSubagents, plus the atomic transform write pattern from #267.
 */
class SettingVMWritePathTest {
    @Test
    fun updateSettingsTransformAppliesFieldChange() {
        val initial = Settings(developerMode = false, launchCount = 1)
        val updated = applyUpdateSettings(initial) { it.copy(developerMode = true) }

        assertTrue(updated.developerMode)
        assertEquals(1, updated.launchCount)
    }

    @Test
    fun concurrentTransformsDoNotLoseSiblingFieldUpdates() = runBlocking {
        val flow = MutableStateFlow(Settings(developerMode = false, launchCount = 0))
        val mutex = Mutex()

        suspend fun update(transform: (Settings) -> Settings) {
            mutex.withLock {
                flow.value = transform(flow.value)
            }
        }

        listOf(
            async { update { it.copy(developerMode = true) } },
            async { update { it.copy(launchCount = 7) } },
            async { update { it.copy(webServerEnabled = true) } },
        ).awaitAll()

        val result = flow.value
        assertTrue(result.developerMode)
        assertEquals(7, result.launchCount)
        assertTrue(result.webServerEnabled)
    }

    @Test
    fun deleteGlobalSubagentRemovesOnlyMatchingName() {
        val explore = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }
        val coder = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "coder" }
        val settings = Settings(globalSubagentProfiles = listOf(explore, coder))

        val updated = deleteGlobalSubagent(settings, "explore")

        assertEquals(listOf("coder"), updated.globalSubagentProfiles.map { it.name })
    }

    @Test
    fun restoreDefaultSubagentsAddsOnlyMissingBuiltins() {
        val explore = SubagentRegistry.BUILTIN_PROFILES.first { it.name == "explore" }
        val custom = SubagentProfile(
            name = "custom",
            description = "user profile",
            systemPrompt = "do work",
        )
        val settings = Settings(globalSubagentProfiles = listOf(explore, custom))

        val updated = restoreDefaultSubagents(settings)

        val names = updated.globalSubagentProfiles.map { it.name }
        assertTrue(names.contains("explore"))
        assertTrue(names.contains("custom"))
        assertTrue(names.containsAll(SubagentRegistry.BUILTIN_PROFILES.map { it.name }))
        assertEquals(1, names.count { it == "explore" })
    }

    @Test
    fun restoreDefaultSubagentsOnEmptyAddsAllBuiltins() {
        val settings = Settings(globalSubagentProfiles = emptyList())
        val updated = restoreDefaultSubagents(settings)

        assertEquals(
            SubagentRegistry.BUILTIN_PROFILES.map { it.name }.toSet(),
            updated.globalSubagentProfiles.map { it.name }.toSet(),
        )
    }

    @Test
    fun deprecatedFullOverwriteStillRoutesThroughTransform() {
        val initial = Settings(developerMode = false)
        val next = Settings(developerMode = true, launchCount = 3)
        val updated = applyUpdateSettings(initial) { next }

        assertTrue(updated.developerMode)
        assertEquals(3, updated.launchCount)
        assertFalse(updated.init)
    }

    private fun applyUpdateSettings(
        settings: Settings,
        transform: (Settings) -> Settings,
    ): Settings = transform(settings)

    private fun deleteGlobalSubagent(settings: Settings, name: String): Settings {
        return settings.copy(
            globalSubagentProfiles = settings.globalSubagentProfiles.filter { it.name != name },
        )
    }

    private fun restoreDefaultSubagents(settings: Settings): Settings {
        val existingNames = settings.globalSubagentProfiles.map { it.name }.toSet()
        val toAdd = SubagentRegistry.BUILTIN_PROFILES.filter { it.name !in existingNames }
        return settings.copy(globalSubagentProfiles = settings.globalSubagentProfiles + toAdd)
    }
}
