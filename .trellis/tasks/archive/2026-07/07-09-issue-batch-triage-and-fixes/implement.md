# Implementation Plan

## Checklist

- [x] Fill PRDs for all child tasks.
- [x] Start and complete `07-09-fast-bugfix-batch`.
- [x] Start and complete `07-09-chat-extension-ux-batch`.
- [x] Start and complete `07-09-preset-subagent-model-batch`.
- [x] Start and complete `07-09-provider-configuration-batch`.
- [x] Start and complete `07-09-memory-table-safety-foundation`.
- [x] Start and complete `07-09-memory-table-row-semantics`.
- [x] Start and complete `07-09-memory-table-observability-scope`.
- [x] Start `07-09-memory-table-advanced-deferred` only after the above are
      completed or explicitly deferred with reasons.
- [x] Keep `07-09-upstream-v241-merge-deferred` out of this batch unless the
      user explicitly changes the scope.
- [x] Map completed code changes back to issue numbers.
- [x] Run appropriate Gradle validation with `--no-daemon`.
- [x] If app-module functionality changes and a device is available, install
      Debug per repository rules unless the user asks for compile-only.
- [x] Comment/close GitHub issues only after verification.

## Completion Notes

- Implemented/fixed: #70, #71, #72, #73, #74, #75, #76, #77, #78, #79, #80,
  #81, #83, #84 fallback, #85, #86, #87, #88, #90, #91, #92, #95, #101.
- Deferred by design: #68 upstream merge; #89 full conversation memory UI;
  #93, #94, #96, #97, #98, #99, #100 advanced memory-table features.
- GitHub issues were not commented on or closed in this batch.
- Validation passed:
  - `.\gradlew --no-daemon :app:testDebugUnitTest`
  - `.\gradlew --no-daemon :app:compileDebugKotlin`
  - `git diff --check`
  - `adb devices` reported no connected devices, so install verification was
    not run.

## Dispatch Strategy

- Use sub-agents for non-trivial implementation and review/check work.
- Only one final check agent should run Gradle commands to avoid memory
  pressure.
- The parent task coordinates ordering, conflict resolution, and final summary.

## Validation

Baseline validation candidates:

- `.\gradlew --no-daemon :app:compileDebugKotlin` for Kotlin/UI changes.
- `.\gradlew --no-daemon test` when shared logic or repository/tool behavior is
  changed and JVM tests exist or are added.
- `.\gradlew --no-daemon lint` for broader UI/resource changes when time permits.
- `adb devices` then `.\gradlew --no-daemon :app:installDebug` after app-module
  feature changes if a device is connected.

## Rollback Points

- Commit or otherwise isolate each completed child task before moving to the
  next large child.
- Avoid mixing upstream merge #68 with local issue fixes.
- Preserve existing uncommitted changes in overlapping files.
