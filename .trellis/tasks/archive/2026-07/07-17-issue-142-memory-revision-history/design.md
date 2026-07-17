# Design

## Data Boundary

- Make `MemoryTableSnapshotDAO` a real production dependency of `MemoryTableRepository`.
- Reuse the existing immutable payload snapshots and rollback-by-upsert behavior; avoid inventing unsupported audit metadata.
- Keep template deletion snapshot cleanup inside the existing Room transaction.
- Add authorized history/rollback entry points that resolve the effective document before touching snapshots.

## UI Boundary

- Add a reusable revision history/detail surface reachable from existing memory document UI.
- List current document plus stored snapshots in descending revision order.
- Build a stable formatted JSON diff with existing `generateUnifiedDiff`/`DiffView`.
- Confirmation invokes rollback asynchronously and retains the page on error.

## Compatibility and Failure Semantics

- Existing databases remain valid; no migration is required for the UI/MVP because the existing table is already present.
- Snapshot DAO absence becomes a configuration/programming failure instead of an empty-history success.
- Invalid JSON snapshots are shown as raw text/diffable content rather than modifying data.
- Rollback only writes after target and current document authorization checks pass.

## Trade-offs

- The current schema cannot truthfully display operation/source/actor; the UI displays revision/time and computed payload diff only.
- Full CAS, soft delete, append-only audit and long-term history remain follow-up work.
