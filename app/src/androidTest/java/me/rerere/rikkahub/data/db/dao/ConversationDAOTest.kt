package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.utils.JsonInstant
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
    private lateinit var appScope: AppScope
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.conversationDao()
        appScope = AppScope()
        repository = ConversationRepository(
            conversationDAO = dao,
            messageNodeDAO = database.messageNodeDao(),
            favoriteDAO = database.favoriteDao(),
            database = database,
            filesManager = FilesManager(context, FilesRepository(database.managedFileDao()), appScope),
            messageFtsManager = MessageFtsManager(database),
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
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

    @Test
    fun recentConversationSummaryPreservesOrderingLimitFieldsAndAssistantIsolation() = runBlocking {
        val targetAssistant = Uuid.random().toString()
        val otherAssistant = Uuid.random().toString()
        val chatModelId = Uuid.random().toString()
        val folderId = Uuid.random().toString()
        val pinned = entity(
            id = Uuid.random().toString(),
            assistantId = targetAssistant,
            title = "Pinned",
            now = 1_000,
            isPinned = true,
            chatModelId = chatModelId,
            folderId = folderId,
        )
        val newest = entity(Uuid.random().toString(), targetAssistant, "Newest", 3_000)
        val older = entity(Uuid.random().toString(), targetAssistant, "Older", 2_000)
        dao.insert(older)
        dao.insert(newest)
        dao.insert(pinned)
        dao.insert(entity(Uuid.random().toString(), otherAssistant, "Other", 4_000, isPinned = true))
        database.messageNodeDao().insert(
            MessageNodeEntity(
                id = Uuid.random().toString(),
                conversationId = pinned.id,
                nodeIndex = 0,
                messages = "not-valid-message-json",
                selectIndex = 0,
            )
        )

        val summaries = dao.getRecentConversationsOfAssistant(targetAssistant, limit = 2)
        val recentConversations = repository.getRecentConversations(Uuid.parse(targetAssistant), limit = 2)

        assertEquals(listOf(pinned.id, newest.id), summaries.map { it.id })
        assertEquals(listOf(pinned.id, newest.id), recentConversations.map { it.id.toString() })
        assertEquals(listOf("Pinned", "Newest"), recentConversations.map { it.title })
        assertEquals(listOf(1_000L, 3_000L), recentConversations.map { it.updateAt.toEpochMilli() })
        assertEquals(listOf(0, 0), recentConversations.map { it.messageNodes.size })
        assertEquals(targetAssistant, summaries[0].assistantId)
        assertEquals(chatModelId, summaries[0].chatModelId)
        assertEquals("Pinned", summaries[0].title)
        assertEquals(true, summaries[0].isPinned)
        assertEquals(1_000L, summaries[0].createAt)
        assertEquals(1_000L, summaries[0].updateAt)
        assertEquals(folderId, summaries[0].folderId)
    }

    @Test
    fun repositoryLoadsAllPagedNodesInOrderWithStateAndFavorites() = runBlocking {
        val assistantId = Uuid.random().toString()
        val conversationId = Uuid.random().toString()
        val nodeIds = List(65) { Uuid.random().toString() }
        dao.insert(entity(conversationId, assistantId, "Paged", 1_000))
        database.messageNodeDao().insertAll(
            nodeIds.indices.reversed().map { index ->
                MessageNodeEntity(
                    id = nodeIds[index],
                    conversationId = conversationId,
                    nodeIndex = index,
                    messages = JsonInstant.encodeToString(
                        listOf(UIMessage(role = MessageRole.USER, parts = emptyList()))
                    ),
                    selectIndex = 0,
                    hidden = index == 64,
                    compressHiddenCount = if (index == 64) 3 else null,
                )
            }
        )
        database.favoriteDao().upsert(
            FavoriteEntity(
                id = Uuid.random().toString(),
                type = "node",
                refKey = "node:$conversationId:${nodeIds[64]}",
                refJson = "{}",
                snapshotJson = "{}",
                createdAt = 1,
                updatedAt = 1,
            )
        )

        val conversation = repository.getConversationById(Uuid.parse(conversationId))!!

        assertEquals(nodeIds, conversation.messageNodes.map { it.id.toString() })
        assertEquals(65, conversation.messageNodes.size)
        assertEquals(0, conversation.messageNodes[64].selectIndex)
        assertEquals(true, conversation.messageNodes[64].hidden)
        assertEquals(3, conversation.messageNodes[64].compressHiddenCount)
        assertEquals(true, conversation.messageNodes[64].isFavorite)
        assertEquals(1, conversation.messageNodes[64].messages.size)
    }

    private fun entity(
        id: String,
        assistantId: String,
        title: String,
        now: Long,
        isPinned: Boolean = false,
        chatModelId: String = "",
        folderId: String = "",
    ) = ConversationEntity(
        id = id,
        assistantId = assistantId,
        chatModelId = chatModelId,
        title = title,
        nodes = "[]",
        createAt = now,
        updateAt = now,
        chatSuggestions = "[]",
        isPinned = isPinned,
        folderId = folderId,
    )
}
