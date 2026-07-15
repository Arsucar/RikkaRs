# Execution Plan

1. Refresh #132 tag repository contract and read generation, database, assistant-detail, and drawer specs.
2. Add serializable Hook configuration models and assistant-detail CRUD/order UI with compatibility tests.
3. Add logical-turn/run/execution entities, migration, DAO state transitions, uniqueness, aggregation, retention, and startup repair tests.
4. Integrate logical-turn creation/reuse and final-success gate across normal send, tools, approval continuation, regeneration, cancellation, and failure.
5. Implement dispatcher, direct provider executor, timeout/lease handling, strict parser, stable errors, and action registry.
6. Register add-conversation-tag action against #132 and test allowed/current/deleted tag and concurrency behavior.
7. Add right-drawer Hook history and `ChatVM` Room Flow, including empty/loading/error/deleted-reference/truncation/accessibility states.
8. Run race, process-death, privacy, migration, DAO, gate, parser, executor, UI-state, end-to-end, and generation regression tests.
9. Run static review, Kotlin compile, `git diff --check`, and device install/manual verification.
10. Commit/push, publish issue-specific Chinese and English comments, reread, close #133, and archive the child task.

## Risky Files / Rollback Points

- `ChatService.handleMessageComplete`, approval continuation, and regeneration paths.
- New Room migration immediately following #132's migration version.
- Timeout token invalidation and late-result conditional writes.
- Assistant JSON compatibility and sealed action serialization.
- Right drawer navigation state and existing `generationDoneFlow` consumers.
