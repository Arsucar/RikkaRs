# Research: FTS search ambiguous column `update_at`

- **Query**: FTS search ambiguous column bug for PRD writing (Issue #26)
- **Scope**: internal
- **Date**: 2026-07-01

## Findings

### Root cause (factual)

`searchWithArchiveFilter` joins `message_fts` (alias `m`) with `conversationentity` (alias `c`). Both sides expose a column named `update_at`. `MessageSearchSort.orderBy` interpolates unqualified `update_at` into `ORDER BY`, which SQLite rejects as ambiguous when multiple joined tables define the same column name.

### `searchWithArchiveFilter` SQL (current)

**File:** `app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt` (lines 75–91)

**SELECT + FROM + WHERE + ORDER BY (as executed):**

```sql
SELECT m.node_id, m.message_id, m.conversation_id, m.title, m.update_at,
       simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
FROM message_fts m
INNER JOIN conversationentity c ON c.id = m.conversation_id
WHERE m.text MATCH jieba_query(?) AND c.is_archived = 0   -- or = 1 when archivedOnly
ORDER BY ${sort.orderBy}
LIMIT 50
```

`archiveClause`: `c.is_archived = 1` when `archivedOnly`, else `c.is_archived = 0`.

**ORDER BY values actually injected** (from `MessageSearchSort`):

| Enum | `orderBy` string | Expanded `ORDER BY` |
|------|------------------|---------------------|
| `RELEVANCE` | `rank, update_at DESC` | `ORDER BY rank, update_at DESC` |
| `NEWEST_FIRST` | `update_at DESC, rank` | `ORDER BY update_at DESC, rank` |
| `OLDEST_FIRST` | `update_at ASC, rank` | `ORDER BY update_at ASC, rank` |

`rank` is FTS5 relevance (not ambiguous). **`update_at` in ORDER BY is unqualified** → ambiguous with join present.

### `MessageSearchSort`

```kotlin
enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}
```

### Table schemas: both `update_at` columns

**`message_fts` (FTS5 virtual table)** — `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` (onCreate callback):

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
    text,
    node_id UNINDEXED,
    message_id UNINDEXED,
    conversation_id UNINDEXED,
    title UNINDEXED,
    update_at UNINDEXED,
    tokenize = 'simple'
)
```

Indexed value at insert time (`MessageFtsManager.indexConversation`):

```kotlin
conversation.updateAt.toEpochMilli().toString()  // stored in message_fts.update_at
```

**`conversationentity` (Room)** — `app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt`:

```kotlin
@ColumnInfo("update_at")
val updateAt: Long,
```

Room table name: `ConversationEntity` → SQLite table `conversationentity` (default Room naming).

### Which alias in ORDER BY: `m.update_at` vs `c.update_at`

| Alias | Source | Used in SELECT | Semantics |
|-------|--------|----------------|-----------|
| **`m.update_at`** | `message_fts` row | **Yes** — `m.update_at` selected; cursor `getLong(4)` → `MessageSearchResult.updateAt` | Snapshot of conversation `updateAt` **at FTS index time** (same value written for all messages in that index pass) |
| **`c.update_at`** | `conversationentity` | No | Live conversation last-update time |

**Product alignment for PRD:**

- UI field `MessageSearchResult.updateAt` is populated from **column index 4 = `m.update_at`** in the SELECT list.
- Sort modes named NEWEST/OLDEST first are documented in repo history as sorting search hits by **indexed `update_at`** (see archive research: `fix(search): add update_at as secondary sort for FTS search results`).
- **Fix for ambiguous SQL that preserves current SELECT mapping:** qualify ORDER BY as **`m.update_at`** (e.g. `rank, m.update_at DESC`, `m.update_at DESC, rank`, `m.update_at ASC, rank`).
- Use **`c.update_at`** only if product intent is to sort by **current** conversation activity rather than the FTS-stored timestamp (would change behavior vs. what is displayed unless SELECT also switches to `c.update_at`).

### Historical context

Archive feature design (`.trellis/tasks/archive/.../design.md`) added the `INNER JOIN conversationentity c` for `is_archived` filtering but left `ORDER BY ${sort.orderBy}` unchanged — introducing ambiguity that did not exist in the pre-join query (`FROM message_fts` only).

### Code patterns (citations)

```22:26:app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt
enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}
```

```83:90:app/src/main/java/me/rerere/rikkahub/data/db/fts/MessageFtsManager.kt
            """
            SELECT m.node_id, m.message_id, m.conversation_id, m.title, m.update_at,
                   simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts m
            INNER JOIN conversationentity c ON c.id = m.conversation_id
            WHERE m.text MATCH jieba_query(?) AND $archiveClause
            ORDER BY ${sort.orderBy}
            LIMIT 50
```

### Related specs / tasks

- `.trellis/tasks/07-01-bug-fts-ambiguous-col/prd.md` — Issue #26 (TBD)
- `.trellis/tasks/archive/2026-06/06-28-archive-conversation/.../design.md` — §1.4 FTS join + unqualified ORDER BY

## Caveats / Not Found

- No in-repo unit test reproducing SQLite “ambiguous column name: update_at” was located in this pass (instrumented tests use `MessageFtsManager` but may not assert SQL error paths).
- `message_fts.update_at` is stored as **string** in INSERT (`toEpochMilli().toString()`); ORDER BY still works numerically when values are numeric strings.