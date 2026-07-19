# PRD: Subagent memory table injection (#164)

## Goal

Subagent profiles can select **memory table document instances** (shared payloads) to inject read-only into subagent context at spawn time, without granting write tools or expanding tool permissions.

## Requirements

### Configuration UI
- Entry: existing subagent profile editor (`AssistantSubagentProfilePage`).
- Multi-select injectable **document instances** available to parent assistant (`memoryTableDocuments` / assistant-effective documents).
- Show selected count + display names (template name for the document); allow deselect.
- States: empty, selected id invalid/deleted (missing chip, safe ignore on run).

### Data model
- `SubagentProfile.injectedMemoryTableDocumentIds: Set<String> = emptySet()`
- Id space = `MemoryTableDocument.id` (instances), **not** template ids.
- Default empty for old configs / missing field.

### Runtime
1. On spawn, resolve profile selected document ids.
2. Filter by parent assistant effective documents + conversation scope; apply `memoryTableIsolation` like ChatService (isolation → CONVERSATION scope only).
3. Collect schemas from templates referenced by resolved documents.
4. Inject via existing `MemoryTableInjectionTransformer` / budget settings.
5. Snapshot at first generate only; no re-inject on continuation.
6. Invalid selections skipped; no hard fail.
7. Empty selection → unchanged behavior.
8. Does **not** enable memory table tools for subagent.

### Compat
- Missing field deserializes to empty set.
- Unreleased WIP field `injectedMemoryTableTemplateIds` is replaced (no production migration).

## Acceptance

- [ ] Profile UI multi-select document instances save/reload.
- [ ] Spawn injects only selected accessible documents.
- [ ] Isolation matches ChatService.
- [ ] Empty selection = no injection.
- [ ] Missing ids skip without fail/leak.
- [ ] Old configs default no inject.
- [ ] Unit tests: persistence, filter by document id, isolation, invalid ids.

## Non-goals

- Auto-grant write tools.
- Cross-assistant memory leak.
- Changing main-agent injection rules beyond mirroring isolation.
