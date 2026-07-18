package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.HookRuntimeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HookOutputParserTest {
    @Test
    fun parsesApplyAndSkip() {
        val tagId = Uuid.random()
        val apply = HookOutputParser.parse("""{"decision":"apply","tagId":"$tagId","reason":" done "}""")
        val skip = HookOutputParser.parse("""{"decision":"skip","tagId":null,"reason":"no"}""")

        assertEquals(HookDecision.APPLY, apply.decision)
        assertEquals(tagId, apply.tagId)
        assertEquals("done", apply.reason)
        assertEquals(HookDecision.SKIP, skip.decision)
        assertEquals(null, skip.tagId)
    }

    @Test
    fun rejectsNonObjectDecorationsAndWrongKeySets() {
        listOf(
            "```json\n{}\n```",
            " {}",
            "[]",
            "{}",
            """{"decision":"skip","tagId":null,"reason":"x","extra":1}""",
        ).forEach { raw ->
            assertThrows(HookOutputException::class.java) { HookOutputParser.parse(raw) }
        }
    }

    @Test
    fun rejectsBadTypesDecisionAndTagRules() {
        listOf(
            """{"decision":true,"tagId":null,"reason":"x"}""",
            """{"decision":"other","tagId":null,"reason":"x"}""",
            """{"decision":"apply","tagId":null,"reason":"x"}""",
            """{"decision":"apply","tagId":"bad","reason":"x"}""",
            """{"decision":"skip","tagId":"${Uuid.random()}","reason":"x"}""",
            """{"decision":"skip","tagId":null,"reason":1}""",
        ).forEach { raw ->
            assertThrows(HookOutputException::class.java) { HookOutputParser.parse(raw) }
        }
    }

    @Test
    fun truncatesReasonByUnicodeCodePoints() {
        val reason = "😀".repeat(501)
        val parsed = HookOutputParser.parse("""{"decision":"skip","tagId":null,"reason":"$reason"}""")

        assertEquals(500, parsed.reason.codePointCount(0, parsed.reason.length))
        assertTrue(parsed.reasonTruncated)
        assertFalse(parsed.reason.endsWith("�"))
    }

    @Test
    fun parsesStrictMemoryTableApplyAndSkip() {
        val apply = MemoryTableSyncHookOutputParser.parse(
            """{"decision":"apply","baseRevision":3,"operations":[{"type":"delete"}],"reason":" update "}""",
            maxOperations = 2,
        )
        val skip = MemoryTableSyncHookOutputParser.parse(
            """{"decision":"skip","baseRevision":4,"operations":[],"reason":"no change"}""",
            maxOperations = 2,
        )

        assertEquals(HookDecision.APPLY, apply.decision)
        assertEquals(3, apply.baseRevision)
        assertEquals(1, apply.operations.size)
        assertEquals("update", apply.reason)
        assertEquals(HookDecision.SKIP, skip.decision)
        assertTrue(skip.operations.isEmpty())
    }

    @Test
    fun memoryTableParserRejectsDecorationsAndUnauthorizedFields() {
        listOf(
            " " + validSyncJson(),
            "```json\n${validSyncJson()}\n```",
            "[]",
            "{}",
            """{"decision":"skip","baseRevision":1,"operations":[],"reason":"x","targetDocumentId":"x"}""",
            """{"decision":"skip","baseRevision":1,"operations":[],"reason":"x","scope":"global"}""",
            """{"decision":"skip","baseRevision":1,"operations":[],"reason":"x","tool":"memory_table"}""",
        ).forEach { raw ->
            assertThrows(HookOutputException::class.java) {
                MemoryTableSyncHookOutputParser.parse(raw, maxOperations = 2)
            }
        }
    }

    @Test
    fun memoryTableParserRejectsBadRevisionAndOperationShapes() {
        listOf(
            """{"decision":"apply","baseRevision":"1","operations":[],"reason":"x"}""",
            """{"decision":"apply","baseRevision":1.5,"operations":[],"reason":"x"}""",
            """{"decision":"apply","baseRevision":1,"operations":{},"reason":"x"}""",
            """{"decision":"skip","baseRevision":1,"operations":[{}],"reason":"x"}""",
            """{"decision":"apply","baseRevision":1,"operations":[{},{}],"reason":"x"}""",
        ).forEach { raw ->
            val error = assertThrows(HookOutputException::class.java) {
                MemoryTableSyncHookOutputParser.parse(raw, maxOperations = 1)
            }
            assertEquals(HookErrorCode.SCHEMA_MISMATCH, error.code)
        }
    }

    @Test
    fun memoryTableParserKeepsInvalidJsonAndSchemaErrorsStable() {
        val invalidJson = assertThrows(HookOutputException::class.java) {
            MemoryTableSyncHookOutputParser.parse("{not-json", maxOperations = 1)
        }
        val oversized = assertThrows(HookOutputException::class.java) {
            MemoryTableSyncHookOutputParser.parse(
                "x".repeat(HookRuntimeRules.MAX_SYNC_RESPONSE_CHARS + 1),
                maxOperations = 1,
            )
        }

        assertEquals(HookErrorCode.INVALID_JSON, invalidJson.code)
        assertEquals(HookErrorCode.SCHEMA_MISMATCH, oversized.code)
    }

    private fun validSyncJson(): String =
        """{"decision":"skip","baseRevision":1,"operations":[],"reason":"x"}"""
}
