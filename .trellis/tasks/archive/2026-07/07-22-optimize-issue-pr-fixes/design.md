# Design

## Boundaries

- **Branch integration:** merge `origin/release/rikka-arsucar` into the current release branch before source edits, preserving the existing local commits and dirty skill files.
- **Draft runtime:** keep generation in `ChatService`; keep composer ownership and cancellation generation tokens in `ChatVM`; expose only eligibility/loading callbacks to Compose.
- **Draft UI:** derive eligibility from conversation availability, edit mode, and ASR state. Cancellation remains available while loading regardless of other states.
- **Memory tool:** preserve existing serialized fields for compatibility and add explicit aliases/metadata. Validate CAS parameters before persistence.
- **Trellis workflow:** make task-artifact requirements conditional without weakening the UI verification matrix for direct-edit tasks.

## Data And State Flow

```text
ChatPage edit/conversation state -> ChatInput eligibility
ASR status ----------------------> ChatInput mutual exclusion
ChatInput command -> ChatVM generation token -> ChatService stream
ChatService blank result -> ChatVM failure path -> original text restore + error feedback

Tool JSON -> parameter validation -> Repository transaction -> document metadata JSON
```

## Behavioral Decisions

- Editing a historical message always wins over reply-draft generation; the draft action is disabled rather than silently exiting edit mode.
- Empty draft completion is a generation failure. It restores the original text only when the generation token and current draft text still match, preserving user/ASR edits.
- ASR and draft generation are mutually exclusive at command availability. Existing generation-token checks remain the final race guard.
- Create-time `expected_revision` is rejected because no stored revision exists to compare; omitted revision retains last-write-wins behavior.
- Template responses retain `id` for compatibility and add `template_id`; they do not fabricate `document_id` or `revision`.
- Direct UI edits without a Trellis task record verification evidence in the delivery/check report instead of nonexistent PRD/design files.

## UI Verification Cases

- States: normal, editing, draft loading, draft success, empty-result failure, cancellation, ASR idle/listening/connecting/stopping/error.
- Content: empty composer, existing text, long text, localized labels, long update changelog and short changelog.
- Form factors: narrow phone, landscape where practical, scrollable update preview.
- Accessibility: draft generate/cancel descriptions remain non-empty; disabled actions expose correct state; update card remains scrollable and close action reachable.
- Interaction: generate, cancel, edit-message send, ASR start/stop, drag changelog without opening card, open update detail.

## Compatibility And Rollback

- No database migration or stored model format changes.
- Existing `MemoryTableTemplate.id` and document serialized fields remain present.
- The branch merge is an explicit integration step; source fixes are isolated in a later commit and can be reverted independently.
