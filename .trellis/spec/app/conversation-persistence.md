# Conversation Persistence

## Scenario: Persist and load large conversation trees safely

### 1. Scope / Trigger

Apply this contract when changing `ConversationRepository`, `ConversationDAO`, `MessageNodeDAO`, recent-conversation tools, full conversation loading, fork/save/update, or index rebuild.

The goal is to reduce avoidable Room/JSON memory peaks without truncating user history or claiming an OOM root cause without heap/allocation evidence.

### 2. Signatures

```kotlin
suspend fun ConversationDAO.getRecentConversationsOfAssistant(
    assistantId: String,
    limit: Int,
): List<LightConversationEntity>

suspend fun MessageNodeDAO.getNodeIdsOfConversation(conversationId: String): List<String>

suspend fun MessageNodeDAO.getReadSummary(conversationId: String): MessageNodeReadSummary

suspend fun MessageNodeDAO.getNodesOfConversationPaged(
    conversationId: String,
    limit: Int,
    offset: Int,
): List<MessageNodeEntity>
```

Initial/fork saves encode and insert at most 64 `MessageNodeEntity` values per batch. Updates may upsert one node at a time, but the existing-side diff must use the ID-only projection.

### 3. Contracts

- Recent summaries select only `id`, `assistant_id`, `chat_model_id`, `title`, `is_pinned`, `create_at`, `update_at`, and `folder_id`. They must not read or decode `message_node.messages`.
- Full reads keep the entire logical node sequence ordered by `node_index`; pagination limits the temporary entity page, not the returned Conversation.
- Before a large full read, `getReadSummary` provides `COUNT(*)` and `SUM(LENGTH(messages))` without returning message bodies.
- A page-level `SQLiteBlobTooBigException` may retry the same offset with `LIMIT 1`. If that single row still fails, log metadata and rethrow; never return a Conversation missing that row.
- Initial/fork writes must not first build a full `List<MessageNodeEntity>` for the whole Conversation.
- Diagnostic logs may contain operation/phase, Conversation ID, page/batch/call counts, node/character counts, elapsed time, heap values, row offset, and error type. They must not contain message JSON, text, tool arguments/results, attachment paths, or model output.
- `totalMessageChars` and `serializedChars` use Unicode code-point counts. SQLite `LENGTH(TEXT)` and Kotlin-side `String.codePointCount` must remain semantically aligned.
- Counts named `completedDaoNodeCount` describe completed DAO calls inside the transaction; they do not claim the outer transaction has committed.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Recent summary query | Return lightweight rows; `messageNodes = emptyList()` |
| Existing-node diff | Query IDs only; preserve new node order |
| Page query is oversized but single row succeeds | Consume one row, advance by one, restore normal page size |
| Single-row query is oversized | Log `read_row_failed`, rethrow the same failure, return no partial Conversation |
| `IllegalStateException`, decode error, cancellation, or unrelated query failure | Log failure metadata where possible and rethrow; do not skip rows |
| Batch or sync write fails | Let the outer Room transaction roll back; do not report a committed row count |
| OOM stack lacks business frame/heap evidence | Describe changes as peak reduction/diagnostics, not a proven root-cause fix |

### 5. Good / Base / Bad Cases

- Good: 65 nodes load as two pages and retain order, selection state, hidden state, messages, and favorites.
- Base: an empty Conversation returns no nodes; a three-node initial save produces one batch.
- Good: a 64-row query fails, the same offset succeeds with one row, and the next query resumes at that offset plus one.
- Bad: incrementing the offset by 64 after a page failure silently drops healthy rows.
- Bad: skipping a single oversized row returns a partial model that a later sync can interpret as an orphan deletion.
- Bad: using `SELECT *` for recent summaries or update diffs materializes message JSON that the caller never uses.

### 6. Tests Required

- JVM: batch boundaries `0`, small, `64`, `65`, and multi-batch; preserve global indices/order.
- JVM: page failure retries `(pageSize, offset)` then `(1, sameOffset)` and restores page size after success.
- JVM: single-row oversized and non-oversized failures are rethrown; no later rows are returned.
- JVM: diagnostic formatter uses exact-string assertions to lock allowed fields and names.
- DAO/androidTest: recent projection preserves assistant isolation, pinned/update ordering, limit, and all lightweight fields.
- Repository/androidTest: malformed sentinel node JSON must not affect recent summaries, proving the path does not load nodes.
- Repository/androidTest: at least 65 nodes retain order and node state across a full load.
- DAO/androidTest: ID projection and read summary are scoped by Conversation; empty summary is `0/0`; non-BMP text matches Unicode code-point semantics.
- Final validation: `:app:compileDebugKotlin`, focused JVM tests, `:app:compileDebugAndroidTestKotlin`, and device install when available. Do not claim instrumentation execution if the device is unavailable.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val existing = messageNodeDAO.getNodesOfConversation(conversationId)
val entities = nodes.mapIndexed(::messageNodeToEntity)
messageNodeDAO.insertAll(entities)
```

This reads old message JSON only to obtain IDs and constructs every new encoded entity at once.

#### Correct

```kotlin
val existingIds = messageNodeDAO.getNodeIdsOfConversation(conversationId)
mapAndConsumeInBatches(nodes, batchSize = 64, transform = ::encodeNode) { batch ->
    messageNodeDAO.insertAll(batch)
}
```

For reads, retry an oversized page at the same offset with one row; rethrow if that one row still cannot be materialized.
