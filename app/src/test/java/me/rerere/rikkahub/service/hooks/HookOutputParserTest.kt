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
    fun parsesStrictTagTransitionApplyAndSkip() {
        val apply = TransitionConversationTagsHookOutputParser.parse(
            """{"decision":"apply","reason":" verified "}"""
        )
        val skip = TransitionConversationTagsHookOutputParser.parse(
            """{"decision":"skip","reason":"no"}"""
        )

        assertEquals(HookDecision.APPLY, apply.decision)
        assertEquals("verified", apply.reason)
        assertEquals(HookDecision.SKIP, skip.decision)
    }

    @Test
    fun tagTransitionParserRejectsDecorationsExtraKeysAndBadDecision() {
        listOf(
            " " + """{"decision":"skip","reason":"x"}""",
            "```json\n{\"decision\":\"skip\",\"reason\":\"x\"}\n```",
            """{"decision":"SKIP","reason":"x"}""",
            """{"decision":"skip","reason":"x","tagId":"${Uuid.random()}"}""",
            """{"decision":"skip","reason":"x","addTagId":"${Uuid.random()}"}""",
            """{"decision":"skip","reason":"x","removeTagId":"${Uuid.random()}"}""",
            """{"decision":"skip","reason":"x","filter":"GITHUB_ISSUE_COMPLETION"}""",
            """{"decision":"skip","reason":1}""",
        ).forEach { raw ->
            assertThrows(HookOutputException::class.java) {
                TransitionConversationTagsHookOutputParser.parse(raw)
            }
        }
    }

    @Test
    fun tagTransitionParserBoundsAndTruncatesResponse() {
        val reason = "😀".repeat(501)
        val parsed = TransitionConversationTagsHookOutputParser.parse(
            """{"decision":"skip","reason":"$reason"}"""
        )
        val oversized = assertThrows(HookOutputException::class.java) {
            TransitionConversationTagsHookOutputParser.parse(
                "x".repeat(HookRuntimeRules.MAX_TAG_TRANSITION_RESPONSE_CHARS + 1)
            )
        }

        assertEquals(500, parsed.reason.codePointCount(0, parsed.reason.length))
        assertTrue(parsed.reasonTruncated)
        assertEquals(HookErrorCode.SCHEMA_MISMATCH, oversized.code)
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

    @Test
    fun manageTagsParserParsesApplyAndSkip() {
        val tagId = Uuid.random()
        val apply = ManageConversationTagsHookOutputParser.parse(
            """{"decision":"apply","operations":[{"op":"add","tagId":"$tagId"}],"reason":" done "}"""
        )
        val skip = ManageConversationTagsHookOutputParser.parse(
            """{"decision":"skip","operations":[],"reason":"no"}"""
        )

        assertEquals(HookDecision.APPLY, apply.decision)
        assertEquals(1, apply.operations.size)
        assertEquals(TagManageOpKind.ADD, apply.operations.single().kind)
        assertEquals(tagId, apply.operations.single().tagId)
        assertEquals("done", apply.reason)
        assertEquals(HookDecision.SKIP, skip.decision)
        assertTrue(skip.operations.isEmpty())
    }

    @Test
    fun manageTagsParserRejectsInvalidShapes() {
        val tagId = Uuid.random()
        listOf(
            """{"decision":"skip","operations":[{"op":"add","tagId":"$tagId"}],"reason":"x"}""",
            """{"decision":"apply","operations":[],"reason":"x"}""",
            """{"decision":"apply","operations":[{"op":"move","tagId":"$tagId"}],"reason":"x"}""",
            """{"decision":"apply","tagId":"$tagId","reason":"x"}""",
            """{"decision":"apply","operations":[{"op":"add","tagId":"bad"}],"reason":"x"}""",
        ).forEach { raw ->
            val error = assertThrows(HookOutputException::class.java) {
                ManageConversationTagsHookOutputParser.parse(raw)
            }
            assertEquals(HookErrorCode.SCHEMA_MISMATCH, error.code)
        }
    }

    @Test
    fun manageTagsParserRejectsTooManyOperations() {
        val ops = (1..HookRuntimeRules.MAX_TAG_MANAGE_OPS + 1).joinToString(",") { index ->
            val tagId = Uuid.parse("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}")
            """{"op":"add","tagId":"$tagId"}"""
        }
        val error = assertThrows(HookOutputException::class.java) {
            ManageConversationTagsHookOutputParser.parse(
                """{"decision":"apply","operations":[$ops],"reason":"too many"}"""
            )
        }
        assertEquals(HookErrorCode.SCHEMA_MISMATCH, error.code)
    }

    @Test
    fun manageTagsParserAcceptsMultiOpWithinLimit() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val parsed = ManageConversationTagsHookOutputParser.parse(
            """{"decision":"apply","operations":[{"op":"remove","tagId":"$first"},{"op":"add","tagId":"$second"}],"reason":"swap"}"""
        )

        assertEquals(HookDecision.APPLY, parsed.decision)
        assertEquals(2, parsed.operations.size)
        assertEquals(TagManageOpKind.REMOVE, parsed.operations[0].kind)
        assertEquals(first, parsed.operations[0].tagId)
        assertEquals(TagManageOpKind.ADD, parsed.operations[1].kind)
        assertEquals(second, parsed.operations[1].tagId)
    }

    private fun validSyncJson(): String =
        """{"decision":"skip","baseRevision":1,"operations":[],"reason":"x"}"""
}
