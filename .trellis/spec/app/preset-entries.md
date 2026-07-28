# Preset Entries Contract

## 1. Scope / Trigger

Use this contract when changing editable preset entries, legacy mode-injection migration, Builtin prompt overrides,
or any UI that creates, edits, orders, counts, or deletes preset entries.

Data flow:

```
DataStore JSON -> Settings normalization/migration -> Preset.entries
    -> runtime resolver -> Compose projection -> DataStore
```

## 2. Signatures

- `Preset.entries: List<PresetEntry>`
- `Preset.entriesVersion: Int`; `PRESET_ENTRIES_VERSION = 1`
- `Preset.migratedWithEntries(modeInjections): Preset`
- `Settings.withModeInjectionsPreservingPresetSnapshots(updatedModeInjections): Settings`
- `SettingsStore.updatePreset(presetId, transform): Boolean`
- `MutablePreferences.writePresetUpdate(presetId, fallbackPresets, fallbackModeInjections, transform): Boolean`
- `resolveBuiltinOverride(assistant, presets, builtinKey): String?`
- `availableBuiltinKeys(entries, editingEntryId = null): List<String>`

## 3. Contracts

- `entriesVersion >= PRESET_ENTRIES_VERSION` is the source of truth for the new model. An empty `entries` list is valid
  and must not fall back to legacy IDs.
- Legacy migration snapshots referenced mode injections before invalid IDs are filtered. The snapshot preserves enabled
  state and legacy priority, then clears the old ID fields.
- Migration persistence runs in one `DataStore.edit`, rereads current persisted values, and writes only `PRESETS` plus
  the migration marker. A stale `Settings` flow snapshot must not overwrite unrelated concurrent changes.
- Every production path that replaces or deletes `Settings.modeInjections` must call
  `withModeInjectionsPreservingPresetSnapshots` first.
- Preset detail actions submit relative `(Preset) -> Preset` mutations, never a captured full `Settings/Preset` snapshot.
  The page keeps optimistic session state; the VM serializes mutations; the store rereads the latest persisted
  `PRESETS` in one DataStore edit and writes only the target preset.
- Current Builtins are config-only: `PromptInjectionTransformer` skips them. Their enabled nonblank overrides are read
  by reply-draft, suggestion, workspace, and memory-table consumers. Runtime macro/data expansion stays with the
  consumer.
- Runtime deduplication uses source identity, not rendered entry identity: Custom uses `entry.id`; Reference uses its
  target `modeInjectionId`. A direct binding and a Reference to the same global injection therefore inject once,
  while distinct Custom entries remain independent even when their content is equal.
- The Workspace registry default and the no-override runtime prompt share `buildWorkspaceGuidePrompt`. Dynamic macro
  resolution scans the original template once; macro-shaped text inside runtime values remains literal.
- `BuiltinPromptDef.dynamic` describes registry-owned double-brace macro parsing only. `supportedVariables` describes
  all editor-insertable variables supported by the dedicated consumer, including single-brace static templates.
- A preset may contain at most one Builtin entry per key. Create excludes all used keys; edit retains the current key
  while excluding keys owned by sibling entries.
- Reordering is allowed only within the same entry subtype. Explicitly reordering migrated Custom entries clears
  `legacyPriority`; entries from other groups retain their position and state.
- Every gesture reorder surface provides keyboard/TalkBack move-up and move-down commands with boundary disabling.
  A drag handle must not expose an empty click action or describe itself as a move direction.
- Destructive entry deletion requires a confirmation naming the target. Cancel or back must not mutate the preset.

## 4. Validation & Error Matrix

- New-model preset with empty entries -> keep empty; never revive legacy IDs.
- Legacy ID no longer present globally -> snapshot before deletion when content still exists; otherwise skip the orphan.
- Disabled global injection during migration -> migrated entry remains disabled.
- Builtin override is null, blank, disabled, or outside assistant presets -> return null and use the consumer fallback.
- Builtin key already owned by a sibling -> exclude it from create/edit options.
- Reference target missing -> show stale state and block confirmation until the user selects a valid target.
- Drag target belongs to another subtype -> no-op.
- Stale fallback plus newer persisted preset -> apply the mutation to the persisted preset and preserve sibling edits.
- Static Builtin with nonempty `supportedVariables` -> show variable chips even when `dynamic == false`.
- Runtime macro value contains another supported macro token -> insert it literally; do not recursively resolve it.

## 5. Good / Base / Bad Cases

- Good: a legacy preset is atomically snapshotted, remains stable after its global injection is deleted, and keeps
  order.
- Base: a new empty preset persists `entriesVersion = 1` and displays zero entries.
- Bad: filtering invalid global IDs before migration loses content permanently.
- Bad: enforcing Builtin uniqueness only in the add menu allows edit to create duplicate keys.
- Bad: a config-only Builtin is appended to the general system prompt or its override is never consumed.
- Bad: a delayed delete writes `settings.copy(presets = oldSnapshot)` and restores sibling entries.
- Bad: UI requires `dynamic=true` before showing variables used by a dedicated single-brace consumer.

## 6. Tests Required

- Serialization: all entry subtypes, missing new optional fields, and empty versioned entries round-trip.
- Migration: enabled inheritance, priority preservation, first default-preset persistence, later imported legacy data,
  deletion-before-snapshot protection, idempotency, and concurrent-field preservation.
- Runtime: Builtins skipped by general injection; direct/preset/lorebook ordering and deduplication; dedicated override
  fallback, Reference/direct source-identity deduplication, workspace default equivalence, non-recursive macros, and
  memory data block with and without its macro.
- UI helpers: entries-aware counts/names, stale Reference validation, create/edit Builtin uniqueness, same-group
  reorder, accessible move boundaries, cross-group no-op, and clearing `legacyPriority` after explicit Custom reorder.
- Latest-value persistence: stale fallback cannot overwrite newer sibling content; sequential edit/delete transforms
  compose from the latest stored target and leave unrelated keys/presets unchanged.
- Variable metadata: assert exact ordered lists for all Builtins and prove `dynamic=false` entries expose editor chips.
- Resources/compile: process Debug resources, compile production Kotlin, run full app JVM tests, and compile
  AndroidTest Kotlin.

## 7. Wrong vs Correct

### Wrong

```kotlin
settings.copy(modeInjections = updated)
Select(options = BuiltinPromptRegistry.all.keys.toList(), selectedOption = entry.builtinKey)
```

This can delete the only source for a legacy snapshot and lets edit create a duplicate Builtin key.

### Correct

```kotlin
settingsStore.updatePreset(presetId) { latest ->
    latest.copy(entries = latest.entries.filterNot { it.id == deleteTargetId })
}
Select(
    options = availableBuiltinKeys(preset.entries, entry.id),
    selectedOption = entry.builtinKey,
)
```
