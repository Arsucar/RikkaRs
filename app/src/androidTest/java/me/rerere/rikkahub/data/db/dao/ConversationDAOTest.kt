package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ConversationDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ConversationDAO

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.conversationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ordinaryQueriesReturnEveryStoredConversation() = runBlocking {
        val assistantId = Uuid.random().toString()
        val first = entity(Uuid.random().toString(), assistantId, "First", 1_000)
        val second = entity(Uuid.random().toString(), assistantId, "Second", 2_000)
        dao.insert(first)
        dao.insert(second)

        assertEquals(listOf(second.id, first.id), dao.getAll().first().map { it.id })
        assertEquals(listOf(second.id, first.id), dao.getConversationsOfAssistant(assistantId).first().map { it.id })
        assertEquals(listOf(second.id), dao.searchConversations("Second").first().map { it.id })
        assertEquals(2, dao.countAll())
    }

    @Test
    fun latestConversationUsesUpdateTimeAndAssistantIsolation() = runBlocking {
        val targetAssistant = Uuid.random().toString()
        val otherAssistant = Uuid.random().toString()
        val olderPinned = entity(Uuid.random().toString(), targetAssistant, "Pinned", 1_000, isPinned = true)
        val newer = entity(Uuid.random().toString(), targetAssistant, "Newer", 2_000)
        val newestOther = entity(Uuid.random().toString(), otherAssistant, "Other", 3_000)
        dao.insert(olderPinned)
        dao.insert(newer)
        dao.insert(newestOther)

        assertEquals(newer.id, dao.getLatestActiveConversationIdOfAssistant(targetAssistant))
    }

    @Test
    fun pinnedQueryReturnsPinnedRowsOnly() = runBlocking {
        val assistantId = Uuid.random().toString()
        val pinned = entity(Uuid.random().toString(), assistantId, "Pinned", 1_000, isPinned = true)
        dao.insert(pinned)
        dao.insert(entity(Uuid.random().toString(), assistantId, "Normal", 2_000))

        assertEquals(listOf(pinned.id), dao.getPinnedConversations().first().map { it.id })
    }

    private fun entity(
        id: String,
        assistantId: String,
        title: String,
        now: Long,
        isPinned: Boolean = false,
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = title,
        nodes = "[]",
        createAt = now,
        updateAt = now,
        chatSuggestions = "[]",
        isPinned = isPinned,
    )
}
