# Assistant Archive Implementation

1. Add model field and pure archive/restore/reorder policy with unit tests.
2. Add atomic SettingsStore operations and active-only current selection semantics.
3. Wire AssistantVM without reusing hard-delete paths; make clone active.
4. Add Active/Archived UI, actions, empty/error feedback and localized strings.
5. Filter all normal pickers/move targets; keep historical and backup lookups unfiltered.
6. Reject archived selection through web/settings entry points.
7. Verify JSON compatibility, fallback, no-delete invariant, UI filtering and restart persistence.
