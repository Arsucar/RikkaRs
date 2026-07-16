# Design

- Keep `ConversationHook` serialization and repository APIs stable.
- Make the list item surface a single clickable container with a separate
  switch interaction and an overflow menu for delete/confirmation.
- Centralize editor validation in a small derived error model consumed by both
  fields and the save action.
- Render basic/runtime/rules/action sections with existing Material 3 section
  components; cap prompt height/lines while preserving editability.
- Introduce an action-config renderer/when boundary keyed by the sealed action
  type, with the current tag action as the only implementation.
- Add UI/state tests where existing test infrastructure permits, plus compile
  and lint checks for resource completeness.
