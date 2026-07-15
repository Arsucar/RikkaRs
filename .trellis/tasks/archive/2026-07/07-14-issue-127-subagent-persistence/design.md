# Subagent Context Persistence Design

## Storage

- Add a Room entity keyed by context id with indexed scope/status/update fields, a monotonic revision, and versioned JSON snapshots for scope, messages and usage.
- Mapper owns serialization compatibility; one malformed row is logged and skipped.
- Explicit database migration creates the table and indices without destructive fallback.

## Hot/Cold Path

- Existing mutex-protected memory cache remains authoritative while running.
- Progress schedules coalesced, ordered IO snapshots per context; revision/conditional upsert prevents an older async snapshot overwriting newer state. Finish/failure performs a final awaited flush in cancellation-safe context. IO failure is logged and never fails generation.
- Host owns restore-once synchronization; spawn/acquire await it, while app startup may only prewarm. Cache miss and recovery load Room, apply TTL/capacity, downgrade stale RUNNING to INTERRUPTED, persist the downgrade, and reinsert into memory.

## Retention and Security

- Persisted rows obey the same 60-minute TTL, 16-context capacity, LRU order and scope mismatch checks as memory.
- Cleanup deletes expired and excess non-running rows. Scope lookup uses stable indexed columns plus decoded equality, not raw `scopeJson` equality. Restore never bypasses permissions.
