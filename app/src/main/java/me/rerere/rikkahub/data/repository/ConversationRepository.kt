package me.rerere.rikkahub.data.repository

import android.database.sqlite.SQLiteBlobTooBigException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.common.android.Logging
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.computeMessageStats
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FavoriteDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.MessageStatsDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MessageNodeEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val favoriteDAO: FavoriteDAO,
    private val database: AppDatabase,
    private val filesManager: FilesManager,
    private val messageFtsManager: MessageFtsManager,
    private val messageStatsDAO: MessageStatsDAO = database.messageStatsDao(),
) {
    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
        private const val MESSAGE_NODE_PAGE_SIZE = 64
        private const val MESSAGE_NODE_WRITE_BATCH_SIZE = 64
        private const val TAG = "ConversationRepository"
    }

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> {
        return conversationDAO.getRecentConversationsOfAssistant(
            assistantId = assistantId.toString(),
            limit = limit
        ).map(::lightConversationEntityToConversation)
    }

    suspend fun getLatestActiveConversationIdOfAssistant(assistantId: Uuid): Uuid? {
        return conversationDAO
            .getLatestActiveConversationIdOfAssistant(assistantId.toString())
            ?.let(Uuid::parse)
    }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    // 列表视图不需要完整的 nodes，使用空列表
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            lightConversationEntityToConversation(entity)
        }
    }

    fun getUnfiledConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            lightConversationEntityToConversation(entity)
        }
    }

    fun getConversationsOfFolderPaging(folderId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfFolderPaging(folderId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            lightConversationEntityToConversation(entity)
        }
    }

    suspend fun getConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.getConversationsOfAssistantPaging(assistantId.toString())
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        lightConversationEntityToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    suspend fun searchConversationsOfAssistantPage(
        assistantId: Uuid,
        titleKeyword: String,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.searchConversationsOfAssistantPaging(
            assistantId = assistantId.toString(),
            searchText = titleKeyword
        )
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        lightConversationEntityToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    suspend fun getUnfiledConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()),
        offset,
        limit,
    )

    suspend fun getConversationsOfFolderPage(
        folderId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getConversationsOfFolderPaging(folderId.toString()),
        offset,
        limit,
    )

    private suspend fun loadConversationPage(
        pagingSource: PagingSource<Int, LightConversationEntity>,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        lightConversationEntityToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { entities ->
                entities.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            lightConversationEntityToConversation(entity)
        }
    }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversationsOfAssistant(assistantId.toString(), titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                conversationDAO.searchConversationsOfAssistantPaging(
                    assistantId.toString(),
                    titleKeyword
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                lightConversationEntityToConversation(entity)
            }
        }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            val nodes = loadMessageNodes(entity.id, operation = "get_by_id")
            conversationEntityToConversation(entity, nodes)
        } else null
    }

    suspend fun existsConversationById(uuid: Uuid): Boolean {
        return conversationDAO.existsById(uuid.toString())
    }

    suspend fun countConversations(): Int {
        return conversationDAO.countAll()
    }

    suspend fun insertConversation(conversation: Conversation) {
        database.withTransaction {
            conversationDAO.insert(
                conversationToConversationEntity(conversation)
            )
            saveMessageNodes(
                conversationId = conversation.id.toString(),
                nodes = conversation.messageNodes,
                operation = "insert",
            )
            messageFtsManager.indexConversationInPlace(conversation)
        }
    }

    fun getConversationsPaging(filter: ConversationFilter): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            conversationDAO.getConversationsPaging(buildConversationFilterQuery(filter))
        },
    ).flow.map { pagingData ->
        pagingData.map(::lightConversationEntityToConversation)
    }

    /**
     * Persists a fork and copies all source tag relations in the same Room transaction.
     * Relations remain independent after this transaction because only their tag ids are copied.
     */
    suspend fun insertForkConversation(sourceConversationId: Uuid, fork: Conversation) {
        database.withTransaction {
            conversationDAO.insert(conversationToConversationEntity(fork))
            saveMessageNodes(
                conversationId = fork.id.toString(),
                nodes = fork.messageNodes,
                operation = "fork",
            )
            database.conversationTagDao().copyRelationsForFork(
                sourceConversationId = sourceConversationId.toString(),
                targetConversationId = fork.id.toString(),
            )
            messageFtsManager.indexConversationInPlace(fork)
        }
    }

    suspend fun updateConversation(conversation: Conversation) {
        database.withTransaction {
            conversationDAO.update(
                conversationToConversationEntity(conversation)
            )
            syncMessageNodes(
                conversationId = conversation.id.toString(),
                nodes = conversation.messageNodes,
                operation = "update",
            )
            messageFtsManager.indexConversationInPlace(conversation)
        }
    }

    suspend fun deleteConversation(conversation: Conversation) {
        // 获取完整的 Conversation（包含 messageNodes）以正确清理文件
        val fullConversation = if (conversation.messageNodes.isEmpty()) {
            getConversationById(conversation.id) ?: conversation
        } else {
            conversation
        }
        database.withTransaction {
            database.memoryTableDao().deleteMemoryTableDataForConversation(conversation.id.toString())
            // message_node 会通过 CASCADE 自动删除
            conversationDAO.delete(
                conversationToConversationEntity(conversation)
            )
            messageFtsManager.deleteConversationInPlace(conversation.id.toString())
        }
        filesManager.deleteChatFiles(fullConversation.files)
    }

    suspend fun searchMessages(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
    ) = messageFtsManager.search(keyword, sort)

    suspend fun rebuildAllIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        messageFtsManager.deleteAll()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val nodes = loadMessageNodes(entity.id, operation = "rebuild_index")
            val conversation = conversationEntityToConversation(entity, nodes)
            messageFtsManager.indexConversation(conversation)
            // indexConversation consumes the decoded tree synchronously, so this loop retains
            // only one full conversation at a time.
            onProgress(index + 1, total)
        }
    }

    suspend fun getConversationIdsOfAssistant(assistantId: Uuid): List<Uuid> {
        return conversationDAO.getIdsOfAssistant(assistantId.toString()).mapNotNull { id ->
            runCatching { Uuid.parse(id) }.getOrNull()
        }
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        val assistantKey = assistantId.toString()
        val ids = conversationDAO.getIdsOfAssistant(assistantKey)
        if (ids.isEmpty()) return

        // Collect chat files one conversation at a time to avoid loading the full entity list.
        val filesToDelete = mutableListOf<Uri>()
        for (id in ids) {
            val uuid = runCatching { Uuid.parse(id) }.getOrNull() ?: continue
            val full = getConversationById(uuid) ?: continue
            filesToDelete.addAll(full.files)
        }

        database.withTransaction {
            for (id in ids) {
                database.memoryTableDao().deleteMemoryTableDataForConversation(id)
            }
            conversationDAO.deleteByAssistantId(assistantKey)
            messageFtsManager.deleteConversationsInPlace(ids)
        }
        if (filesToDelete.isNotEmpty()) {
            filesManager.deleteChatFiles(filesToDelete)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        require(conversation.messageNodes.none { it.messages.any { message -> message.hasBase64Part() } })
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = "[]",  // nodes 现在存储在单独的表中
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            chatModelId = conversation.chatModelId?.toString() ?: "",
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            customSystemPrompt = conversation.customSystemPrompt ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
            folderId = conversation.folderId?.toString() ?: "",
            memoryTableIsolation = conversation.memoryTableIsolation,
        )
    }

    fun conversationEntityToConversation(
        conversationEntity: ConversationEntity,
        messageNodes: List<MessageNode>
    ): Conversation {
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messageNodes = messageNodes.filter { it.messages.isNotEmpty() },
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId),
            chatModelId = conversationEntity.chatModelId.ifEmpty { null }?.let { Uuid.parse(it) },
            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),
            isPinned = conversationEntity.isPinned,
            customSystemPrompt = conversationEntity.customSystemPrompt.ifEmpty { null },
            modeInjectionIds = JsonInstant.decodeFromString(conversationEntity.modeInjectionIds),
            lorebookIds = JsonInstant.decodeFromString(conversationEntity.lorebookIds),
            workspaceCwd = conversationEntity.workspaceCwd.ifEmpty { null },
            folderId = conversationEntity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
            memoryTableIsolation = conversationEntity.memoryTableIsolation,
        )
    }

    fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDAO
            .getPinnedConversations()
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    suspend fun togglePinStatus(conversationId: Uuid) {
        setPinStatus(
            conversationId,
            !(getConversationById(conversationId)?.isPinned ?: false),
        )
    }

    suspend fun setPinStatus(conversationId: Uuid, isPinned: Boolean) {
        conversationDAO.updatePinStatus(
            id = conversationId.toString(),
            isPinned = isPinned,
        )
    }

    /**
     * 单列更新会话的文件夹归属，folderId 为 null 表示移出文件夹（未归类）。
     */
    suspend fun updateConversationFolderId(conversationId: Uuid, folderId: Uuid?) {
        conversationDAO.updateFolderId(
            id = conversationId.toString(),
            folderId = folderId?.toString() ?: ""
        )
    }

    private suspend fun loadMessageNodes(
        conversationId: String,
        operation: String,
    ): List<MessageNode> {
        val favoriteNodeIds = favoriteDAO
            .getFavoriteNodeIdsOfConversation(conversationId)
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
            .toSet()

        return database.withTransaction {
            val startedAtNanos = System.nanoTime()
            val readSummary = try {
                messageNodeDAO.getReadSummary(conversationId)
            } catch (error: Exception) {
                logConversationNodeDiagnostics(
                    operation = operation,
                    phase = "read_summary_failed",
                    conversationId = conversationId,
                    startedAtNanos = startedAtNanos,
                    errorType = error::class.simpleName,
                )
                throw error
            }
            val nodes = mutableListOf<MessageNode>()
            val diagnosticsEnabled = shouldLogConversationNodeDiagnostics(
                nodeCount = readSummary.nodeCount,
                serializedChars = readSummary.messageChars,
            )
            if (diagnosticsEnabled) {
                logConversationNodeDiagnostics(
                    operation = operation,
                    phase = "read_start",
                    conversationId = conversationId,
                    totalNodeCount = readSummary.nodeCount,
                    totalMessageChars = readSummary.messageChars,
                    startedAtNanos = startedAtNanos,
                )
            }

            val readStats = try {
                consumePagedRowsWithSingleRowFallback(
                    pageSize = MESSAGE_NODE_PAGE_SIZE,
                    load = { limit, offset ->
                        messageNodeDAO.getNodesOfConversationPaged(conversationId, limit, offset)
                    },
                    isOversizedPage = { it is SQLiteBlobTooBigException },
                    consume = { page ->
                        page.forEach { entity ->
                            val nodeId = Uuid.parse(entity.id)
                            nodes.add(
                                messageNodeEntityToMessageNode(
                                    entity = entity,
                                    isFavorite = favoriteNodeIds.contains(nodeId),
                                )
                            )
                        }
                    },
                    onSingleRowFailure = { offset, error ->
                        logConversationNodeDiagnostics(
                            operation = operation,
                            phase = "read_row_failed",
                            conversationId = conversationId,
                            loadedNodeCount = nodes.size,
                            totalNodeCount = readSummary.nodeCount,
                            totalMessageChars = readSummary.messageChars,
                            rowOffset = offset,
                            startedAtNanos = startedAtNanos,
                            errorType = error::class.simpleName,
                        )
                    },
                )
            } catch (error: Exception) {
                logConversationNodeDiagnostics(
                    operation = operation,
                    phase = "read_failed",
                    conversationId = conversationId,
                    loadedNodeCount = nodes.size,
                    totalNodeCount = readSummary.nodeCount,
                    totalMessageChars = readSummary.messageChars,
                    startedAtNanos = startedAtNanos,
                    errorType = error::class.simpleName,
                )
                throw error
            }
            if (diagnosticsEnabled) {
                logConversationNodeDiagnostics(
                    operation = operation,
                    phase = "read_complete",
                    conversationId = conversationId,
                    pageCount = readStats.pageCount,
                    loadedNodeCount = nodes.size,
                    totalNodeCount = readSummary.nodeCount,
                    totalMessageChars = readSummary.messageChars,
                    startedAtNanos = startedAtNanos,
                )
            }
            nodes
        }
    }

    private suspend fun saveMessageNodes(
        conversationId: String,
        nodes: List<MessageNode>,
        operation: String,
    ) {
        val startedAtNanos = System.nanoTime()
        var persistedNodeCount = 0
        var serializedChars = 0L
        var writeBatchCount = 0
        var diagnosticsLogged = false
        if (shouldLogConversationNodeDiagnostics(nodes.size.toLong(), 0)) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "write_start",
                conversationId = conversationId,
                plannedNodeCount = nodes.size,
                startedAtNanos = startedAtNanos,
            )
            diagnosticsLogged = true
        }
        try {
            mapAndConsumeInBatches(
                items = nodes,
                batchSize = MESSAGE_NODE_WRITE_BATCH_SIZE,
                transform = { index, node ->
                    messageNodeToEntity(
                        node = node,
                        conversationId = conversationId,
                        nodeIndex = index,
                    ).also { serializedChars += countUnicodeCodePoints(it.messages) }
                },
                consume = { entities ->
                    if (!diagnosticsLogged && shouldLogConversationNodeDiagnostics(0, serializedChars)) {
                        logConversationNodeDiagnostics(
                            operation = operation,
                            phase = "write_start",
                            conversationId = conversationId,
                            plannedNodeCount = nodes.size,
                            writeBatchCount = writeBatchCount,
                            completedDaoNodeCount = persistedNodeCount,
                            serializedChars = serializedChars,
                            startedAtNanos = startedAtNanos,
                        )
                        diagnosticsLogged = true
                    }
                    messageNodeDAO.insertAll(entities)
                    writeBatchCount++
                    persistedNodeCount += entities.size
                },
            )
            upsertMessageStats(conversationId, nodes)
        } catch (error: Exception) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "write_failed",
                conversationId = conversationId,
                plannedNodeCount = nodes.size,
                writeBatchCount = writeBatchCount,
                completedDaoNodeCount = persistedNodeCount,
                serializedChars = serializedChars,
                startedAtNanos = startedAtNanos,
                errorType = error::class.simpleName,
            )
            throw error
        }
        if (diagnosticsLogged) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "write_complete",
                conversationId = conversationId,
                plannedNodeCount = nodes.size,
                writeBatchCount = writeBatchCount,
                completedDaoNodeCount = persistedNodeCount,
                serializedChars = serializedChars,
                startedAtNanos = startedAtNanos,
            )
        }
    }

    private suspend fun syncMessageNodes(
        conversationId: String,
        nodes: List<MessageNode>,
        operation: String,
    ) {
        val existingIds = messageNodeDAO.getNodeIdsOfConversation(conversationId)
        val (deleteIds, upsertNodes) = computeNodeSyncOps(
            existingIds = existingIds,
            newNodes = nodes,
        )
        val startedAtNanos = System.nanoTime()
        var serializedChars = 0L
        var writeCallCount = 0
        var diagnosticsLogged = false
        if (shouldLogConversationNodeDiagnostics(upsertNodes.size.toLong(), 0)) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "sync_start",
                conversationId = conversationId,
                plannedNodeCount = upsertNodes.size,
                deleteNodeCount = deleteIds.size,
                startedAtNanos = startedAtNanos,
            )
            diagnosticsLogged = true
        }
        try {
            deleteIds.forEach { messageNodeDAO.deleteById(it) }
            upsertNodes.forEachIndexed { index, node ->
                val entity = messageNodeToEntity(
                    node = node,
                    conversationId = conversationId,
                    nodeIndex = index,
                )
                serializedChars += countUnicodeCodePoints(entity.messages)
                if (!diagnosticsLogged && shouldLogConversationNodeDiagnostics(0, serializedChars)) {
                    logConversationNodeDiagnostics(
                        operation = operation,
                        phase = "sync_start",
                        conversationId = conversationId,
                        plannedNodeCount = upsertNodes.size,
                        deleteNodeCount = deleteIds.size,
                        writeCallCount = writeCallCount,
                        completedDaoNodeCount = writeCallCount,
                        serializedChars = serializedChars,
                        startedAtNanos = startedAtNanos,
                    )
                    diagnosticsLogged = true
                }
                messageNodeDAO.insert(entity)
                writeCallCount++
            }
            upsertMessageStats(conversationId, nodes)
        } catch (error: Exception) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "sync_failed",
                conversationId = conversationId,
                plannedNodeCount = upsertNodes.size,
                deleteNodeCount = deleteIds.size,
                writeCallCount = writeCallCount,
                completedDaoNodeCount = writeCallCount,
                serializedChars = serializedChars,
                startedAtNanos = startedAtNanos,
                errorType = error::class.simpleName,
            )
            throw error
        }
        if (diagnosticsLogged) {
            logConversationNodeDiagnostics(
                operation = operation,
                phase = "sync_complete",
                conversationId = conversationId,
                plannedNodeCount = upsertNodes.size,
                deleteNodeCount = deleteIds.size,
                writeCallCount = writeCallCount,
                completedDaoNodeCount = writeCallCount,
                serializedChars = serializedChars,
                startedAtNanos = startedAtNanos,
            )
        }
    }

    private suspend fun upsertMessageStats(conversationId: String, nodes: List<MessageNode>) {
        val computed = computeMessageStats(conversationId, nodes)
        messageStatsDAO.replaceConversationStats(computed.stats, computed.daily)
    }

    private fun logConversationNodeDiagnostics(
        operation: String,
        phase: String,
        conversationId: String,
        pageCount: Int? = null,
        loadedNodeCount: Int? = null,
        totalNodeCount: Long? = null,
        totalMessageChars: Long? = null,
        plannedNodeCount: Int? = null,
        deleteNodeCount: Int? = null,
        writeBatchCount: Int? = null,
        writeCallCount: Int? = null,
        completedDaoNodeCount: Int? = null,
        serializedChars: Long? = null,
        rowOffset: Int? = null,
        startedAtNanos: Long,
        errorType: String? = null,
    ) {
        val runtime = Runtime.getRuntime()
        Logging.log(
            TAG,
            buildConversationNodeDiagnosticMessage(
                operation = operation,
                phase = phase,
                conversationId = conversationId,
                pageCount = pageCount,
                loadedNodeCount = loadedNodeCount,
                totalNodeCount = totalNodeCount,
                totalMessageChars = totalMessageChars,
                plannedNodeCount = plannedNodeCount,
                deleteNodeCount = deleteNodeCount,
                writeBatchCount = writeBatchCount,
                writeCallCount = writeCallCount,
                completedDaoNodeCount = completedDaoNodeCount,
                serializedChars = serializedChars,
                rowOffset = rowOffset,
                elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000,
                heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                heapMaxBytes = runtime.maxMemory(),
                errorType = errorType,
            ),
        )
    }
}

internal fun lightConversationEntityToConversation(entity: LightConversationEntity): Conversation = Conversation(
    id = Uuid.parse(entity.id),
    assistantId = Uuid.parse(entity.assistantId),
    title = entity.title,
    isPinned = entity.isPinned,
    createAt = Instant.ofEpochMilli(entity.createAt),
    updateAt = Instant.ofEpochMilli(entity.updateAt),
    messageNodes = emptyList(),
    chatModelId = entity.chatModelId.ifEmpty { null }?.let { Uuid.parse(it) },
    folderId = entity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
)

internal suspend fun <T, R> mapAndConsumeInBatches(
    items: List<T>,
    batchSize: Int,
    transform: (index: Int, item: T) -> R,
    consume: suspend (List<R>) -> Unit,
) {
    require(batchSize > 0) { "batchSize must be positive" }
    var startIndex = 0
    while (startIndex < items.size) {
        val endIndex = minOf(startIndex + batchSize, items.size)
        val batch = ArrayList<R>(endIndex - startIndex)
        for (index in startIndex until endIndex) {
            batch += transform(index, items[index])
        }
        consume(batch)
        startIndex = endIndex
    }
}

internal data class PagedReadStats(
    val pageCount: Int,
)

internal suspend fun <T> consumePagedRowsWithSingleRowFallback(
    pageSize: Int,
    load: suspend (limit: Int, offset: Int) -> List<T>,
    isOversizedPage: (Throwable) -> Boolean,
    consume: suspend (List<T>) -> Unit,
    onSingleRowFailure: (offset: Int, error: Throwable) -> Unit,
): PagedReadStats {
    require(pageSize > 0) { "pageSize must be positive" }
    var offset = 0
    var pageCount = 0
    while (true) {
        val page = try {
            load(pageSize, offset)
        } catch (error: Throwable) {
            if (!isOversizedPage(error)) throw error
            val singleRow = try {
                load(1, offset)
            } catch (singleRowError: Throwable) {
                if (!isOversizedPage(singleRowError)) throw singleRowError
                onSingleRowFailure(offset, singleRowError)
                throw singleRowError
            }
            if (singleRow.isEmpty()) break
            consume(singleRow)
            pageCount++
            offset += singleRow.size
            continue
        }
        if (page.isEmpty()) break
        consume(page)
        pageCount++
        offset += page.size
    }
    return PagedReadStats(pageCount = pageCount)
}

internal fun countUnicodeCodePoints(value: String): Long =
    value.codePointCount(0, value.length).toLong()

internal fun shouldLogConversationNodeDiagnostics(
    nodeCount: Long,
    serializedChars: Long,
): Boolean = nodeCount >= 256L || serializedChars >= 4_000_000L

internal fun buildConversationNodeDiagnosticMessage(
    operation: String,
    phase: String,
    conversationId: String,
    pageCount: Int? = null,
    loadedNodeCount: Int? = null,
    totalNodeCount: Long? = null,
    totalMessageChars: Long? = null,
    plannedNodeCount: Int? = null,
    deleteNodeCount: Int? = null,
    writeBatchCount: Int? = null,
    writeCallCount: Int? = null,
    completedDaoNodeCount: Int? = null,
    serializedChars: Long? = null,
    rowOffset: Int? = null,
    elapsedMs: Long,
    heapUsedBytes: Long,
    heapMaxBytes: Long,
    errorType: String? = null,
): String = buildString {
    append("operation=").append(operation)
    append(" phase=").append(phase)
    append(" conversationId=").append(conversationId)
    pageCount?.let { append(" pageCount=").append(it) }
    loadedNodeCount?.let { append(" loadedNodeCount=").append(it) }
    totalNodeCount?.let { append(" totalNodeCount=").append(it) }
    totalMessageChars?.let { append(" totalMessageChars=").append(it) }
    plannedNodeCount?.let { append(" plannedNodeCount=").append(it) }
    deleteNodeCount?.let { append(" deleteNodeCount=").append(it) }
    writeBatchCount?.let { append(" writeBatchCount=").append(it) }
    writeCallCount?.let { append(" writeCallCount=").append(it) }
    completedDaoNodeCount?.let { append(" completedDaoNodeCount=").append(it) }
    serializedChars?.let { append(" serializedChars=").append(it) }
    rowOffset?.let { append(" rowOffset=").append(it) }
    append(" elapsedMs=").append(elapsedMs)
    append(" heapUsedBytes=").append(heapUsedBytes)
    append(" heapMaxBytes=").append(heapMaxBytes)
    errorType?.let { append(" errorType=").append(it) }
}

internal fun messageNodeToEntity(
    node: MessageNode,
    conversationId: String,
    nodeIndex: Int,
): MessageNodeEntity = MessageNodeEntity(
    id = node.id.toString(),
    conversationId = conversationId,
    nodeIndex = nodeIndex,
    messages = JsonInstant.encodeToString(node.messages),
    selectIndex = node.selectIndex,
    hidden = node.hidden,
    compressHiddenCount = node.compressHiddenCount,
)

internal fun messageNodeEntityToMessageNode(
    entity: MessageNodeEntity,
    isFavorite: Boolean,
): MessageNode = MessageNode(
    id = Uuid.parse(entity.id),
    messages = JsonInstant.decodeFromString<List<UIMessage>>(entity.messages),
    selectIndex = entity.selectIndex,
    hidden = entity.hidden,
    compressHiddenCount = entity.compressHiddenCount,
    isFavorite = isFavorite,
)

/**
 * Pure diff for [ConversationRepository.syncMessageNodes]: orphan ids to delete and nodes to upsert (order preserved).
 */
internal fun computeNodeSyncOps(
    existingIds: List<String>,
    newNodes: List<MessageNode>,
): Pair<List<String>, List<MessageNode>> {
    val newIds = newNodes.map { it.id.toString() }.toSet()
    val deleteIds = existingIds.filter { it !in newIds }
    return deleteIds to newNodes
}

/**
 * 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段
 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val chatModelId: String = "",
    val folderId: String = "",
)

data class ConversationPageResult(
    val items: List<Conversation>,
    val nextOffset: Int?,
)

data class ConversationFilter(
    val assistantId: Uuid? = null,
    val folderId: Uuid? = null,
    val unfiledOnly: Boolean = false,
    val searchText: String = "",
    val tagIds: Set<Uuid> = emptySet(),
) {
    init {
        require(folderId == null || !unfiledOnly) {
            "folderId and unfiledOnly cannot be used together"
        }
    }
}

internal fun buildConversationFilterQuery(filter: ConversationFilter): SimpleSQLiteQuery {
    val conditions = mutableListOf<String>()
    val arguments = mutableListOf<Any>()

    filter.assistantId?.let { assistantId ->
        conditions += "c.assistant_id = ?"
        arguments += assistantId.toString()
    }
    filter.folderId?.let { folderId ->
        conditions += "c.folder_id = ?"
        arguments += folderId.toString()
    }
    if (filter.unfiledOnly) {
        conditions += "c.folder_id = ''"
    }
    if (filter.searchText.isNotEmpty()) {
        conditions += "c.title LIKE '%' || ? || '%'"
        arguments += filter.searchText
    }

    val tagIds = filter.tagIds.map { it.toString() }.sorted()
    if (tagIds.isNotEmpty()) {
        val placeholders = List(tagIds.size) { "?" }.joinToString(", ")
        conditions +=
            "EXISTS (SELECT 1 FROM conversation_tag_cross_ref AS tag_filter " +
            "WHERE tag_filter.conversation_id = c.id AND tag_filter.tag_id IN ($placeholders))"
        arguments.addAll(tagIds)
    }

    val orderBy = "c.is_pinned DESC, c.update_at DESC, c.id DESC"
    val sql =
        """
        SELECT
            c.id,
            c.assistant_id AS assistantId,
            c.chat_model_id AS chatModelId,
            c.title,
            c.is_pinned AS isPinned,
            c.create_at AS createAt,
            c.update_at AS updateAt,
            c.folder_id AS folderId
        FROM ConversationEntity AS c
        ${if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"}
        ORDER BY $orderBy
        """.trimIndent()
    return SimpleSQLiteQuery(sql, arguments.toTypedArray())
}
