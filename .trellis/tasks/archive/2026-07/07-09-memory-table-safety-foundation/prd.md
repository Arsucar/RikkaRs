# Memory Table Safety Foundation

## Goal

Make memory-table reads/writes safer before adding more advanced capabilities.

## Requirements

- Implement #81: update default memory-table template and wording so it models
  multi-column structured tables instead of only key/value facts.
- Fix #83: validate template `schemaJson` before writing and show readable
  errors for invalid schema JSON.
- Fix #85: validate `payloadJson` before destructive writes and prevent invalid
  or truncated payload from replacing good data.
- Fix #87: wrap memory-table tool errors in user-readable messages rather than
  raw kotlinx/stack trace output.

## Acceptance Criteria

- [x] Invalid template schema is rejected before persistence.
- [x] Invalid payload writes do not corrupt or replace the previous valid
      document.
- [x] Successful writes preserve expected revision behavior.
- [x] Tool/UI errors identify the invalid field/action in understandable text.
- [x] Default schema/example demonstrates multi-column structure.
