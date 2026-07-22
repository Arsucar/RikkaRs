# Implementation Plan

1. Add parameter-array process execution and canonical path validation to `workspace`, preserving existing Shell behavior; add focused runner/manager tests.
2. Add Git domain models, porcelain v2 parser, read-only repository, status/diff use cases, limits and error mapping; register dependencies.
3. Add request-generation-safe Git status/diff state to `ChatVM` and wire current `Assistant.workspaceId` through `ChatPage` into the right drawer.
4. Add the menu summary, grouped status list, refresh action, empty/error/CTA states and bounded diff view with stable keys and accessibility descriptions.
5. Add/translate Android resources through the locale workflow; audit hardcoded strings in changed UI.
6. Add parser/error/path/security unit tests and focused Compose UI tests for unbound, clean, grouped changes and binary/truncated diff states.
7. Run one combined Gradle verification for workspace tests, app resources, Kotlin compilation, app JVM tests and AndroidTest source compilation. Then follow `adb devices` and `:app:installDebug` acceptance flow.
8. Run Trellis quality check, inspect the final diff, commit implementation, archive the task, record the session, and close issue #174 only after posting distinct Chinese and English delivery comments with exact verification evidence.

## Risk And Rollback Points

- Runner context changes affect all workspace commands: keep Shell execution as the default branch and test exact PRoot argv construction.
- Porcelain paths are untrusted repository data: use NUL framing and limited splits; never parse line-oriented quoted output.
- Assistant switches can leak stale UI: generation guard and immediate loading reset are mandatory and unit-tested.
- Diff can expose private content on screen by design but must never leave UI state; audit logs, persistence and model request wiring before commit.

## Validation Commands

```powershell
.\gradlew --no-daemon :workspace:testDebugUnitTest :app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
adb devices
.\gradlew --no-daemon :app:installDebug
```

