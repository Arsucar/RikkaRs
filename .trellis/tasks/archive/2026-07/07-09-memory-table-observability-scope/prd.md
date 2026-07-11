# Memory Table Observability And Scope

## Goal

Improve memory-table inspection, template lifecycle operations, injection limit
wording, and conversation-scope visibility.

## Requirements

- Implement #88: allow listing memory-table documents and filtering by scope;
  add equivalent `memory_tool` list support where applicable.
- Address #84: conversation-scope writes must not be invisible to users; either
  provide the planned UI path or an explicitly documented fallback.
- Implement #89: add conversation-level memory-table entry/UI if chosen as the
  full solution for #84.
- Fix #91: align `maxRows` behavior and wording with actual row/document limits.
- Implement #95: add `update_template` and `delete_template` tool actions,
  reusing repository lifecycle behavior.

## Acceptance Criteria

- [x] Users or tools can enumerate memory-table documents with scope metadata.
- [x] Conversation-scope memory data has a visible/manageable path or is safely
      redirected according to the chosen strategy.
- [x] Injection limit names and user-facing text match implementation behavior.
- [x] Template update/delete actions validate inputs and return readable
      results.
- [x] Template deletion behavior around dependent documents is explicit.

## Decision

The full #89 right-side conversation memory UI is deferred. This task uses the
#84 short-term fallback: new `scope=conversation` writes are rejected with a
readable error, while existing conversation-scope documents remain discoverable
through `memory_table_tool` `read` plus `scope=conversation`.
