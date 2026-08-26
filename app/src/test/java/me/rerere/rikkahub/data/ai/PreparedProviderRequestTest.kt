package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PreparedProviderRequestTest {
    @Test
    fun firstProviderRequestUsesExactPreviewMessagesAndToolSnapshot() = runBlocking {
        val providerSetting = ProviderSetting.OpenAI()
        val model = Model(modelId = "fake", providerOverwrite = providerSetting)
        val tool = Tool(name = "lookup", description = "Lookup", execute = { emptyList() })
        val prepared = PreparedProviderInput(
            messages = listOf(UIMessage.system("system"), UIMessage.user("question")),
            tools = listOf(tool),
            sourceMessageCount = 1,
            retainedSourceMessageCount = 1,
            usedConversationSystemPrompt = false,
        )
        val preview = prepared.toContextPreview(Json)
        val provider = RecordingProvider()

        executePreparedProviderRequest(
            providerSetting = providerSetting,
            prepared = prepared,
            params = TextGenerationParams(model = model),
        ) { messages, params ->
            provider.generateText(providerSetting, messages, params)
        }

        assertEquals(preview.messages, provider.messages)
        assertSame(prepared.messages, provider.messages)
        assertEquals(preview.tools.map { it.name }, provider.params!!.tools.map { it.name })
        assertSame(prepared.tools, provider.params!!.tools)
    }

    private class RecordingProvider : Provider<ProviderSetting.OpenAI> {
        var messages: List<UIMessage>? = null
        var params: TextGenerationParams? = null

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): TextGenerationResult {
            this.messages = messages
            this.params = params
            return TextGenerationResult(
                id = "fake",
                model = params.model.modelId,
                message = UIMessage.assistant(""),
            )
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<StreamChunk> = emptyFlow()
    }
}
