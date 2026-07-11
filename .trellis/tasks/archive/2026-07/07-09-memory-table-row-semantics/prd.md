# Memory Table Row Semantics

## Goal

Clarify row identity and deletion semantics in `memory_table_tool` so models do
not accidentally replace or delete too much data.

## Requirements

- Fix #86: `patch_rows` must not hard-code row identity to `"key"` when the
  table schema defines another natural/business key.
- Fix #90: separate or clearly rename document deletion behavior so
  `delete_rows` is not mistaken for row deletion.
- Implement #92: add a real row-delete action that deletes rows by explicit
  table and selector/key semantics.

## Acceptance Criteria

- [x] Partial row patches preserve unrelated rows for non-`key` schemas.
- [x] Row identity strategy is documented and tested.
- [x] Whole-document deletion remains available only through a clearly named
      path.
- [x] Row deletion cannot delete an entire document by ambiguous naming.
- [x] Errors identify missing/ambiguous selectors.
