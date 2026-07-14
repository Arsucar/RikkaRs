package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ModelListRecentModelsTest {
    @Test
    fun resolvesModelsAndProvidersInRecentOrderWithoutDeduplicatingDisplayNames() {
        val firstModel = chatModel(modelId = "first", displayName = "Shared name")
        val secondModel = chatModel(modelId = "second", displayName = "Shared name")
        val firstProvider = provider(name = "First provider", models = listOf(firstModel))
        val secondProvider = provider(name = "Second provider", models = listOf(secondModel))

        val items = resolveRecentChatModelItems(
            recentChatModelIds = listOf(secondModel.id, firstModel.id),
            providers = listOf(firstProvider, secondProvider),
            type = ModelType.CHAT,
        )

        assertEquals(listOf(secondModel.id, firstModel.id), items.map { it.model.id })
        assertEquals(listOf("Second provider", "First provider"), items.map { it.provider.name })
        assertSame(secondModel, items[0].model)
        assertSame(firstModel, items[1].model)
    }

    @Test
    fun resolvesProviderNameFromCurrentProviderConfiguration() {
        val model = chatModel(modelId = "chat", displayName = "Chat")
        val originalProvider = provider(name = "Original", models = listOf(model))
        val renamedProvider = originalProvider.copy(name = "Renamed")
        val recentIds = listOf(model.id)

        val originalItems = resolveRecentChatModelItems(recentIds, listOf(originalProvider), ModelType.CHAT)
        val renamedItems = resolveRecentChatModelItems(recentIds, listOf(renamedProvider), ModelType.CHAT)

        assertEquals("Original", originalItems.single().provider.name)
        assertEquals("Renamed", renamedItems.single().provider.name)
    }

    @Test
    fun filtersMissingDisabledAndNonChatModels() {
        val validModel = chatModel(modelId = "valid", displayName = "Valid")
        val disabledModel = chatModel(modelId = "disabled", displayName = "Disabled")
        val imageModel = Model(modelId = "image", displayName = "Image", type = ModelType.IMAGE)
        val providers = listOf(
            provider(name = "Valid provider", models = listOf(validModel)),
            provider(name = "Disabled provider", models = listOf(disabledModel), enabled = false),
            provider(name = "Image provider", models = listOf(imageModel)),
        )

        val items = resolveRecentChatModelItems(
            recentChatModelIds = listOf(Uuid.random(), disabledModel.id, imageModel.id, validModel.id),
            providers = providers,
            type = ModelType.CHAT,
        )

        assertEquals(listOf(validModel.id), items.map { it.model.id })
        assertTrue(
            resolveRecentChatModelItems(
                recentChatModelIds = listOf(validModel.id),
                providers = providers,
                type = ModelType.IMAGE,
            ).isEmpty()
        )
    }

    @Test
    fun ignoresProviderOverwriteWhenResolvingOwningProvider() {
        val overwriteProvider = provider(name = "Override provider")
        val model = chatModel(
            modelId = "overwritten",
            displayName = "Overwritten",
            providerOverwrite = overwriteProvider,
        )
        val actualProvider = provider(name = "Actual provider", models = listOf(model))

        val item = resolveRecentChatModelItems(
            recentChatModelIds = listOf(model.id),
            providers = listOf(actualProvider),
            type = ModelType.CHAT,
        ).single()

        assertSame(actualProvider, item.provider)
        assertEquals("Actual provider", item.provider.name)
    }

    private fun chatModel(
        modelId: String,
        displayName: String,
        providerOverwrite: ProviderSetting? = null,
    ) = Model(
        modelId = modelId,
        displayName = displayName,
        type = ModelType.CHAT,
        providerOverwrite = providerOverwrite,
    )

    private fun provider(
        name: String,
        models: List<Model> = emptyList(),
        enabled: Boolean = true,
    ) = ProviderSetting.OpenAI(
        name = name,
        models = models,
        enabled = enabled,
    )
}
