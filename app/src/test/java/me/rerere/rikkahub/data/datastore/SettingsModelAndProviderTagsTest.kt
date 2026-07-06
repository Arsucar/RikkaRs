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
    fun recentChatModelMovesSelectedModelToFrontAndKeepsLimit() {
        val models = List(10) { Uuid.random() }
        val settings = Settings.dummy().copy(recentChatModels = models)

        val updated = settings.withRecentChatModel(models[5])

        assertEquals(models[5], updated.recentChatModels.first())
        assertEquals(RECENT_CHAT_MODELS_LIMIT, updated.recentChatModels.size)
        assertEquals(updated.recentChatModels.distinct(), updated.recentChatModels)
    }

    @Test
    fun providerTagsIncludeSuggestionsAndCanHideDeletedSuggestion() {
        val settings = Settings.dummy().copy(
            providers = listOf(provider(tags = listOf("custom"))),
        )

        val deleted = settings.deleteProviderTag("suggested")

        assertEquals(listOf("suggested", "custom"), settings.effectiveProviderTags(listOf("suggested")))
        assertEquals(listOf("custom"), deleted.effectiveProviderTags(listOf("suggested")))
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

        val updated = settings.renameProviderTag("old", "new", suggestedTags = emptyList())

        assertEquals(listOf("new", "keep"), updated.effectiveProviderTags(emptyList()))
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
