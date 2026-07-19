package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookEvent
import me.rerere.rikkahub.data.model.HookEventType
import me.rerere.rikkahub.data.model.HookExecutionMode
import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType.ASSISTANT
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookActionConfig
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class HookEventTest {
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val turnId = Uuid.parse("00000000-0000-0000-0000-000000000002")

    @Test
    fun stableIdIsDeterministicAndEventTypeScoped() {
        val first = HookEvent.stableId(HookEventType.KEYWORD_MATCHED, conversationId, turnId, "message", "issue")
        val second = HookEvent.stableId(HookEventType.KEYWORD_MATCHED, conversationId, turnId, "message", "issue")
        val other = HookEvent.stableId(HookEventType.SUBAGENT_COMPLETED, conversationId, turnId, "message", "issue")
        assertEquals(first, second)
        assertNotEquals(first, other)
    }

    @Test
    fun keywordMatcherHonorsWordBoundaries() {
        assertEquals("issue", detectConfiguredHookKeyword("Issue fixed", "issue"))
        assertEquals("issues", detectConfiguredHookKeyword("Several issues fixed", "issue"))
        assertEquals("#123", detectConfiguredHookKeyword("Resolved #123.", "issue"))
        assertNull(detectConfiguredHookKeyword("tissue fixed", "issue"))
        assertEquals("#123", detectConfiguredHookKeyword("Resolved #123.", "#123"))
        assertEquals(
            "https://github.com/acme/project/issues/42",
            detectConfiguredHookKeyword("See HTTPS://GitHub.com/Acme/Project/issues/42/.", "issue"),
        )
    }

    @Test
    fun errorExperienceMapsInsertAndEquivalentUpdate() {
        val first = parseExperience("fix shell timeout")
        assertEquals(HookDecision.APPLY, first.decision)
        assertEquals("insert", first.operations[0].jsonObject["type"]?.jsonPrimitive?.content)

        val existing = parseExperience(
            "fix shell timeout",
            "{\"memories\":[{\"key\":\"fix shell timeout\",\"summary\":\"old\",\"evidence\":\"old\",\"updated_at\":\"1\"}]}",
        )
        assertEquals("update", existing.operations[0].jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun errorExperienceSkipsAndRejectsUnsafeEvidence() {
        val skipped = parseExperience(
            "",
            response = "{\"should_remember\":false,\"deduplication_key\":\"\",\"symptom\":\"\",\"root_cause\":\"\",\"correction\":\"\",\"scope\":\"\",\"tools\":[],\"commands\":[],\"reason\":\"transient\"}",
        )
        assertEquals(HookDecision.SKIP, skipped.decision)
        org.junit.Assert.assertThrows(HookOutputException::class.java) {
            parseExperience("key", response = "{\"should_remember\":true,\"deduplication_key\":\"key\",\"symptom\":\"token=secret\",\"root_cause\":\"cause\",\"correction\":\"fix\",\"scope\":\"tool\",\"tools\":[],\"commands\":[],\"reason\":\"keep\"}")
        }
    }

    private fun parseExperience(
        key: String,
        payload: String = "{}",
        response: String = "{\"should_remember\":true,\"deduplication_key\":\"$key\",\"symptom\":\"timeout\",\"root_cause\":\"network\",\"correction\":\"retry\",\"scope\":\"shell\",\"tools\":[\"workspace\"],\"commands\":[\"run\"],\"reason\":\"reusable\"}",
    ): ParsedMemoryTableSyncHookOutput {
        val target = MemoryTableDocument(
            id = "doc",
            templateId = "template",
            scopeType = ASSISTANT,
            scopeId = "assistant",
            payloadJson = payload,
            revision = 3,
        )
        val prepared = PreparedHookAction.SyncMemoryTable(
            request = FrozenHookModelRequest.SyncMemoryTable(
                modelId = conversationId,
                prompt = "",
                messages = emptyList(),
                targetDocumentId = target.id,
                baseRevision = target.revision,
                schemaJson = """{"tables":[{"name":"memories","columns":[{"name":"key","type":"string"},{"name":"summary","type":"string"},{"name":"evidence","type":"string"},{"name":"updated_at","type":"string"}]}]}""",
                payloadJson = target.payloadJson,
                maxOperations = 4,
                errorExperience = true,
            ),
            audit = HookPreparedAudit.MemoryTable("doc", "template", ASSISTANT, "assistant", 3, "id", null),
            hookId = conversationId,
            hookConfigVersion = 1,
            hookConfigHash = "hash",
            assistantId = conversationId,
            conversationId = conversationId,
            logicalTurnId = turnId,
            cutoffMessageId = turnId,
            sourceKind = HookExecutionMode.AUTO.name,
            sourceKey = "event",
            eventId = "event",
            eventType = HookEventType.TOOL_CALL_FINAL_FAILED,
            eventOccurredAtEpochMillis = 10,
            errorExperience = true,
            target = target,
            schemaJson = requestSchema(),
            config = HookActionConfig.SyncMemoryTable(targetDocumentId = "doc"),
        )
        return MemoryExperienceHookOutputParser.parse(response, prepared, 10)
    }

    private fun requestSchema() = """{"tables":[{"name":"memories","columns":[{"name":"key","type":"string"},{"name":"summary","type":"string"},{"name":"evidence","type":"string"},{"name":"updated_at","type":"string"}]}]}"""
}
