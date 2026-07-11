# Memory Table Row Semantics Design

## Scope

This task resolves #86, #90, and #92 for `memory_table_tool`.

## Design

- Row identity:
  - Prefer an explicit `row_key` tool argument when provided.
  - Otherwise infer from the document template schema:
    - a column with `"primaryKey": true`
    - fallback to a column named `"key"` for existing templates
  - If no row key can be inferred for an array patch/delete, return a readable
    error instead of replacing the whole array.

- Patch behavior:
  - `patch_rows` continues to accept `payload_json` as a top-level object.
  - For array values, merge rows by the resolved row key and preserve unrelated
    rows.
  - If a patch array cannot be keyed, fail explicitly.

- Delete behavior:
  - Add `delete_document` for whole-document deletion. It requires
    `confirm_document_id` equal to `document_id`.
  - Keep `delete_rows` as a guarded compatibility alias that returns an error
    directing callers to `delete_row` or `delete_document`; it must not delete
    the document with only `document_id`.
  - Add `delete_row` with `document_id`, `table`, and `row_key_value`; it uses
    the same row-key strategy as `patch_rows` and persists the edited payload.

## Compatibility

- Existing `key`-based templates keep working.
- New multi-column templates can opt in to non-`key` identity by setting
  `"primaryKey": true` on one column.
- Existing accidental `delete_rows` calls become safe failures instead of
  destructive document deletion.
