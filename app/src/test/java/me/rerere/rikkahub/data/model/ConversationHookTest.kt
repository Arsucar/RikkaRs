package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationHookTest {
    @Test
    fun oldAssistantJsonDefaultsHooksToEmpty() {
        assertTrue(JsonInstant.decodeFromString<Assistant>("{}").hooks.isEmpty())
    }

    @Test
    fun hookConfigurationSurvivesAssistantRoundTrip() {
        val tagId = Uuid.random()
        val hook = ConversationHook(
            name = "Mark completed",
            modelId = Uuid.random(),
            prompt = "Decide whether the work is complete",
            actionConfig = HookActionConfig.AddConversationTag(setOf(tagId)),
            configVersion = 7,
        )

        val restored = JsonInstant.decodeFromString<Assistant>(
            JsonInstant.encodeToString(Assistant(hooks = listOf(hook)))
        ).hooks.single()

        assertEquals(hook, restored)
        assertEquals(7, restored.configVersion)
    }

    @Test
    fun configurationHashIsStableAndChangesWithConfiguration() {
        val hook = ConversationHook(
            modelId = Uuid.random(),
            prompt = "prompt",
            actionConfig = HookActionConfig.AddConversationTag(setOf(Uuid.random())),
        )

        assertEquals(hook.configurationHash(), hook.copy().configurationHash())
        assertNotEquals(hook.configurationHash(), hook.copy(prompt = "changed").configurationHash())
    }

    @Test
    fun aggregateKeepsActiveRunsOpenThenUsesTerminalPriority() {
        assertEquals(
            HookRunStatus.RUNNING,
            aggregateHookRunStatus(listOf(HookExecutionStatus.FAILED, HookExecutionStatus.QUEUED)),
        )
        assertEquals(
            HookRunStatus.FAILED,
            aggregateHookRunStatus(listOf(HookExecutionStatus.FAILED, HookExecutionStatus.INTERRUPTED)),
        )
        assertEquals(
            HookRunStatus.SUCCESS,
            aggregateHookRunStatus(listOf(HookExecutionStatus.SUCCESS, HookExecutionStatus.SKIPPED)),
        )
        assertEquals(
            HookRunStatus.SKIPPED,
            aggregateHookRunStatus(listOf(HookExecutionStatus.SKIPPED, HookExecutionStatus.SKIPPED)),
        )
    }

    @Test
    fun reasonTruncationCountsUnicodeCodePoints() {
        val result = truncateHookReason("😀".repeat(501))

        assertTrue(result.truncated)
        assertEquals(500, Character.codePointCount(result.value, 0, result.value.length))
        assertFalse(result.value.endsWith("\uD83D"))
    }

    @Test
    fun hookErrorsRedactCommonCredentialsAndUrlQueries() {
        val sanitized = sanitizeHookError(
            "Authorization: Bearer secret-token https://example.test/path?api_key=secret sk-live-secret"
        ).orEmpty()

        assertFalse(sanitized.contains("secret-token"))
        assertFalse(sanitized.contains("api_key=secret"))
        assertFalse(sanitized.contains("sk-live-secret"))
    }
}
