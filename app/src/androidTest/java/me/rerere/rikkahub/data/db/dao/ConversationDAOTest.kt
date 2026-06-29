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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
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
    fun getConversationsOfAssistant_excludesArchived() = runBlocking {
        val assistantId = Uuid.random().toString()
        val activeId = Uuid.random().toString()
        val archivedId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()

        dao.insert(entity(id = activeId, assistantId = assistantId, title = "Active", now = now))
        dao.insert(
            entity(
                id = archivedId,
                assistantId = assistantId,
                title = "Archived",
                now = now,
                isArchived = true,
                archivedAt = now,
            ),
        )

        val result = dao.getConversationsOfAssistant(assistantId).first()

        assertEquals(1, result.size)
        assertEquals(activeId, result[0].id)
    }

    @Test
    fun getArchivedConversations_ordersByArchivedAtDesc() = runBlocking {
        val assistantId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()
        val idOld = Uuid.random().toString()
        val idMid = Uuid.random().toString()
        val idNew = Uuid.random().toString()
        val activeId = Uuid.random().toString()

        dao.insert(entity(id = activeId, assistantId = assistantId, title = "Active", now = now))
        dao.insert(
            entity(
                id = idOld,
                assistantId = assistantId,
                title = "Old",
                now = now,
                isArchived = true,
                archivedAt = now - 3_000,
            ),
        )
        dao.insert(
            entity(
                id = idNew,
                assistantId = assistantId,
                title = "New",
                now = now,
                isArchived = true,
                archivedAt = now,
            ),
        )
        dao.insert(
            entity(
                id = idMid,
                assistantId = assistantId,
                title = "Mid",
                now = now,
                isArchived = true,
                archivedAt = now - 1_000,
            ),
        )

        val archived = dao.getArchivedConversations().first()

        assertEquals(3, archived.size)
        assertEquals(listOf(idNew, idMid, idOld), archived.map { it.id })
    }

    @Test
    fun getArchivedCount_returnsOnlyArchivedRows() = runBlocking {
        val assistantId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()

        dao.insert(entity(id = Uuid.random().toString(), assistantId = assistantId, title = "A", now = now))
        dao.insert(
            entity(
                id = Uuid.random().toString(),
                assistantId = assistantId,
                title = "B",
                now = now,
                isArchived = true,
                archivedAt = now,
            ),
        )
        dao.insert(
            entity(
                id = Uuid.random().toString(),
                assistantId = assistantId,
                title = "C",
                now = now,
                isArchived = true,
                archivedAt = now + 1,
            ),
        )

        assertEquals(2, dao.getArchivedCount().first())
    }

    @Test
    fun updateArchiveStatus_flipsRowIntoArchivedList() = runBlocking {
        val assistantId = Uuid.random().toString()
        val id = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()
        val archivedAt = now + 5_000

        dao.insert(entity(id = id, assistantId = assistantId, title = "To archive", now = now))
        dao.updateArchiveStatus(id = id, archived = true, archivedAt = archivedAt)

        val archived = dao.getArchivedConversations().first()
        assertEquals(1, archived.size)
        assertEquals(id, archived[0].id)
        assertTrue(archived[0].isArchived)
        assertEquals(archivedAt, archived[0].archivedAt)
    }

    @Test
    fun searchArchivedConversations_filtersByTitle() = runBlocking {
        val assistantId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()

        dao.insert(
            entity(
                id = Uuid.random().toString(),
                assistantId = assistantId,
                title = "Project Alpha archive",
                now = now,
                isArchived = true,
                archivedAt = now,
            ),
        )
        dao.insert(
            entity(
                id = Uuid.random().toString(),
                assistantId = assistantId,
                title = "Other topic",
                now = now,
                isArchived = true,
                archivedAt = now + 1,
            ),
        )

        val hits = dao.searchArchivedConversations("Alpha").first()

        assertEquals(1, hits.size)
        assertTrue(hits[0].title.contains("Alpha"))
    }

    @Test
    fun getPinnedConversations_excludesArchivedPinned() = runBlocking {
        val assistantId = Uuid.random().toString()
        val now = Instant.now().toEpochMilli()
        val pinnedActive = Uuid.random().toString()
        val pinnedArchived = Uuid.random().toString()

        dao.insert(
            entity(
                id = pinnedActive,
                assistantId = assistantId,
                title = "Pinned active",
                now = now,
                isPinned = true,
            ),
        )
        dao.insert(
            entity(
                id = pinnedArchived,
                assistantId = assistantId,
                title = "Pinned archived",
                now = now,
                isPinned = true,
                isArchived = true,
                archivedAt = now,
            ),
        )

        val pinned = dao.getPinnedConversations().first()

        assertEquals(1, pinned.size)
        assertEquals(pinnedActive, pinned[0].id)
    }

    private fun entity(
        id: String,
        assistantId: String,
        title: String,
        now: Long,
        isPinned: Boolean = false,
        isArchived: Boolean = false,
        archivedAt: Long = 0L,
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        title = title,
        nodes = "[]",
        createAt = now,
        updateAt = now,
        chatSuggestions = "[]",
        isPinned = isPinned,
        isArchived = isArchived,
        archivedAt = archivedAt,
    )
}