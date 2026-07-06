# Resolve open issues 49-51

## Goal

Resolve all currently open GitHub issues in the Rikka-arsucar fork:

- #49: `memory_table_tool` `patch_rows` must not silently replace existing array rows when it advertises merge semantics.
- #50: memory table documents need a full-screen table rendering and editing mode, while retaining raw JSON editing.
- #51: read-only Markdown files opened from message chips/document chips should render as Markdown, matching the workspace file viewer.

## Requirements

- `patch_rows` merges payload objects without dropping existing rows. Array-valued table fields such as `facts` are upserted by a stable row key, preferring `key` when present and otherwise replacing only when no row key is available.
- Add regression tests for `patch_rows` preserving existing rows and updating existing rows by key.
- Add a full-screen memory table document editor from the assistant memory page. It must parse the selected template schema, render table headers/rows from `payloadJson`, allow row add/delete and cell edits, and serialize changes back through the existing document upsert path.
- Keep a JSON source editing path available in the full-screen editor for advanced users and invalid/unsupported payloads.
- For message edited-file chips and document attachment chips, read-only Markdown opens as rendered Markdown preview. Editing still uses the existing text editor.
- Reuse existing Markdown preview utilities and existing persistence flows where practical.
- Do not change unrelated memory scope or repository semantics.

## Acceptance Criteria

- [x] #49: a `patch_rows` payload like `{"facts":[{"key":"验收","value":"正在实测语义"}]}` preserves pre-existing `facts` rows with different `key` values.
- [x] #49: a `patch_rows` payload with an existing row key updates that row instead of duplicating it.
- [x] #49: focused JVM tests for `MemoryTableToolsTest` cover the row merge behavior.
- [x] #50: assistant memory table documents expose a full-screen table editor with table and JSON modes.
- [x] #50: editing cells, adding rows, and deleting rows update `payloadJson` and save via `AssistantDetailVM.upsertMemoryTableDocument`.
- [x] #50: malformed schema/payload can still be edited in JSON mode and shows a clear error instead of crashing.
- [x] #51: `.md` read-only opens from edited-file chips and message document chips in rendered Markdown preview.
- [x] #51: non-Markdown text files and editable Markdown files continue to use `FullScreenTextEditor`.
- [x] Validation: run focused tests for memory table tools and compile the app module.

## Notes

- Source of truth: GitHub issues #49, #50, and #51 as of 2026-07-06.
- Validation completed: `.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.ai.tools.MemoryTableToolsTest" --tests "me.rerere.rikkahub.utils.TextFileUtilTest"`, `.\gradlew --no-daemon :app:compileDebugKotlin`, and `.\gradlew --no-daemon :app:installDebug`.
