package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookDecision
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
}
