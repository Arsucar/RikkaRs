# Technical Design

## Scope and task tree

The parent task coordinates two independently verifiable app-layer deliverables:

- Child #140: memory-table document-first list and creation flow.
- Child #141: assistant Hook list and editor information architecture.

Issue #136 is explicitly excluded from implementation. Its uncommitted local
changes must remain intact and are treated as a regression boundary.

## Cross-layer boundaries

### #140

`MemoryTableTemplate` / effective-template flows remain the source for choosing
a schema, while `MemoryTableDocument` becomes the only list projection shown in
the assistant memory page. The page opens a document route using the document
identifier. New-document creation is an explicit UI state: choose an effective
template or create a private/global template, persist it, then navigate only
after the repository/VM operation returns success. Document deletion calls the
document delete path only; template deletion remains available from the
template-management flow and is not inferred from a missing document.

The VM owns persistence ordering and exposes success/failure to the UI. Compose
owns dialog visibility and one-shot navigation events, using stable keys for
remembered state. Existing repository scope/actor semantics are reused rather
than adding a second persistence abstraction.

### #141

The Hook model and repository contracts remain unchanged except where an
explicit validation projection is needed. The list card becomes the single
navigation target; the enabled switch remains the only always-visible action,
while deletion moves to an overflow/confirmation path. The editor is split into
Material 3 sections (basic, runtime, rules, action), derives field-level
validation messages from one source of truth, bounds the prompt editor, and
renders an action-type boundary that can later host additional action configs.

All user-visible copy is provided through app string resources with Simplified
Chinese translations. Existing Hook execution and exactly-once persistence
contracts are not changed.

## Compatibility and rollout

- No database migration is expected for either issue.
- Existing routes and serialized Hook/MemoryTable models remain backward
  compatible.
- Changes are isolated to the app UI, AssistantDetailVM, and existing memory
  repository calls; rollback is a source revert without data migration.
- Tests must cover persistence ordering, list projections, validation, and
  action rendering without requiring connected-device instrumentation by
  default.
