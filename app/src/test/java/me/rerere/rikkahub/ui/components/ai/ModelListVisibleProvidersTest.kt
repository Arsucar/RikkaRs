package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModelListVisibleProvidersTest {
    @Test
    fun blankSearchKeepsEveryTagFilteredProvider() {
        val matchingProvider = provider("Matching", listOf(model("match", "Match")))
        val emptyProvider = provider("Empty")
        val providers = listOf(matchingProvider, emptyProvider)

        val modelsByProvider = searchModelsByProvider(providers, ModelType.CHAT, "   ")
        val visibleProviders = resolveVisibleModelProviders(providers, modelsByProvider, "   ")

        assertSame(providers, visibleProviders)
    }

    @Test
    fun modelSearchKeepsOnlyProvidersWithMatchingModelsInOriginalOrder() {
        val firstMatch = provider("First", listOf(model("first", "Shared target")))
        val noMatch = provider("Middle", listOf(model("middle", "Different")))
        val secondMatch = provider("Last", listOf(model("last", "Another target")))
        val providers = listOf(firstMatch, noMatch, secondMatch)

        val modelsByProvider = searchModelsByProvider(providers, ModelType.CHAT, "target")
        val visibleProviders = resolveVisibleModelProviders(providers, modelsByProvider, "target")

        assertEquals(listOf(firstMatch, secondMatch), visibleProviders)
        assertEquals(listOf("first"), modelsByProvider.getValue(firstMatch.id).map { it.modelId })
        assertEquals(listOf("last"), modelsByProvider.getValue(secondMatch.id).map { it.modelId })
    }

    @Test
    fun providerNameSearchKeepsAllModelsOfTheRequestedType() {
        val chatModel = model("chat", "Unrelated chat")
        val imageModel = Model(modelId = "image", displayName = "Unrelated image", type = ModelType.IMAGE)
        val matchingProvider = provider("Target provider", listOf(chatModel, imageModel))
        val otherProvider = provider("Other provider", listOf(model("other", "Other chat")))
        val providers = listOf(matchingProvider, otherProvider)

        val modelsByProvider = searchModelsByProvider(providers, ModelType.CHAT, "target")
        val visibleProviders = resolveVisibleModelProviders(providers, modelsByProvider, "target")

        assertEquals(listOf(matchingProvider), visibleProviders)
        assertEquals(listOf(chatModel), modelsByProvider.getValue(matchingProvider.id))
    }

    @Test
    fun providerWithOnlyWrongTypeMatchesIsHidden() {
        val provider = provider(
            name = "Provider",
            models = listOf(Model(modelId = "image", displayName = "Target image", type = ModelType.IMAGE)),
        )

        val modelsByProvider = searchModelsByProvider(listOf(provider), ModelType.CHAT, "target")

        assertEquals(
            emptyList<ProviderSetting>(),
            resolveVisibleModelProviders(listOf(provider), modelsByProvider, "target"),
        )
    }

    private fun model(modelId: String, displayName: String) = Model(
        modelId = modelId,
        displayName = displayName,
        type = ModelType.CHAT,
    )

    private fun provider(name: String, models: List<Model> = emptyList()) = ProviderSetting.OpenAI(
        name = name,
        models = models,
    )
}
