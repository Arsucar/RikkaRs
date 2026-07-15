# Execution Plan

1. Read relevant app data/UI specs and inventory migration, repository, drawer, settings, fork, and backup tests.
2. Implement entities, schema 38 migration, DAO transactions, domain models/errors, repository, and DI.
3. Add unit/migration tests for normalization, limits, CRUD/merge, foreign keys, cascade, idempotency, and concurrency boundaries.
4. Implement composable paging filters and one observable tag relation data source; test OR/AND semantics, stable paging, and invalidation.
5. Integrate fork transaction and post-restore foreign-key integrity reporting; test rollback and backup/restore cases.
6. Add settings management UI, drawer filter state, title chips, and long-press relation management with localized accessible states.
7. Run static review, targeted tests, Kotlin compilation, `git diff --check`, and device install/manual verification.
8. Commit/push, publish issue-specific Chinese and English comments, reread, close #132, and archive the child task.

## Risky Files / Rollback Points

- `AppDatabase.kt`, migration registration, and schema files.
- Existing hard-coded `ConversationDAO` paging queries.
- `ChatService.forkConversationAtMessage` transaction boundary.
- Database restore/reopen flow.
- `ConversationList.kt` title layout and drawer state restoration.
