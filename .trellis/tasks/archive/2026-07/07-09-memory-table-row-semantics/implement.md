# Memory Table Row Semantics Implementation Plan

- [x] Add tool parameters for `row_key`, `row_key_value`, `table`, and
      `confirm_document_id`.
- [x] Add `delete_document` and `delete_row` actions; guard `delete_rows`.
- [x] Resolve row key from explicit argument or template schema primary key.
- [x] Make `patch_rows` use resolved row key and error on ambiguous arrays.
- [x] Add tests for non-`key` primary key patch merge.
- [x] Add tests for `delete_document` confirmation and guarded `delete_rows`.
- [x] Add tests for `delete_row` success, not-found, and multi-table safety.
- [x] Run focused memory table tool tests.
- [x] Run `:app:compileDebugKotlin`.
- [x] Run `git diff --check`.
- [x] Update PRD acceptance criteria.

## Verification

- `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.tools.MemoryTableToolsTest"` passed.
- `.\gradlew --no-daemon :app:compileDebugKotlin` passed.
- `git diff --check` passed.
- `adb devices` reported no connected devices, so install verification was not run.
