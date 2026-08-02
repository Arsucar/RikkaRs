package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningDialect
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Chat Completions reasoning effort dialect mapping (#207):
 * - official DeepSeek XHIGH → max
 * - mid-station proxy + per-model DeepSeekMax
 * - default OpenAI-compat OFF→none / xhigh passthrough (#214)
 * - unknown hosts keep passthrough for deepseek model ids (#214)
 * - nvidia deepseek-v4 regression
 */
class ChatCompletionsAPIReasoningDialectTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    private fun buildRequest(
        baseUrl: String,
        modelId: String,
        reasoningLevel: ReasoningLevel,
        reasoningDialect: ReasoningDialect = ReasoningDialect.Auto,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = listOf(ModelAbility.REASONING),
            reasoningDialect = reasoningDialect,
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = baseUrl)
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true,
        ) as JsonObject
    }

    @Test
    fun `official deepseek XHIGH sends reasoning_effort max`() {
        val body = buildRequest(
            baseUrl = "https://api.deepseek.com",
            modelId = "deepseek-reasoner",
            reasoningLevel = ReasoningLevel.XHIGH,
        )
        assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("max", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `official deepseek HIGH keeps high`() {
        val body = buildRequest(
            baseUrl = "https://api.deepseek.com",
            modelId = "deepseek-reasoner",
            reasoningLevel = ReasoningLevel.HIGH,
        )
        assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `official deepseek AUTO enables thinking without effort`() {
        val body = buildRequest(
            baseUrl = "https://api.deepseek.com",
            modelId = "deepseek-reasoner",
            reasoningLevel = ReasoningLevel.AUTO,
        )
        assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun `proxy host with explicit DeepSeekMax maps XHIGH to max`() {
        val body = buildRequest(
            baseUrl = "https://mid.station.example/v1",
            modelId = "my-ds-alias",
            reasoningLevel = ReasoningLevel.XHIGH,
            reasoningDialect = ReasoningDialect.DeepSeekMax,
        )
        assertEquals("max", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `proxy host Auto without deepseek id keeps xhigh`() {
        val body = buildRequest(
            baseUrl = "https://mid.station.example/v1",
            modelId = "gpt-whatever",
            reasoningLevel = ReasoningLevel.XHIGH,
        )
        assertEquals("xhigh", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `proxy host Auto with deepseek id keeps xhigh passthrough`() {
        // #214: weak model-id hints must not force DeepSeekMax on unknown hosts.
        val body = buildRequest(
            baseUrl = "https://mid.station.example/v1",
            modelId = "deepseek-reasoner",
            reasoningLevel = ReasoningLevel.XHIGH,
        )
        assertEquals("xhigh", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `proxy host Auto OFF maps to none not low`() {
        val body = buildRequest(
            baseUrl = "https://mid.station.example/v1",
            modelId = "deepseek-reasoner",
            reasoningLevel = ReasoningLevel.OFF,
        )
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `default openai-compat OFF maps to none not low`() {
        val body = buildRequest(
            baseUrl = "https://api.openai.com/v1",
            modelId = "o3-mini",
            reasoningLevel = ReasoningLevel.OFF,
        )
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `nvidia deepseek-v4 XHIGH still max`() {
        val body = buildRequest(
            baseUrl = "https://integrate.api.nvidia.com/v1",
            modelId = "deepseek-ai/deepseek-v4-pro",
            reasoningLevel = ReasoningLevel.XHIGH,
        )
        assertEquals("max", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `nvidia deepseek-v4 LOW still high coarse table`() {
        val body = buildRequest(
            baseUrl = "https://integrate.api.nvidia.com/v1",
            modelId = "deepseek-ai/deepseek-v4-flash",
            reasoningLevel = ReasoningLevel.LOW,
        )
        assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `moonshot on-off shape unchanged`() {
        val body = buildRequest(
            baseUrl = "https://api.moonshot.cn/v1",
            modelId = "kimi-k2.5",
            reasoningLevel = ReasoningLevel.HIGH,
        )
        assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertNull(body["reasoning_effort"])
    }
}
