# Design

## Architecture and Boundaries

This parent task coordinates issue triage and sequencing. It should not directly
own broad source edits except for final integration and coordination. Each
independently verifiable package lives in a child task.

Child task boundaries:

- `07-09-fast-bugfix-batch`: small, well-located fixes with low architectural
  risk (#101, #72, #71, #76, #78).
- `07-09-chat-extension-ux-batch`: localized chat and extension UX improvements
  (#70, #75, #80).
- `07-09-preset-subagent-model-batch`: data/model and prompt composition work
  around presets and subagents (#73, #74, #79).
- `07-09-provider-configuration-batch`: provider tags and rate limiting (#77,
  #82).
- `07-09-memory-table-safety-foundation`: validation, atomicity, error handling,
  and default schema framing (#81, #83, #85, #87).
- `07-09-memory-table-row-semantics`: row identity, row delete, and document
  delete naming/semantics (#86, #90, #92).
- `07-09-memory-table-observability-scope`: list/read observability, maxRows
  wording, template lifecycle, and conversation-scope UI (#84, #88, #89, #91,
  #95).
- `07-09-memory-table-advanced-deferred`: final-phase advanced memory-table
  work (#93, #94, #96, #97, #98, #99, #100).
- `07-09-upstream-v241-merge-deferred`: upstream merge #68, intentionally
  separate.

## Dependency Shape

Recommended order:

1. Fast bugfix batch.
2. Chat/extension UX batch.
3. Preset/subagent model batch.
4. Provider configuration batch.
5. Memory-table safety foundation.
6. Memory-table row semantics.
7. Memory-table observability and scope.
8. Deferred memory-table advanced features.
9. Deferred upstream merge.

Memory-table dependency notes:

- Validation and readable errors should land before expanding write APIs.
- Row identity must be solved before row delete and batch-style operations.
- `delete_rows` document semantics should be clarified before adding more tool
  actions that include deletion.
- List/read observability helps debug conversation-scope work.
- Advanced injection, query, batch, import/export, and history should wait until
  the foundation is stable.

## Compatibility and Migration

- Existing user data must remain readable.
- Model/schema changes must provide backwards-compatible defaults where
  practical.
- If a child task requires migration, its design must document the migration
  path and rollback risk before implementation starts.

## Operational Notes

- Keep #68 out of this implementation batch because the upstream merge can
  rewrite or delete many files touched by local fixes.
- Existing uncommitted working-tree changes must be preserved and reviewed
  before touching overlapping files.
- GitHub issue comments/closures happen only after implementation and
  validation.
