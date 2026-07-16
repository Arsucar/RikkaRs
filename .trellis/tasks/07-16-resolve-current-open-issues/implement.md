# Implementation Plan

1. Activate and implement child #140 first because it changes the memory-page
   projection and creation flow used by the larger UI surface.
2. Run focused #140 tests and `:app:compileDebugKotlin`; review for accidental
   edits to the #136 working-tree files.
3. Implement child #141 list/editor restructuring and validation/resource
   updates.
4. Run focused #141 tests, compile, and lint. If a connected device is
   available, perform the repository-required `:app:installDebug` acceptance.
5. Run a parent cross-issue review: open-issue acceptance criteria, localization
   coverage, #136 regression boundary, and dirty-worktree ownership.
6. Prepare per-issue verification notes and only then commit/close issues under
   the repository's bilingual-comment rules.

Validation commands:

- `.\gradlew --no-daemon :app:compileDebugKotlin`
- `.\gradlew --no-daemon :app:testDebugUnitTest` (or the narrowest supported
  focused test task)
- `.\gradlew --no-daemon :app:lint`
- `adb devices` followed by `.\gradlew --no-daemon :app:installDebug` when a
  device is available.

Rollback points are child boundaries: revert #141 or #140 independently while
  preserving the pre-existing #136 changes.
