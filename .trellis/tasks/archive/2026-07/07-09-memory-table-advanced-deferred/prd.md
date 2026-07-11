# Deferred Memory Table Advanced Features

## Goal

Queue advanced memory-table capabilities for the final phase after foundation,
row semantics, and observability are stable.

## Requirements

- Defer #93: per-table injection gate from schema `injectPolicy`.
- Defer #94: trigger-send row-level injection filtering.
- Defer #96: revision history snapshots and rollback.
- Defer #97: query action for row/field filtering.
- Defer #98: batch/apply-ops write API.
- Defer #99: manual macros/placeholders for memory-table injection placement.
- Defer #100: JSON import/export bundle for templates and documents.
- Revisit exact ordering after the foundation tasks are complete.

## Acceptance Criteria

- [x] These issues are not implemented before prerequisite foundation tasks
      unless the user explicitly changes ordering.
- [x] Final-phase planning records dependencies on validation, row identity,
      observability, and template lifecycle behavior.
- [x] Each advanced issue receives a specific design before implementation.

## Final-Phase Decision

The prerequisite batches are complete, but these issues remain intentionally
split out for final-phase implementation. #93, #97, #98, #94, #99, #100, and
#96 should each become their own child task before code changes begin.
