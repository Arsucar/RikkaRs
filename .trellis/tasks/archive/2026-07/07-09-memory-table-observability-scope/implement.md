# Memory Table Observability And Scope Implementation Plan

- [x] Add `memory_table_tool` read filtering by `scope`.
- [x] Reject new `scope=conversation` writes until #89 UI exists.
- [x] Add `update_template` and confirmed `delete_template` actions.
- [x] Add lifecycle/filter tests for memory table tools.
- [x] Rename injection document limit identifiers/text away from row wording.
- [x] Add/update injection transformer tests.
- [x] Add `memory_tool` list action and wire GenerationHandler callback.
- [x] Add/update memory tool tests.
- [x] Run focused JVM tests for memory tools/table/injection.
- [x] Run `:app:compileDebugKotlin`.
- [x] Run `git diff --check`.
- [x] Update PRD acceptance criteria.

## Verification

- `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.tools.MemoryTableToolsTest" --tests "me.rerere.rikkahub.data.ai.tools.MemoryToolsTest" --tests "me.rerere.rikkahub.data.ai.transformers.MemoryTableInjectionTransformerTest"` passed.
- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- `adb devices` reported no connected devices, so install verification was not run.

## Scope Note

#84 is handled with the short-term safety fallback: existing conversation-scope
documents can be listed by scope, but new conversation-scope writes are rejected
until the full #89 conversation-level UI is implemented.
