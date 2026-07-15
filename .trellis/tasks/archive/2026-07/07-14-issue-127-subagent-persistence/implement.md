# Subagent Context Persistence Implementation

1. Define entity/DAO/migration/schema and migration test.
2. Implement version-tolerant mappers and corrupted-row isolation tests.
3. Add persistence repository with per-context ordered/coalesced writes and failure isolation.
4. Integrate progress/final flush into host/cache lifecycle.
5. Implement startup and acquire fallback recovery with RUNNING downgrade and scope validation.
6. Add TTL/LRU/capacity/concurrency/restart tests and inspect database schema export.
