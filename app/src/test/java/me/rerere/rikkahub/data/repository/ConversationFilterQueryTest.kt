package me.rerere.rikkahub.data.repository

import kotlin.uuid.Uuid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFilterQueryTest {
    @Test
    fun emptyFilterHasNoArchiveColumnsOrEmptyWhereClause() {
        val sql = buildConversationFilterQuery(ConversationFilter()).sql

        assertFalse(sql.contains("is_archived", ignoreCase = true))
        assertFalse(sql.contains("archived_at", ignoreCase = true))
        assertFalse(sql.contains("WHERE", ignoreCase = true))
        assertTrue(sql.contains("ORDER BY c.is_pinned DESC, c.update_at DESC, c.id DESC"))
    }

    @Test
    fun existingFiltersRemainComposed() {
        val sql = buildConversationFilterQuery(
            ConversationFilter(
                assistantId = Uuid.random(),
                folderId = Uuid.random(),
                searchText = "needle",
                tagIds = setOf(Uuid.random(), Uuid.random()),
            )
        ).sql

        assertTrue(sql.contains("c.assistant_id = ?"))
        assertTrue(sql.contains("c.folder_id = ?"))
        assertTrue(sql.contains("c.title LIKE '%' || ? || '%'"))
        assertTrue(sql.contains("conversation_tag_cross_ref"))
    }
}
