package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsModelAndProviderTagsTest {
    @Test
    fun sponsorAlertDisabledDefaultsToFalseAndCanBePersistedInModel() {
        assertFalse(Settings().sponsorAlertDisabled)
        assertTrue(Settings().copy(sponsorAlertDisabled = true).sponsorAlertDisabled)
    }

    @Test
    fun recentChatModelMovesSelectedModelToFrontAndKeepsLimit() {
        val models = List(10) { Uuid.random() }
        val settings = Settings.dummy().copy(recentChatModels = models)

        val updated = settings.withRecentChatModel(models[5])

        assertEquals(models[5], updated.recentChatModels.first())
        assertEquals(RECENT_CHAT_MODELS_LIMIT, updated.recentChatModels.size)
        assertEquals(updated.recentChatModels.distinct(), updated.recentChatModels)
    }

    @Test
    fun providerTagsUseOnlyUserOrderAndUsedTags() {
        val settings = Settings.dummy().copy(
            providers = listOf(provider(tags = listOf("custom"))),
            providerTagOrder = listOf("manual"),
        )

        val deleted = settings.deleteProviderTag("manual")

        assertEquals(listOf("manual", "custom"), settings.effectiveProviderTags())
        assertEquals(listOf("custom"), deleted.effectiveProviderTags())
    }

    @Test
    fun providerTagOrderKeepsSettledTagAfterProviderDetachesIt() {
        // 标签曾挂到 provider 后沉淀进 providerTagOrder；即使当前没有 provider 持有它，
        // 只要不在 hiddenProviderTags 里，effectiveProviderTags 仍应返回它。
        val settings = Settings.dummy().copy(
            providers = listOf(provider(tags = emptyList())),
            providerTagOrder = listOf("settled"),
            hiddenProviderTags = emptyList(),
        )

        assertEquals(listOf("settled"), settings.effectiveProviderTags())
    }

    @Test
    fun renameProviderTagUpdatesAllProviderReferences() {
        val settings = Settings.dummy().copy(
            providers = listOf(
                provider(tags = listOf("old", "keep")),
                provider(tags = listOf("old")),
            ),
            providerTagOrder = listOf("old", "keep"),
        )

        val updated = settings.renameProviderTag("old", "new")

        assertEquals(listOf("new", "keep"), updated.effectiveProviderTags())
        assertTrue(updated.providers.all { provider -> "new" in provider.tags })
        assertFalse(updated.providers.any { provider -> "old" in provider.tags })
    }

    private fun provider(tags: List<String>): ProviderSetting =
        ProviderSetting.OpenAI(
            name = "Provider",
            models = listOf(Model(modelId = "model", displayName = "Model")),
            tags = tags,
        )
}
