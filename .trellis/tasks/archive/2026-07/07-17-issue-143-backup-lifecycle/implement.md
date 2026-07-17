# Implementation Plan

1. Read relevant frontend/backend specs and exact BackupVM/tab/sync implementations.
2. Extract lifecycle-independent coordinator state machine with injectable suspend operations for testability.
3. Register coordinator in Koin and adapt BackupVM to start/cancel/observe operations.
4. Replace WebDAV/S3/local Composition-scope launches with state-driven calls; correct local export failure wording and success timing.
5. Preserve cancellation through sync/restore layers and make temporary-file cleanup unconditional.
6. Add coordinator JVM tests for UI-scope disposal equivalence, cancel, failure, success, deduplication and state re-observation.
7. Run targeted tests, compile and independent review before commit.

## Validation

- Targeted backup coordinator/VM JVM tests.
- `./gradlew --no-daemon :app:compileDebugKotlin`
- Device installation and manual tab/back navigation smoke check when an adb device is available.

## Risk / Rollback

- Do not keep `Uri`, toaster or Activity references in a singleton beyond the running operation.
- Avoid double notifications when UI re-collects terminal state.
- Restore cancellation may be unsafe mid-database import; do not expose user cancellation until the affected restore path has safe semantics.
