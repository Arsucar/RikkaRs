# Design

## Boundaries

- Tool behavior lives in `MemoryTableTools.kt` and is covered by `MemoryTableToolsTest.kt`.
- Memory table document UI lives in `AssistantMemoryPage.kt` and uses existing `AssistantDetailVM.upsertMemoryTableDocument` persistence.
- Markdown chip preview lives in `ChatMessage.kt` and `ChatMessageEditedFiles.kt`, reusing `buildMarkdownPreviewHtml` and existing WebView preview plumbing where possible.

## Memory Table Patch Semantics

`patch_rows` keeps top-level object merge semantics, but array fields are treated as table rows when both original and patch values are JSON arrays of objects. Rows are upserted by a stable row key:

- Prefer a `key` string field, matching the default facts schema and issue #49.
- If no `key` is present, preserve existing behavior for unsupported arrays by replacing that array field with the patch value.
- For keyed rows, matching patch rows replace old rows at their original position; new keys append after existing rows.

This makes the advertised merge behavior safe for default facts without inventing schema-wide business logic yet.

## Full-Screen Table Editor

The document editor opens full-screen for memory table documents. It derives table definitions from the selected template schema:

- `tables[].name` selects payload arrays by table name.
- `tables[].columns[].name` defines visible columns.
- Cell values are edited as strings, matching current `string`/`text` schema usage.

The editor keeps a single `payloadJson` draft. Table edits mutate the draft model and serialize it to JSON; JSON edits update the same draft string. If schema or payload parsing fails, the editor shows the parse error and leaves JSON mode available.

The existing small dialog is not kept as a separate document edit surface; document editing routes to the full-screen editor. Template schema editing remains the existing JSON dialog.

## Markdown Chip Preview

Add a shared markdown filename predicate or local helper and use it in both message chip paths:

- Read-only Markdown opens a rendered preview.
- Editable Markdown and non-Markdown text keep `FullScreenTextEditor`.
- Existing external-open behavior for unsupported documents is unchanged.

## Compatibility

- Stored payload format remains JSON.
- Repository and revision semantics remain unchanged.
- No migration is required.
