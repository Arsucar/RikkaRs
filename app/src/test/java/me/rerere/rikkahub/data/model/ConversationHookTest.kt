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
            actionConfig = HookActionConfig.ManageConversationTags(setOf(tagId)),
            configVersion = 7,
        )

        val restored = JsonInstant.decodeFromString<Assistant>(
            JsonInstant.encodeToString(Assistant(hooks = listOf(hook)))
        ).hooks.single()

        assertEquals(hook, restored)
        assertEquals(7, restored.configVersion)
    }

    @Test
    fun literalLegacyAddTagJsonNormalizesToManage() {
        val tagId = Uuid.parse("00000000-0000-0000-0000-000000000003")
        val action = JsonInstant.decodeFromString<HookActionConfig>(
            """{"type":"add_conversation_tag","allowedTagIds":["$tagId"]}"""
        ).normalize()

        assertEquals(HookActionConfig.ManageConversationTags(setOf(tagId)), action)
        assertEquals(
            """{"type":"manage_conversation_tags","allowedTagIds":["$tagId"]}""",
            JsonInstant.encodeToString<HookActionConfig>(action),
        )
    }

    @Test
    fun literalLegacyTransitionJsonNormalizesToManageAllowlistUnion() {
        val addTagId = Uuid.parse("00000000-0000-0000-0000-000000000010")
        val removeTagId = Uuid.parse("00000000-0000-0000-0000-000000000020")
        val action = JsonInstant.decodeFromString<HookActionConfig>(
            """{"type":"transition_conversation_tags","addTagId":"$addTagId","removeTagId":"$removeTagId"}"""
        ).normalize()

        assertEquals(
            HookActionConfig.ManageConversationTags(setOf(addTagId, removeTagId)),
            action,
        )
        assertTrue(JsonInstant.encodeToString(action).contains("manage_conversation_tags"))
    }

    @Test
    fun syncMemoryTableConfigurationSurvivesRoundTrip() {
        val action = HookActionConfig.SyncMemoryTable(
            targetDocumentId = "document-1",
            targetScopeType = MemoryTableScopeType.CONVERSATION,
            recentMessageCount = 9,
            includeUserMessages = false,
            includeAssistantMessages = true,
            maxContextChars = 4_096,
            maxOperations = 7,
            minimumIntervalSeconds = 30,
            automatic = true,
        )

        val encoded = JsonInstant.encodeToString<HookActionConfig>(action)
        val restored = JsonInstant.decodeFromString<HookActionConfig>(encoded)

        assertTrue(encoded.contains("sync_memory_table"))
        assertEquals(action, restored)
    }

    @Test
    fun manageConversationTagsConfigurationSurvivesRoundTrip() {
        val action = HookActionConfig.ManageConversationTags(
            setOf(
                Uuid.parse("00000000-0000-0000-0000-000000000010"),
                Uuid.parse("00000000-0000-0000-0000-000000000020"),
            ),
        )

        val encoded = JsonInstant.encodeToString<HookActionConfig>(action)
        val restored = JsonInstant.decodeFromString<HookActionConfig>(encoded)

        assertTrue(encoded.contains("manage_conversation_tags"))
        assertEquals(action, restored)
        assertEquals(HookActionType.MANAGE_CONVERSATION_TAGS, restored.actionType)
    }

    @Test
    fun configurationHashIsStableAndChangesWithConfiguration() {
        val hook = ConversationHook(
            modelId = Uuid.random(),
            prompt = "prompt",
            actionConfig = HookActionConfig.ManageConversationTags(setOf(Uuid.random())),
        )

        assertEquals(hook.configurationHash(), hook.copy().configurationHash())
        assertNotEquals(hook.configurationHash(), hook.copy(prompt = "changed").configurationHash())
    }

    @Test
    fun manageTagConfigurationHashKeepsSortedAllowlistMaterial() {
        val hook = ConversationHook(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            name = "Mark completed",
            modelId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            prompt = "Decide whether the work is complete",
            actionConfig = HookActionConfig.ManageConversationTags(
                setOf(Uuid.parse("00000000-0000-0000-0000-000000000003"))
            ),
            configVersion = 7,
        )

        assertEquals(
            "c8f5e2e8f0c2e1d0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6".length,
            hook.configurationHash().length,
        )
        assertEquals(64, hook.configurationHash().length)
        assertEquals(
            hook.configurationHash(),
            hook.copy(actionConfig = HookActionConfig.AddConversationTag(setOf(Uuid.parse("00000000-0000-0000-0000-000000000003"))))
                .configurationHash(),
        )
    }

    @Test
    fun manageTagHashIgnoresAllowedTagSetIterationOrder() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000010")
        val second = Uuid.parse("00000000-0000-0000-0000-000000000020")
        val hook = ConversationHook(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            modelId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            actionConfig = HookActionConfig.ManageConversationTags(linkedSetOf(first, second)),
        )

        assertEquals(
            hook.configurationHash(),
            hook.copy(actionConfig = HookActionConfig.ManageConversationTags(linkedSetOf(second, first)))
                .configurationHash(),
        )
    }

    @Test
    fun everySyncConfigurationFieldAffectsHash() {
        val baseAction = HookActionConfig.SyncMemoryTable(
            targetDocumentId = "document-1",
            targetScopeType = MemoryTableScopeType.ASSISTANT,
            recentMessageCount = 8,
            includeUserMessages = true,
            includeAssistantMessages = true,
            maxContextChars = 12_000,
            maxOperations = 12,
            minimumIntervalSeconds = 0,
            automatic = false,
        )
        val hook = ConversationHook(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            modelId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            prompt = "sync",
            actionConfig = baseAction,
        )
        val variants = listOf(
            baseAction.copy(targetDocumentId = "document-2"),
            baseAction.copy(targetScopeType = MemoryTableScopeType.CONVERSATION),
            baseAction.copy(recentMessageCount = 9),
            baseAction.copy(includeUserMessages = false),
            baseAction.copy(includeAssistantMessages = false),
            baseAction.copy(maxContextChars = 12_001),
            baseAction.copy(maxOperations = 13),
            baseAction.copy(minimumIntervalSeconds = 1),
            baseAction.copy(automatic = true),
        )

        variants.forEach { action ->
            assertNotEquals(hook.configurationHash(), hook.copy(actionConfig = action).configurationHash())
        }
        assertNotEquals(hook.configurationHash(), hook.copy(prompt = "changed").configurationHash())
        assertNotEquals(
            hook.configurationHash(),
            hook.copy(modelId = Uuid.parse("00000000-0000-0000-0000-000000000004")).configurationHash(),
        )
    }

    @Test
    fun legacyTransitionNormalizesToSameHashAsManageUnion() {
        val addTagId = Uuid.parse("00000000-0000-0000-0000-000000000010")
        val removeTagId = Uuid.parse("00000000-0000-0000-0000-000000000020")
        val transition = ConversationHook(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            modelId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            prompt = "transition",
            actionConfig = HookActionConfig.TransitionConversationTags(addTagId, removeTagId),
        )
        val manage = transition.copy(
            actionConfig = HookActionConfig.ManageConversationTags(setOf(addTagId, removeTagId)),
        )

        assertEquals(manage.configurationHash(), transition.configurationHash())
        assertEquals(HookActionType.MANAGE_CONVERSATION_TAGS, transition.actionConfig.actionType)
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

    @Test
    fun parseStoredHookActionTypeKeepsLegacyNames() {
        assertEquals(HookActionType.ADD_CONVERSATION_TAG, parseStoredHookActionType("ADD_CONVERSATION_TAG"))
        assertEquals(
            HookActionType.TRANSITION_CONVERSATION_TAGS,
            parseStoredHookActionType("TRANSITION_CONVERSATION_TAGS"),
        )
        assertEquals(
            HookActionType.MANAGE_CONVERSATION_TAGS,
            parseStoredHookActionType("MANAGE_CONVERSATION_TAGS"),
        )
    }
}
