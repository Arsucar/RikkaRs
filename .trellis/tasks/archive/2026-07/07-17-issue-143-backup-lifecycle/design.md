# Design

## Lifecycle Boundary

- Introduce a Koin singleton backup task coordinator using the existing `AppScope`.
- Coordinator owns Jobs and persistent `StateFlow` operation states; it must not hold Composable callbacks or Activity instances.
- `BackupVM` exposes coordinator state and delegates start/cancel operations. Tabs collect state and no longer launch business work in `rememberCoroutineScope`.

## Operation Model

- Represent operation type and state explicitly: Idle, Running, Success, Failed, Cancelled.
- Serialize or key-deduplicate conflicting operations. Preserve item IDs for restore operations and target URI/import type for local tasks only as long as needed.
- Publish root-level notice events as user feedback, while retaining terminal state in StateFlow for reliable re-entry.

## IO and Cancellation

- Catch `CancellationException` separately, perform cleanup, publish Cancelled when user initiated, then preserve cancellation semantics.
- Run file/network work on IO dispatchers.
- Move the full local export transaction (generate zip, open/write/close SAF stream, cleanup, record success time) behind the lifecycle-independent task boundary.
- Use `try/finally` for local temp files; treat null input/output streams as errors.

## Compatibility

- Keep archive format, WebDAV/S3 naming conventions and settings storage compatible, adding uniqueness only if collision prevention is required.
- AppScope does not survive process death; WorkManager/foreground service is explicitly deferred.
- Existing restore behavior is retained, but cancellation wrappers must rethrow `CancellationException`.
