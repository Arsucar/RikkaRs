package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class DefaultModelCompletionProviderTest {
    private val model = Model(
        id = Uuid.random(),
        modelId = "test-model",
        displayName = "Test Model",
    )

    @Test
    fun `exact half width command returns default model action`() = runBlocking {
        val result = provider(model).complete(context("~dm"))

        assertNotNull(result)
        assertEquals(TextRange(0, 3), result?.replacementRange)
        assertEquals(1, result?.items?.size)
        val item = result!!.items.single()
        assertEquals("~dm", item.label)
        assertEquals("", item.insertText)
        assertEquals(
            ChatCompletionAction.SetAssistantDefaultModel(model.id, "Test Model"),
            item.action,
        )
    }

    @Test
    fun `exact full width command returns matching replacement range`() = runBlocking {
        val result = provider(model).complete(context("～dm"))

        assertNotNull(result)
        assertEquals(TextRange(0, 3), result?.replacementRange)
    }

    @Test
    fun `incomplete unrelated whitespace and selection queries do not match`() = runBlocking {
        listOf("~", "~d", "~other", "~dm ").forEach { text ->
            assertNull(text, provider(model).complete(context(text)))
        }
        assertNull(
            provider(model).complete(
                ChatCompletionContext("~dm", TextRange(0, 3))
            )
        )
    }

    @Test
    fun `provider works without presets and preserves invalid model action`() = runBlocking {
        val result = provider(null).complete(context("~dm"))

        assertNotNull(result)
        assertEquals(
            ChatCompletionAction.SetAssistantDefaultModel(modelId = null, modelName = null),
            result!!.items.single().action,
        )
    }

    @Test
    fun `preset provider excludes dm command without changing normal matching`() = runBlocking {
        val preset = Preset(name = "Demo", description = "Default model helper")
        val provider = PresetCompletionProvider(listOf(preset))

        assertNull(provider.complete(context("~dm")))
        assertEquals(preset.id, provider.complete(context("~dem"))?.items?.single()?.presetId)
        assertEquals(preset.id, provider.complete(context("～dem"))?.items?.single()?.presetId)
    }

    @Test
    fun `valid action updates target assistant clears command and returns success feedback`() = runBlocking {
        val target = Assistant(id = Uuid.random())
        val item = provider(model).complete(context("prefix ~dm"))!!.items.single()

        val application = prepareChatCompletionApplication(
            textLength = "prefix ~dm".length,
            replacementRange = TextRange(7, 10),
            item = item,
            assistant = target,
        )

        assertEquals(target.id, application.assistantUpdate?.id)
        assertEquals(model.id, application.assistantUpdate?.chatModelId)
        assertEquals("prefix ", application.applyTo("prefix ~dm"))
        assertEquals(TextRange(7, 10), application.replacementRange)
        assertEquals(7, application.cursor)
        assertEquals(
            ChatCompletionApplyResult.DefaultModelSaved("Test Model"),
            application.result,
        )
    }

    @Test
    fun `invalid action clears command without writing assistant and returns failure feedback`() = runBlocking {
        val target = Assistant()
        val item = provider(null).complete(context("~dm"))!!.items.single()

        val application = prepareChatCompletionApplication(
            textLength = 3,
            replacementRange = TextRange(0, 3),
            item = item,
            assistant = target,
        )

        assertNull(application.assistantUpdate)
        assertEquals("", application.applyTo("~dm"))
        assertEquals(ChatCompletionApplyResult.DefaultModelUnavailable, application.result)
    }

    @Test
    fun `setting the existing default model remains idempotent and successful`() = runBlocking {
        val target = Assistant(chatModelId = model.id)
        val item = provider(model).complete(context("~dm"))!!.items.single()

        val application = prepareChatCompletionApplication(
            textLength = 3,
            replacementRange = TextRange(0, 3),
            item = item,
            assistant = target,
        )

        assertEquals(target, application.assistantUpdate)
        assertEquals(
            ChatCompletionApplyResult.DefaultModelSaved("Test Model"),
            application.result,
        )
    }

    private fun provider(model: Model?) = DefaultModelCompletionProvider(
        model = model,
        detail = model?.let { "Set ${it.displayName} as the assistant default model" }
            ?: "No model is currently available",
        unknownModelName = "Unknown model",
    )

    private fun context(text: String) = ChatCompletionContext(
        text = text,
        selection = TextRange(text.length),
    )
}
