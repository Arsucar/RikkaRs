# Assistant Archive Design

## State and Policy

- Add `Assistant.isArchived: Boolean = false`; existing DataStore JSON remains compatible.
- Central archive transition atomically updates assistants and selected assistant. Any assistant, including built-ins, may be archived unless it is the last active assistant.
- Archiving the selected assistant chooses the first remaining active assistant in Settings order. Restore does not switch selection. Clones are always active.
- Archive never invokes hard-delete cleanup. Archived assistants remain resolvable for historical conversations and editing, but cannot become the global current assistant through normal selection APIs.

## Read Boundaries

- Active UI pickers and move targets use active assistants only.
- Historical conversation rendering, backup, import, lookup-by-id and archived detail use all assistants.
- `getCurrentAssistant` accepts only an active selected id and falls back to the first active assistant.
- Selection APIs reject archived ids; opening a historical conversation may resolve its archived configuration without changing the active global selection.

## UI

- AssistantPage switches between Active and Archived modes. Active supports reorder/archive; Archived supports restore/detail/delete.
- Reorder operates on active slots while preserving archived relative positions in persisted Settings order.
- Last-active rejection and archive/restore success use localized feedback.

## Compatibility

- JSON default false handles upgrades; encode/decode and backup round-trip include the field.
- No Room migration. Deep links to archived assistants resolve safely without exposing them in pickers.
