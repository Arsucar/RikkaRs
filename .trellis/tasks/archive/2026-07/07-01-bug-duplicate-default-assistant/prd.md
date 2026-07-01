# PRD: Fix duplicate default assistant for new users

## Issue

- #23: New users see two identically named "默认助手" / "Default Assistant" entries in the assistant list

## Problem

`PreferencesStore.kt` defines `DEFAULT_ASSISTANTS` with two entries, both having `name = ""`:

1. `0950e2dc-9bd5-4801-afa3-aa887aa36b4e` — blank slate (no systemPrompt), used as `DEFAULT_ASSISTANT_ID` for DB defaults
2. `3d47790c-c415-4b90-9388-751128adb0a0` — ships with a Pebble-template systemPrompt

UI fallback `assistant.name.ifBlank { R.string.assistant_page_default_assistant }` renders both as "Default Assistant" — cannot tell them apart.

Both are non-deletable (`id in DEFAULT_ASSISTANTS_IDS`), so user permanently sees two identical rows.

The merge logic (`ifEmpty { DEFAULT_ASSISTANTS }` + per-id injection) also ensures both are always present even for users who deleted one — they reappear on next settings load.

## Acceptance Criteria

- New users see at most one "Default Assistant" in the list, or two visibly distinct entries
- Recommended fix: **Option A — distinct non-empty names**
  - Keep entry 1 `name = ""` (fallback to "默认助手" / "Default Assistant" stays unchanged)
  - Set entry 2 `name` to a stable string (e.g. "Template" / "模板助手"), stored as a non-empty `name` field so UI shows it directly
  - Add string resource `assistant_page_template_assistant` (EN: "Template Assistant", zh: "模板助手") for display; but the stored `name` can be the EN string to avoid migration complexity
- Existing users: merge logic already ensures both entries exist by ID; second entry just gets a visible name on next settings load
- Both entries remain non-deletable (no change to `DEFAULT_ASSISTANTS_IDS` logic)
- Search in assistant page can find the second entry by its new name

## Constraints

- No DataStore migration needed (names are always overwritten by merge logic for default IDs)
- Must NOT break conversations referencing `3d47790c-...` as assistant_id
- Keep both UUIDs stable — they are referenced in DB conversations
- Minimal change: only modify `DEFAULT_ASSISTANTS[1].name` from `""` to a non-empty string

## Related Code

| File | Lines | Key |
|------|-------|-----|
| `PreferencesStore.kt` | ~DEFAULT_ASSISTANTS | Both entries with `name = ""` |
| `PreferencesStore.kt` | 302–307 | `ifEmpty { DEFAULT_ASSISTANTS }` + per-id merge |
| `AssistantPage.kt` | 416, 429, 509, 536 | Display + non-delete logic |
| `AssistantPicker.kt` | 74 | `ifEmpty` fallback |