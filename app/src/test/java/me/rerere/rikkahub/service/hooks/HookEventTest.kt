package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookEvent
import me.rerere.rikkahub.data.model.HookEventType
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
        assertNull(detectConfiguredHookKeyword("tissue fixed", "issue"))
        assertEquals("#123", detectConfiguredHookKeyword("Resolved #123.", "#123"))
    }
}
