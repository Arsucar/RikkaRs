# Research: config-only Switch (issue #245)

- **Query**: preset builtin config-only entries show fake Switch that doesn't inject; locate definition, injection skip, UI Switch, entry-count logic, tests, minimal change sites
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### 1. Where config-only is defined / detected

| File Path | Lines | Description |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptRegistry.kt` | 63–89, 104–190 | `BuiltinPromptDef.injectable` (default `false`) is the config-only flag; all 4 registered defs set `injectable = false` |
| `app/src/main/java/me/rerere/rikkahub/data/model/PresetEntry.kt` | 14–15, 54–73 | `PresetEntry.Builtin` documents current builtins as config-only; has shared `enabled` field |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PresetDetailPage.kt` | 602–604 | UI detection: `configOnly = entry is Builtin && BuiltinPromptRegistry[key]?.injectable != true` |
| `.trellis/spec/app/preset-entries.md` | 41–43 | Spec: current Builtins are config-only; overrides consumed by dedicated features |

**Keys (all config-only today):**

- `BuiltinPromptRegistry.KEY_REPLY_DRAFT` = `"reply_draft"` (L106, L141–149)
- `KEY_SUGGESTION` = `"suggestion"` (L107, L153–161)
- `KEY_MEMORY_TABLE_GUIDE` = `"memory_table_guide"` (L108, L167–175)
- `KEY_WORKSPACE_GUIDE` = `"workspace_guide"` (L109, L180–188)

**Contract test locking injectable=false:**

- `app/src/test/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptRegistryTest.kt` L43–56 `allBuiltinDefsAreConfigOnlyAndNotInjectable`

There is **no** shared `isConfigOnly()` helper; detection is inlined in `PresetDetailPage` only.

---

### 2. PromptInjectionTransformer config-only path

| File Path | Lines | Description |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt` | 194–243 | `resolvePresetEntry` |

**Code pattern (L212–216):**

```kotlin
is PresetEntry.Builtin -> {
    val def = BuiltinPromptRegistry[entry.builtinKey] ?: return null
    // config-only：… 预设路径对 injectable=false 的条目一律跳过
    if (!def.injectable) return null
    ...
}
```

- Does **not** check `entry.enabled` before the injectable gate (enabled would only matter if a future injectable Builtin existed).
- Unknown key → `return null`.
- Blank content after resolve → `return null` (L230).

**Related tests** (`PromptInjectionTransformerTest.kt`):

- L1487+ `preset builtin entry is config-only and must not be injected`
- L1519+ `preset builtin entry with override is still config-only and not injected`
- L1583+ `preset builtin dynamic template is config-only and macro is never leaked`

**Domain note (critical for UI change):** injection skip ≠ `enabled` is meaningless.

`resolveBuiltinOverride` **does** require `it.enabled`:

| File | Lines |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptOverrides.kt` | 34–52 (filter `it.enabled && it.builtinKey == builtinKey`) |
| `app/src/main/java/me/rerere/rikkahub/data/model/DraftContextConfig.kt` | 84–97 (`enabledReplyDraftEntries`) |

Consumers of overrides: `WorkspaceReminderTransformer`, `MemoryTableInjectionTransformer`, `ChatService` (suggestion / reply_draft), `DraftContextConfig` / `buildInputDraftPrompt` path.

So today Switch **does** gate dedicated-feature override consumption; it does **not** gate general chat injection (always skipped).

---

### 3. PresetEntryCard / Switch UI for Builtin

| File Path | Lines | Description |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PresetDetailPage.kt` | 586–736 | `private fun PresetEntryCard` |
| same | 730–733 | **Always** renders interactive `Switch(checked = entry.enabled, onCheckedChange = onToggle)` — no `configOnly` branch |
| same | 663–666 | config-only **Tag** only: `R.string.preset_detail_config_only_tag` |
| same | 796–803 | Edit sheet also has full `Switch` for `draft.enabled` (all entry types including Builtin) |
| same | 834–840 | Edit sheet info Tag: `preset_detail_config_only_desc` when `def?.injectable != true` |
| same | 196–204 | `toggleEntry` → `withEnabled` for all subtypes |
| same | 1010–1013 | `PresetEntry.withEnabled` |

**Call sites of PresetEntryCard:** L325–337 (Builtin section), L367+ (Custom), and Reference section similarly.

**Strings:**

- `app/src/main/res/values/strings.xml` L778–779
- `app/src/main/res/values-zh/strings.xml` L744–745
  - `preset_detail_config_only_tag` / `preset_detail_config_only_desc`

**No** dedicated `PresetEntryCard` file; card is private in `PresetDetailPage.kt`. **No** Compose UI tests for the card.

---

### 4. PresetEntryUi.kt entry count logic

| File Path | Lines | Description |
|---|---|---|
| `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/PresetEntryUi.kt` | 34–35 | `displayEntryCount()` = `entries.count { it.enabled }` when `hasEntries()` |
| same | 47–62 | `displayEntryNames()` = filter `it.enabled`, map names (Builtin → `builtinKey`) |

**Callers:**

| File | Lines |
|---|---|
| `ui/pages/extensions/PromptPage.kt` | 319–322 (`PresetCard` count + names) |
| `ui/components/ai/ExtensionContent.kt` | 70–74 (assistant extension list “entries count”) |

Both count/list **include enabled config-only Builtins** today.

---

### 5. Existing tests (PresetEntryUi / related)

| File | Relevant tests |
|---|---|
| `app/src/test/.../ui/pages/extensions/PresetEntryUiTest.kt` | L36–44 empty entries count 0; **L47–62** mixed Custom+Builtin+Reference expects **count 3** and names include `"suggestion"` |
| same | reorder / availableBuiltinKeys / switchBuiltinKey / hasEditorVariables — no config-only Switch coverage |
| `BuiltinPromptRegistryTest.kt` | all defs config-only |
| `BuiltinPromptOverridesTest.kt` | enabled/disabled override selection |
| `PromptInjectionTransformerTest.kt` | injection skip for builtins |

**Missing:** no test that `displayEntryCount` excludes config-only; no UI test for disabled Switch.

---

### 6. Related specs / PRD

- `.trellis/tasks/08-09-issue-245-config-only-switch/prd.md` — disable Switch (or static label); exclude from enabled count; do not change injection
- `.trellis/spec/app/preset-entries.md` — Builtins config-only; enabled nonblank overrides for consumers
- Archive design #182: config-only badge already planned; Switch left interactive

---

## Recommended minimal patch plan (line ranges)

**Do not change** injection (`PromptInjectionTransformer` L212–216) or registry `injectable` flags.

### A. Shared config-only helper (optional but small)

`PresetEntryUi.kt` (near top, after imports ~L11–26):

```kotlin
internal fun PresetEntry.isConfigOnlyBuiltin(): Boolean =
    this is PresetEntry.Builtin &&
        BuiltinPromptRegistry[builtinKey]?.injectable != true
```

Reuse in count + page UI to avoid drift.

### B. Exclude from enabled count / names — **required**

`PresetEntryUi.kt` **L34–35** and **L50–51**:

- Count/name only entries where `enabled && !isConfigOnlyBuiltin()` (or equivalent inline).
- Legacy `effectiveInjectionIds()` branch unchanged.

### C. Card Switch — **required**

`PresetDetailPage.kt` **L730–733** inside `PresetEntryCard`:

- If `configOnly` (already L603–604): either
  - `Switch(..., enabled = false)` (still shows state; may confuse), or
  - replace with static `Text`/`Tag` (e.g. reuse `preset_detail_config_only_tag` / new “by feature” label per PRD option a).

### D. Edit sheet Switch — **strongly related**

`PresetDetailPage.kt` **L796–803**: same config-only treatment so sheet cannot re-enable a “fake” toggle that card hides. If product still wants disable-override, keep Switch only for non-config-only.

### E. Tests — **required**

`PresetEntryUiTest.kt` **L47–62**:

- Today expects count `3` with Builtin `suggestion` enabled.
- After fix: count should be `2` (Custom + Reference only); names should **not** list config-only builtin keys (or document product choice if names still list them while count excludes — PRD says count only; names likely same filter for consistency).

Add focused test: only Custom/Reference (and future injectable Builtin) contribute to count when Builtins are config-only.

### F. Out of scope for #245 (per PRD)

- Domain injection / `resolveBuiltinOverride` semantics
- Deleting Builtin `enabled` field from model
- Compose instrumented tests for card (none exist)

---

## Caveats / Not Found

1. **PRD wording vs domain:** PRD says config-only `enabled` “不参与注入”. True for `PromptInjectionTransformer`. **False** for dedicated consumers: `resolveBuiltinOverride` and draft-context selection still filter on `enabled`. Disabling or removing Switch without another way to set `enabled=false` removes the only UI to stop override consumption short of deleting the entry.
2. **No shared `isConfigOnly` symbol** — only local `configOnly` in `PresetEntryCard`.
3. **No `PresetEntryCard` unit/UI tests** — only pure helpers in `PresetEntryUiTest`.
4. **All current Builtins are config-only** (`injectable` never true); acceptance “Custom / injectable Builtin unchanged” is forward-compatible via `injectable` check.
5. Task dir used: `.trellis/tasks/08-09-issue-245-config-only-switch/` (no active Trellis current task; path taken from research request).
