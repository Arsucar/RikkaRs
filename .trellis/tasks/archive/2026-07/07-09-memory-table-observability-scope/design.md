# Memory Table Observability And Scope Design

## Scope

This task resolves #88, #84 short-term safety, #91, and #95. Full conversation
memory UI (#89) is not implemented here; the safety fallback from #84 is used.

## Design

- Document observability:
  - `memory_table_tool` `read` can list all effective documents and optionally
    filter by `scope`.
  - Returned document objects already include id/template/scope/revision/payload,
    which is sufficient for tool-side inspection.

- Conversation scope:
  - Until the full #89 UI exists, `memory_table_tool` rejects new
    `scope=conversation` writes with a readable error.
  - Existing conversation-scope documents remain readable through `read` and
    scope filtering for diagnosis.

- Injection wording:
  - Rename row-limit identifiers that actually limit documents to
    `maxDocuments`.
  - Prompt text should say `documents=N/M` and omissions by document limit.

- Template lifecycle:
  - Add `update_template` and `delete_template`.
  - `update_template` requires `template_id`, loads the existing template, and
    upserts a copy with optional name/description/schema updates.
  - `delete_template` requires `template_id` and matching `confirm_template_id`;
    it uses repository lifecycle behavior, which cascades dependent documents.

- Legacy memory observability:
  - `memory_tool` gains `list` and returns the effective memory records provided
    by the caller.

## Compatibility

- Existing reads without filters keep returning effective memory-table docs.
- Existing template delete repository behavior is preserved, but tool deletion
  requires explicit confirmation.
- Existing `maxRows` constructor parameter is renamed internally for clarity;
  tests and local call sites are updated together.
