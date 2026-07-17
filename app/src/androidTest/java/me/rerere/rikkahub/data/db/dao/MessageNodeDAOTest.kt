package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageNodeDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: MessageNodeDAO

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageNodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun nodeIdProjectionAndReadSummaryAreConversationScoped() = runBlocking {
        insertConversation("conversation-a")
        insertConversation("conversation-b")
        insertConversation("conversation-empty")
        val firstMessages = "[\"large-😀-payload-a\"]"
        val secondMessages = "[\"large-payload-b\"]"
        dao.insertAll(
            listOf(
                node(
                    id = "second",
                    conversationId = "conversation-a",
                    nodeIndex = 1,
                    messages = secondMessages,
                ),
                node(
                    id = "first",
                    conversationId = "conversation-a",
                    nodeIndex = 0,
                    messages = firstMessages,
                ),
                node(
                    id = "other",
                    conversationId = "conversation-b",
                    nodeIndex = 0,
                    messages = "[\"other-conversation\"]",
                ),
            )
        )

        assertEquals(listOf("first", "second"), dao.getNodeIdsOfConversation("conversation-a"))
        assertEquals(
            MessageNodeReadSummary(
                nodeCount = 2,
                messageChars = firstMessages.codePointCount(0, firstMessages.length).toLong() +
                    secondMessages.codePointCount(0, secondMessages.length).toLong(),
            ),
            dao.getReadSummary("conversation-a"),
        )
        assertEquals(MessageNodeReadSummary(0, 0), dao.getReadSummary("conversation-empty"))
    }

    private suspend fun insertConversation(id: String) {
        database.conversationDao().insert(
            ConversationEntity(
                id = id,
                assistantId = "assistant",
                title = id,
                nodes = "[]",
                createAt = 1,
                updateAt = 1,
                chatSuggestions = "[]",
                isPinned = false,
            )
        )
    }

    private fun node(
        id: String,
        conversationId: String,
        nodeIndex: Int,
        messages: String,
    ) = MessageNodeEntity(
        id = id,
        conversationId = conversationId,
        nodeIndex = nodeIndex,
        messages = messages,
        selectIndex = 0,
        hidden = false,
        compressHiddenCount = 0,
    )
}
