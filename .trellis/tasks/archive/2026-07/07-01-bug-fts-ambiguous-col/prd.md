# PRD: Fix FTS search ambiguous column `update_at`

## Issue

- #26: Searching chat history (message FTS) crashes with `SQLiteException: ambiguous column name: update_at`

## Problem

`searchWithArchiveFilter` in `MessageFtsManager.kt` joins `message_fts` (alias `m`) with `conversationentity` (alias `c`) via `INNER JOIN`. Both tables have an `update_at` column:

- `message_fts.update_at` — UNINDEXED column, stores `conversation.updateAt.toEpochMilli().toString()` at FTS index time
- `conversationentity.update_at` — Room entity column, live conversation update time

`MessageSearchSort.orderBy` uses unqualified `update_at` in ORDER BY:

```sql
ORDER BY rank, update_at DESC       -- RELEVANCE
ORDER BY update_at DESC, rank       -- NEWEST_FIRST
ORDER BY update_at ASC, rank        -- OLDEST_FIRST
```

SQLite cannot resolve which table's `update_at` when both are in scope → crash.

The SELECT clause already uses `m.update_at` and cursor reads column index 4 → `MessageSearchResult.updateAt`. ORDER BY should match the SELECT semantics (FTS-indexed timestamp).

## Acceptance Criteria

- `MessageSearchSort.orderBy` values qualify `update_at` with alias `m.`:
  - `RELEVANCE`: `"rank, m.update_at DESC"`
  - `NEWEST_FIRST`: `"m.update_at DESC, rank"`
  - `OLDEST_FIRST`: `"m.update_at ASC, rank"`
- FTS search no longer crashes when archive filter JOIN is active
- Search results sort by FTS-indexed message timestamp (same as before fix, just qualified)
- No behavior change for search without archive filter (single-table query still works with aliased column)

## Constraints

- Single enum change + line in query; no schema migration
- Keep using `m.update_at` (FTS index time) to match SELECT + `MessageSearchResult.updateAt`
- Using `c.update_at` would change behavior (sort by live conversation time) — only do this with explicit product decision

## Related Code

| File | Lines | Key |
|------|-------|-----|
| `MessageFtsManager.kt` | 22–26 | `MessageSearchSort` enum |
| `MessageFtsManager.kt` | 75–91 | `searchWithArchiveFilter` SQL |
| `DataSourceModule.kt` | 80–88 | `message_fts` DDL with `update_at UNINDEXED` |
| `ConversationEntity.kt` | 19–20 | `@ColumnInfo("update_at")` |