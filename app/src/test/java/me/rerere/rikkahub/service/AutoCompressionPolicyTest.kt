package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class AutoCompressionPolicyTest {
    private val config = AutoCompressionConfig(
        enabled = true,
        thresholdTokens = 100,
        targetTokens = 40,
        keepRecentMessages = 8,
        identity = "assistant",
    )

    @Test
    fun `invocation classification allows only normal sends with prepared input`() {
        assertTrue(GenerationInvocationKind.NormalSend.supportsAutoCompression(true))
        assertFalse(GenerationInvocationKind.NormalSend.supportsAutoCompression(false))
        assertFalse(GenerationInvocationKind.Regenerate.supportsAutoCompression(true))
        assertFalse(GenerationInvocationKind.ToolContinuation.supportsAutoCompression(true))
        assertFalse(GenerationInvocationKind.Preview.supportsAutoCompression(true))
    }

    @Test
    fun `disabled invalid unsupported and below threshold outcomes are reasoned`() {
        assertEquals(
            AutoCompressionDecision.Disabled,
            evaluateAutoCompression(input(config.copy(enabled = false)), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.InvalidConfig,
            evaluateAutoCompression(input(config.copy(thresholdTokens = 0)), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.InvalidConfig,
            evaluateAutoCompression(input(config.copy(targetTokens = -1)), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.InvalidConfig,
            evaluateAutoCompression(input(config.copy(keepRecentMessages = -1)), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.UnsupportedFlow,
            evaluateAutoCompression(
                input(invocationKind = GenerationInvocationKind.Regenerate),
                AutoCompressionRuntimeState(),
            ).decision,
        )
        assertEquals(
            AutoCompressionDecision.UnsupportedFlow,
            evaluateAutoCompression(input(providerInputAvailable = false), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.BelowThreshold,
            evaluateAutoCompression(input(promptTokens = 99), AutoCompressionRuntimeState()).decision,
        )
    }

    @Test
    fun `threshold is inclusive and requires compressible visible history`() {
        assertEquals(
            AutoCompressionDecision.NotCompressible,
            evaluateAutoCompression(input(promptTokens = 100, visibleMessageCount = 8), AutoCompressionRuntimeState()).decision,
        )
        assertEquals(
            AutoCompressionDecision.Trigger,
            evaluateAutoCompression(input(promptTokens = 100, visibleMessageCount = 9), AutoCompressionRuntimeState()).decision,
        )
    }

    @Test
    fun `busy duplicate and disarmed states do not trigger`() {
        assertEquals(
            AutoCompressionDecision.Busy,
            evaluateAutoCompression(input(busy = true), AutoCompressionRuntimeState()).decision,
        )
        val duplicate = AutoCompressionRuntimeState(
            armed = true,
            lastAttemptFingerprint = "fingerprint",
            configSignature = config.signature,
        )
        assertEquals(AutoCompressionDecision.Duplicate, evaluateAutoCompression(input(), duplicate).decision)

        val disarmed = duplicate.copy(armed = false, lastAttemptFingerprint = "older")
        assertEquals(AutoCompressionDecision.Disarmed, evaluateAutoCompression(input(), disarmed).decision)
    }

    @Test
    fun `trigger disarms and low watermark rearms while clearing duplicate state`() {
        val triggered = evaluateAutoCompression(input(), AutoCompressionRuntimeState())
        assertEquals(AutoCompressionDecision.Trigger, triggered.decision)
        assertFalse(triggered.nextState.armed)
        assertEquals("fingerprint", triggered.nextState.lastAttemptFingerprint)
        assertEquals(75, triggered.lowWatermarkTokens)

        val rearmed = evaluateAutoCompression(input(promptTokens = 75), triggered.nextState)
        assertEquals(AutoCompressionDecision.BelowThreshold, rearmed.decision)
        assertTrue(rearmed.nextState.armed)
        assertNull(rearmed.nextState.lastAttemptFingerprint)
    }

    @Test
    fun `config change and disable reset runtime state`() {
        val oldState = AutoCompressionRuntimeState(
            armed = false,
            lastAttemptFingerprint = "old",
            failedVisibleMessageCount = 20,
            configSignature = config.signature,
        )
        val changed = config.copy(thresholdTokens = 101)
        val changedEvaluation = evaluateAutoCompression(input(config = changed, promptTokens = 100), oldState)
        assertEquals(AutoCompressionDecision.BelowThreshold, changedEvaluation.decision)
        assertTrue(changedEvaluation.nextState.armed)
        assertNull(changedEvaluation.nextState.lastAttemptFingerprint)

        val disabled = evaluateAutoCompression(input(config = config.copy(enabled = false)), oldState)
        assertTrue(disabled.nextState.armed)
        assertNull(disabled.nextState.failedVisibleMessageCount)
    }

    @Test
    fun `failure cooldown retries after required growth even while still above low watermark`() {
        val failed = AutoCompressionRuntimeState(
            armed = false,
            lastAttemptFingerprint = "failed-send",
            failedVisibleMessageCount = 20,
            configSignature = config.signature,
        )
        assertEquals(
            AutoCompressionDecision.CoolingDown,
            evaluateAutoCompression(input(promptTokens = 120, visibleMessageCount = 23), failed).decision,
        )
        val retry = evaluateAutoCompression(input(promptTokens = 120, visibleMessageCount = 24), failed)
        assertEquals(AutoCompressionDecision.Trigger, retry.decision)
        assertTrue(120 > checkNotNull(retry.lowWatermarkTokens))
    }

    @Test
    fun `low watermark calculation is overflow safe and always below high`() {
        assertEquals(75, calculateAutoCompressionLowWatermark(100, 40))
        assertEquals(99, calculateAutoCompressionLowWatermark(100, 500))
        assertEquals(Int.MAX_VALUE - 1, calculateAutoCompressionLowWatermark(Int.MAX_VALUE, Int.MAX_VALUE))
        assertNull(calculateAutoCompressionLowWatermark(0, 0))
    }

    @Test
    fun `fingerprint changes for new messages and config without hashing attachment bodies`() {
        val messageId = Uuid.random()
        val nodeId = Uuid.random()
        val message = UIMessage(
            id = messageId,
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Image("data:image/png;base64," + "a".repeat(100_000))),
        )
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(MessageNode(id = nodeId, messages = listOf(message))),
        )
        val first = buildAutoCompressionFingerprint(conversation, config)
        val changedBody = conversation.copy(
            messageNodes = listOf(
                MessageNode(
                    id = nodeId,
                    messages = listOf(
                        message.copy(
                            parts = listOf(UIMessagePart.Image("data:image/png;base64," + "z".repeat(100_000))),
                        )
                    ),
                )
            ),
        )
        assertEquals(first, buildAutoCompressionFingerprint(changedBody, config))

        val withNewUserMessage = conversation.copy(
            messageNodes = conversation.messageNodes + MessageNode.of(UIMessage.user("next")),
        )
        assertNotEquals(first, buildAutoCompressionFingerprint(withNewUserMessage, config))
        assertNotEquals(first, buildAutoCompressionFingerprint(conversation, config.copy(keepRecentMessages = 9)))
    }

    private fun input(
        config: AutoCompressionConfig = this.config,
        invocationKind: GenerationInvocationKind = GenerationInvocationKind.NormalSend,
        providerInputAvailable: Boolean = true,
        promptTokens: Int = 120,
        visibleMessageCount: Int = 20,
        fingerprint: String = "fingerprint",
        busy: Boolean = false,
    ) = AutoCompressionPolicyInput(
        config = config,
        invocationKind = invocationKind,
        providerInputAvailable = providerInputAvailable,
        promptTokens = promptTokens,
        visibleMessageCount = visibleMessageCount,
        fingerprint = fingerprint,
        busy = busy,
    )
}
