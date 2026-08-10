package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepositorySyncOpsTest {

    @Test
    fun `light conversation mapping never includes message nodes`() {
        val assistantId = Uuid.random()
        val conversationId = Uuid.random()
        val chatModelId = Uuid.random()
        val folderId = Uuid.random()
        val summary = lightConversationEntityToConversation(
            LightConversationEntity(
                id = conversationId.toString(),
                assistantId = assistantId.toString(),
                chatModelId = chatModelId.toString(),
                title = "Recent",
                isPinned = true,
                createAt = 10,
                updateAt = 20,
                folderId = folderId.toString(),
            )
        )

        assertEquals(conversationId, summary.id)
        assertEquals(assistantId, summary.assistantId)
        assertEquals("Recent", summary.title)
        assertTrue(summary.isPinned)
        assertEquals(Instant.ofEpochMilli(10), summary.createAt)
        assertEquals(Instant.ofEpochMilli(20), summary.updateAt)
        assertEquals(chatModelId, summary.chatModelId)
        assertEquals(folderId, summary.folderId)
        assertTrue(summary.messageNodes.isEmpty())
        assertTrue(summary.chatSuggestions.isEmpty())
        assertTrue(summary.lorebookIds.isEmpty())
        assertNull(summary.customSystemPrompt)
        assertNull(summary.workspaceCwd)
        assertFalse(summary.memoryTableIsolation)
    }

    @Test
    fun `batch mapper handles exact boundaries and preserves global indices`() = runBlocking {
        suspend fun consume(count: Int): Pair<List<Int>, List<Int>> {
            val batchSizes = mutableListOf<Int>()
            val consumed = mutableListOf<Int>()
            mapAndConsumeInBatches(
                items = (0 until count).toList(),
                batchSize = 64,
                transform = { index, item -> index + item },
                consume = { batch ->
                    batchSizes += batch.size
                    consumed += batch
                },
            )
            return batchSizes to consumed
        }

        assertEquals(emptyList<Int>(), consume(0).first)
        assertEquals(listOf(3), consume(3).first)
        assertEquals(listOf(64), consume(64).first)
        assertEquals(listOf(64, 1), consume(65).first)
        val (batchSizes, consumed) = consume(130)
        assertEquals(listOf(64, 64, 2), batchSizes)
        assertEquals((0 until 130).map { it * 2 }, consumed)
    }

    @Test
    fun `batch mapper rejects non-positive sizes`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                mapAndConsumeInBatches(
                    items = listOf(1),
                    batchSize = 0,
                    transform = { _, item -> item },
                    consume = {},
                )
            }
        }
    }

    @Test
    fun `diagnostic message locks metadata whitelist and field order`() {
        val message = buildConversationNodeDiagnosticMessage(
            operation = "get_by_id",
            phase = "read_complete",
            conversationId = "conversation-a",
            pageCount = 4,
            loadedNodeCount = 256,
            totalNodeCount = 256,
            totalMessageChars = 4_200_000,
            elapsedMs = 123,
            heapUsedBytes = 100,
            heapMaxBytes = 512,
            errorType = "SQLiteBlobTooBigException",
        )

        assertEquals(
            "operation=get_by_id phase=read_complete conversationId=conversation-a " +
                "pageCount=4 loadedNodeCount=256 totalNodeCount=256 " +
                "totalMessageChars=4200000 elapsedMs=123 heapUsedBytes=100 " +
                "heapMaxBytes=512 errorType=SQLiteBlobTooBigException",
            message,
        )
    }

    @Test
    fun `write diagnostic uses dao completion count without claiming commit`() {
        val message = buildConversationNodeDiagnosticMessage(
            operation = "insert",
            phase = "write_failed",
            conversationId = "conversation-a",
            plannedNodeCount = 65,
            writeBatchCount = 1,
            completedDaoNodeCount = 64,
            serializedChars = 1000,
            elapsedMs = 10,
            heapUsedBytes = 20,
            heapMaxBytes = 30,
            errorType = "SQLiteException",
        )

        assertEquals(
            "operation=insert phase=write_failed conversationId=conversation-a plannedNodeCount=65 " +
                "writeBatchCount=1 completedDaoNodeCount=64 serializedChars=1000 elapsedMs=10 " +
                "heapUsedBytes=20 heapMaxBytes=30 errorType=SQLiteException",
            message,
        )
    }

    @Test
    fun `diagnostic thresholds include exact boundaries`() {
        assertFalse(shouldLogConversationNodeDiagnostics(255, 3_999_999))
        assertTrue(shouldLogConversationNodeDiagnostics(256, 0))
        assertTrue(shouldLogConversationNodeDiagnostics(0, 4_000_000))
    }

    private class OversizedPageException : RuntimeException()

    @Test
    fun `paged read retries the same offset with one row then restores page size`() = runBlocking {
        val calls = mutableListOf<Pair<Int, Int>>()
        val consumed = mutableListOf<Int>()
        var firstPageFailed = false

        val stats = consumePagedRowsWithSingleRowFallback(
            pageSize = 64,
            load = { limit, offset ->
                calls += limit to offset
                if (!firstPageFailed && limit == 64 && offset == 0) {
                    firstPageFailed = true
                    throw OversizedPageException()
                }
                (offset until minOf(offset + limit, 3)).toList()
            },
            isOversizedPage = { it is OversizedPageException },
            consume = { consumed += it },
            onSingleRowFailure = { _, _ -> error("No row should fail") },
        )

        assertEquals(listOf(64 to 0, 1 to 0, 64 to 1, 64 to 3), calls)
        assertEquals(listOf(0, 1, 2), consumed)
        assertEquals(PagedReadStats(pageCount = 2), stats)
    }

    @Test
    fun `paged read rethrows when a single row is oversized`() {
        val consumed = mutableListOf<Int>()
        val failedOffsets = mutableListOf<Int>()

        assertThrows(OversizedPageException::class.java) {
            runBlocking {
                consumePagedRowsWithSingleRowFallback(
                    pageSize = 64,
                    load = { limit, offset ->
                        if ((limit == 64 && offset <= 1) || (limit == 1 && offset == 1)) {
                            throw OversizedPageException()
                        }
                        (offset until minOf(offset + limit, 3)).toList()
                    },
                    isOversizedPage = { it is OversizedPageException },
                    consume = { consumed += it },
                    onSingleRowFailure = { offset, _ -> failedOffsets += offset },
                )
            }
        }

        assertEquals(listOf(0), consumed)
        assertEquals(listOf(1), failedOffsets)
    }

    @Test
    fun `paged read rethrows non oversized failures`() {
        val failure = IllegalStateException("decode failed")

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                consumePagedRowsWithSingleRowFallback<Int>(
                    pageSize = 64,
                    load = { _, _ -> throw failure },
                    isOversizedPage = { it is OversizedPageException },
                    consume = {},
                    onSingleRowFailure = { _, _ -> error("No row should fail") },
                )
            }
        }

        assertSame(failure, thrown)
    }

    @Test
    fun `unicode character count matches sqlite text length semantics`() {
        assertEquals(3L, countUnicodeCodePoints("A😀B"))
    }

    private fun node(id: Uuid, selectIndex: Int = 0) = MessageNode(
        id = id,
        messages = listOf(UIMessage(role = MessageRole.USER, parts = emptyList())),
        selectIndex = selectIndex,
    )

    @Test
    fun `orphan existing ids are marked for delete`() {
        val keep = Uuid.random()
        val orphan = Uuid.random()
        val (deleteIds, upsert) = computeNodeSyncOps(
            existingIds = listOf(keep.toString(), orphan.toString()),
            newNodes = listOf(node(keep)),
        )
        assertEquals(listOf(orphan.toString()), deleteIds)
        assertEquals(1, upsert.size)
        assertEquals(keep, upsert[0].id)
    }

    @Test
    fun `new node id appears in upsert list`() {
        val existing = Uuid.random()
        val added = Uuid.random()
        val (_, upsert) = computeNodeSyncOps(
            existingIds = listOf(existing.toString()),
            newNodes = listOf(node(existing), node(added)),
        )
        assertEquals(2, upsert.size)
        assertEquals(listOf(existing, added), upsert.map { it.id })
    }

    @Test
    fun `same id with updated selectIndex is fully represented in upsert`() {
        val id = Uuid.random()
        val updated = node(id, selectIndex = 2)
        val (deleteIds, upsert) = computeNodeSyncOps(
            existingIds = listOf(id.toString()),
            newNodes = listOf(updated),
        )
        assertTrue(deleteIds.isEmpty())
        assertEquals(1, upsert.size)
        assertEquals(2, upsert[0].selectIndex)
    }
}
