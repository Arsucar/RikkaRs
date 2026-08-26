# Research: Issue #302 — current HEAD vs PR #303 vs design.md

- **Query**: What of #302 is already on `release/rikka-arsucar`, what is only on `feat/tavern-card-bindings-302` (PR #303), and what still needs to be implemented on current HEAD?
- **Scope**: mixed (internal code + git/PR + character-card-spec-v2 + SillyTavern source)
- **Date**: 2026-08-26

## Git / PR facts

| Item | Value |
|---|---|
| Current HEAD | `f8c1bc0087f5983598ca4c1b28f62f8d89347080` on `release/rikka-arsucar` |
| PR #303 head | `538b8d4fb09a2f016f681249ffdda222f436d19f` on `origin/feat/tavern-card-bindings-302` |
| PR base | `e313e0f90f2d3230c63939fd9e461e13de252f87` |
| `mergeable_state` | `dirty` |
| PR commits in HEAD? | **No.** `7e026d20` / `082b3600` / `538b8d4f` are **not** ancestors of HEAD |
| Commits on HEAD not in PR | 70 |
| Parallel HEAD implementation | `0a09549fd7a47338f061507784a151a96be1ae4b` (`feat(#302): tavern card world book import`, 2026-08-16) — still the tip of importer/serializer/page for these files; later commits did not retouch them |

PR #303 commits (not in HEAD):

1. `7e026d20` — `feat(#302): import character_book lorebook bindings from tavern cards`
2. `082b3600` — `fix(#302): pass AssistantVM to AssistantCreationSheet for lorebook writeback`
3. `538b8d4f` — `fix(#302): drop divider and ListItem fill from binding confirm dialog`

Issue: https://github.com/Arsucar/RikkaRs/issues/302  
PR: https://github.com/Arsucar/RikkaRs/pull/303

## Present vs missing on current HEAD

### Present on HEAD (`0a09549f` + current files)

A **parallel** #302 implementation already exists. It is **not** a cherry-pick of PR #303.

| Piece | HEAD location | Behavior |
|---|---|---|
| `PendingImport` | `AssistantImporter.kt` L184-187 | `data class PendingImport(assistant, lorebooks)` — matches `design.md` name |
| Dialog | `AssistantImporter.kt` L155-181 | `RikkaConfirmDialog` (not `BindingConfirmDialog`) |
| Detection | `AssistantImporter.kt` L289-304 `detectWorldBook` | Probes `data.character_book`, then `data.extensions.world`, then `data.extensions.character_book` |
| Mapper used | `LorebookSerializer.tryImportSillyTavern` | Lifted `internal` in `ExportSerializer.kt` L314 |
| Types lifted | `SillyTavernLorebook` / `SillyTavernEntry` | `internal` at `ExportSerializer.kt` L357-374 |
| `onUpdate` signature | `AssistantImporter.kt` L55; `AssistantPage.kt` L421 | `(Assistant, List<Lorebook>) -> Unit` |
| Persistence | `AssistantPage.kt` L421-438 | If lorebooks non-empty: `vm.updateSettings { lorebooks += ... }` then `update(assistant.copy(lorebookIds=...)); state.confirm()` |
| `AssistantCreationSheet(vm)` | `AssistantPage.kt` L284, L377-379 | VM passed in (same structural change as PR) |
| Strings (en/zh) | `values/strings.xml` L222-225; `values-zh/strings.xml` L220-223 | `assistant_importer_detected_bindings` / `world_book_count` / `import_all` / `skip_bindings` |
| Changelog | `CHANGELOG.md` v2.3.53 | Documents #302 as shipped |

`createState` still persists the assistant via `vm.addAssistant(it)` (`AssistantPage.kt` L99-101 → `UseEditState.kt` L34-39).

### Missing on HEAD (only on PR branch, or never implemented)

| Piece | PR location | HEAD |
|---|---|---|
| `tryImportCharacterBook` | `ExportSerializer.kt` (PR, after `SillyTavernEntry`) | **Absent** |
| `CharacterBook` / `CharacterBookEntry` | PR private data classes | **Absent** |
| `mapCharacterBookPosition` | PR private | **Absent** |
| `extractLorebooksFromCard` | PR `AssistantImporter.kt` | **Absent** (HEAD has `detectWorldBook` instead) |
| `TavernCardImportResult` | PR | **Absent** (HEAD uses `PendingImport`) |
| `BindingConfirmDialog` | PR private composable | **Absent** (HEAD uses `RikkaConfirmDialog`) |
| `addAssistantWithLorebooks` | PR `AssistantVM.kt` | **Absent** |
| Strings `assistant_importer_binding_*` | PR `values` + `values-zh` | **Absent** (HEAD uses `detected_bindings` / `world_book_count` / `import_all` / `skip_bindings`) |
| Unit tests for character book | PR body: explicitly **not added** | **Absent** (no `*CharacterBook*` / importer tests) |
| Preset-from-card import | Issue AC2 | **Out of scope in `prd.md`**; neither HEAD nor PR implements it |

### Mapper mismatch (HEAD detection does not parse canonical cards)

Spec `CharacterBook.entries` is an **array** with fields `keys`, `enabled`, `insertion_order`, `position: 'before_char' \| 'after_char'`  
([character-card-spec-v2](https://github.com/malfoyslastname/character-card-spec-v2/blob/master/spec_v2.md)).

HEAD `SillyTavernLorebook` expects:

```kotlin
internal data class SillyTavernLorebook(
    val entries: Map<String, SillyTavernEntry> = emptyMap(),  // object, not array
)
internal data class SillyTavernEntry(
    val key: List<String> = emptyList(),   // not `keys`
    val disable: Boolean = false,          // not `enabled`
    val order: Int = 100,                  // not `insertion_order`
    val position: Int = 0,                 // not string
)
```

`LorebookSerializer.tryImportSillyTavern` wraps decode in `runCatching { ... }.getOrNull()`. Canonical `data.character_book` therefore returns **null**. `detectWorldBook` then returns `emptyList()`, so **no dialog** and **no lorebook write**. PNG/JSON paths still import the assistant body (AC5/AC6-like for the no-binding path).

Standalone world-info JSON (`entries` as a uid-keyed map with `key`/`disable`/`order`/`position: Int`) is what `tryImportSillyTavern` is for. That is **not** the v2/v3 card `character_book` shape. SillyTavern converts world-info → character_book via `convertWorldInfoToCharacterBook` (`src/endpoints/characters.js`): `keys: entry.key`, array of entries, `insertion_order`, string `position`.

### `data.extensions.world` type

SillyTavern stores `data.extensions.world` as a **string** (world book **filename**), not an embedded object:

```js
_.set(char, 'data.extensions.world', data.world || '');
```

HEAD `detectWorldBook` does `extensions?.get("world")?.jsonObject`. kotlinx `JsonElement.jsonObject` **throws** if the element is a `JsonPrimitive`. That throw is caught in `importAssistantFromUri` (`AssistantImporter.kt` L347-353) → Error toast, **assistant not imported**.

Embedded book content on ST cards is `data.character_book` (object). `extensions.world` is a name/reference, not entries.

### Persistence on HEAD vs PR

HEAD (`AssistantPage.kt` L422-438):

1. `vm.updateSettings { settings.copy(lorebooks = settings.lorebooks + lorebooks, assistants = settings.assistants) }` — lorebooks only; `newLorebookIds` is unused.
2. `update(importedAssistant.copy(lorebookIds = ...)); state.confirm()` → `vm.addAssistant` (`AssistantVM.kt` L45-53) which does `settingsStore.update(settings.value.copy(assistants = ...))` — **snapshot write**, not the transform overload (`AssistantVM.kt` L33-43, #267).

Two sequential updates. Lorebooks and assistant are not one `settingsStore.update`.

PR: `addAssistantWithLorebooks` writes `assistants + lorebooks` in **one** `settingsStore.update`, then `state.dismiss()`. Also snapshot-based (`settings.value`), not transform.

`EditState.confirm` (`UseEditState.kt` L34-39) calls `onUpdate(currentState)` then closes the sheet. HEAD always `confirm()` after import. PR `dismiss()` when lorebooks non-empty (skips `addAssistant` because VM already wrote the assistant).

### Dialog dismiss semantics

Issue / `design.md` / `implement.md`: cancel / back / outside tap ≡ **skip bindings**, still import character body.

| Path | Confirm | Skip button | `onDismissRequest` (back / outside) |
|---|---|---|---|
| HEAD `RikkaConfirmDialog` | import assistant + lorebooks | `onDismiss` → `onImport(assistant, emptyList())` | same as skip (ConfirmDialog.kt L23 `onDismissRequest = onDismiss`) |
| PR `BindingConfirmDialog` | `onImport(assistant, lorebooks)` | `onImport(assistant, emptyList())` | `pendingResult = null` **only** — **drops the whole import** |

HEAD matches issue AC4 / design dismiss=skip. PR does not.

### Strings

HEAD (matches `implement.md` keys, not PR keys):

- `assistant_importer_detected_bindings`
- `assistant_importer_world_book_count` (`%1$d`)
- `assistant_importer_import_all`
- `assistant_importer_skip_bindings`

PR-only:

- `assistant_importer_binding_dialog_title`
- `assistant_importer_binding_dialog_message`
- `assistant_importer_binding_dialog_import`
- `assistant_importer_binding_dialog_skip`
- `assistant_importer_unnamed_lorebook`
- `assistant_importer_lorebook_entries_count`

Other locales (`values-zh-rTW`, `values-ru`, `values-ja`, `values-ko-rKR`) have base importer strings only; they fall back to English `values/strings.xml` for the new keys.

### PR `CharacterBookEntry` JSON names

PR class uses Kotlin camelCase **without** `@SerialName` for snake_case spec fields. `ExportSerializer.DefaultJson` has `ignoreUnknownKeys = true` and **no** `JsonNamingStrategy.SnakeCase`. Existing preset models in the same file use `@SerialName("prompt_order")` etc. (`ExportSerializer.kt` L388-441).

| Spec JSON key | PR property | Decode result with DefaultJson |
|---|---|---|
| `keys` | `keys` | maps |
| `enabled` | `enabled` | maps |
| `content` | `content` | maps |
| `constant` | `constant` | maps |
| `position` (string) | `position` | maps |
| `insertion_order` | `insertionOrder` | **misses** → default `100` |
| `case_sensitive` | `caseSensitive` | **misses** → null → `false` |
| `secondary_keys` | `secondaryKeys` | **misses** (unused in mapping anyway) |

Porting PR mapper as-is still imports entries; order/case-sensitivity from spec JSON are not applied unless `@SerialName` is added.

## design.md / prd.md / implement.md vs HEAD vs PR

| Design / PRD item | HEAD | PR #303 |
|---|---|---|
| Probe `data.character_book` | yes | yes (only this path) |
| Probe `data.extensions.world` | yes (as JsonObject; ST type is string) | no |
| Probe `data.extensions.character_book` | yes | no |
| Reuse `tryImportSillyTavern` | yes (wrong shape for character_book) | **no** — separate `tryImportCharacterBook` |
| `PendingImport` name | yes | uses `TavernCardImportResult` + `pendingResult` |
| `RikkaConfirmDialog` | yes | custom `AlertDialog` |
| `onImport: (Assistant, List<Lorebook>)` | yes | yes |
| Persist lorebooks then associate `lorebookIds` | yes (two writes) | one VM method |
| No new Assistant fields | yes | yes |
| Preset from card | out of scope (`prd.md`) | not implemented |
| Unit tests (`implement.md` §6 / issue AC8) | **not done** | **not done** |
| Dialog shows count not full list | count only | name + per-book entry count, scrollable |
| Dismiss ≡ skip | yes | no (dismiss aborts) |
| Invalid entries toast / skip count | **not done** | decode failure → null (silent no-dialog) |

`prd.md` AC numbering (task) vs issue #302 AC numbering differ. Issue AC2 is presets; task PRD AC2 is “selecting import persists lorebook”. Task PRD marks presets **out of scope**.

## Conflict-prone files (merge-tree HEAD vs PR)

`git merge-tree $(merge-base) HEAD origin/feat/tavern-card-bindings-302`:

| File | merge-tree |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` | **changed in both** |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt` | **changed in both** |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | **changed in both** (many hunks) |
| `app/src/main/res/values/strings.xml` | **changed in both** (upstream 2.4.12 + HEAD #302 keys vs PR keys) |
| `app/src/main/res/values-zh/strings.xml` | **changed in both** |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantVM.kt` | **merged** (PR-only addition; HEAD `addAssistant` still snapshot-based) |

Diffstat HEAD…PR for those six files: `314 insertions, 206 deletions` — mostly importer rewrite + serializer insert + string-key swap, not a clean patch on top of `0a09549f`.

## Cherry-pick / merge vs reimplement

- Merging PR #303 into current HEAD is **dirty** on 5 of 6 files. The PR branch has none of the 70 HEAD commits (including `0a09549f` and upstream 2.4.12 `b47d5d30`).
- Cherry-picking `7e026d20` (and follow-ups) onto HEAD replays the same overlapping hunks against an already-modified importer/page/strings.
- HEAD already has dialog + callback signature + string keys + visibility lift. The remaining work is **replace the mapper**, tighten persistence, add tests — not replay the PR’s UI rewrite.

Factual implication: **reimplement/port `tryImportCharacterBook` (+ tests) onto HEAD’s existing importer/dialog**, rather than `git merge` / cherry-pick the dirty PR.

## Exact files to change on current HEAD

| File | Change needed on HEAD |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt` | Add `CharacterBook` / `CharacterBookEntry` + `internal fun tryImportCharacterBook` + `mapCharacterBookPosition`. Keep existing `tryImportSillyTavern` for standalone world-info JSON. Add `@SerialName` for `insertion_order` / `case_sensitive` / `secondary_keys` to match DefaultJson style in this file. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt` | Point `detectWorldBook` at `tryImportCharacterBook(JsonElement, fallbackName)` instead of `tryImportSillyTavern(string)`. Do not coerce `extensions.world` via `.jsonObject` (ST type is string). Keep `PendingImport` + `RikkaConfirmDialog` unless product wants PR’s named-list UI. Remove unused `import me.rerere.rikkahub.data.export.ExportSerializer`. |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantPage.kt` | Persistence: one settings write that adds lorebooks **and** assistant with `lorebookIds` (today lorebooks and `addAssistant` are split). |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantVM.kt` | Optional: `addAssistantWithLorebooks` using **transform** `settingsStore.update { }` (existing `updateSettings(transform)` at L33-36), not `settings.value` snapshot. |
| `app/src/main/res/values/strings.xml` | Already has HEAD keys. PR `assistant_importer_binding_*` only needed if switching to PR dialog copy. |
| `app/src/main/res/values-zh/strings.xml` | Same as en. |
| **New** `app/src/test/java/me/rerere/rikkahub/data/export/CharacterBookImportTest.kt` (name flexible) | JVM tests for `tryImportCharacterBook` / detection. Pattern: `SillyTavernPresetImportTest.kt` (Context-free, `internal` serializer functions). |

Not required for MVP: `FilesManager` (lorebooks live in `Settings.lorebooks`, not files), Assistant model fields, preset-from-card.

## AC gaps

Mapping uses **issue #302** numbers and **task `prd.md`** numbers.

### Issue #302

| AC | Status on HEAD | Notes |
|---|---|---|
| AC1 world-book dialog | **Gap** | Dialog code exists, but canonical `character_book` decode fails → no dialog |
| AC2 preset dialog | **Out of scope** (`prd.md`); not on PR either | |
| AC3 import persists + associates | **Partial** | Path exists; mapper never produces lorebooks for spec cards; two-step write |
| AC4 skip / dismiss | Dialog skip/dismiss ≡ body-only **if dialog shows** | Dialog rarely shows for spec cards |
| AC5 no bindings → no dialog | **Met** for true-empty; also met accidentally when decode fails (looks like no bindings) | |
| AC6 PNG = JSON | **Met** for parse path (shared `importAssistantFromUri`) | |
| AC7 invalid data toast, no crash | **Partial** | `runCatching` swallows decode; `extensions.world` string can toast “import failed” and drop the assistant |
| AC8 unit tests | **Missing** on HEAD and PR | |

### Task `prd.md`

| PRD AC | Status |
|---|---|
| AC1 dialog on world book | Gap (mapper) |
| AC2 import persists Lorebook + associate | Partial (write path; mapper) |
| AC3 skip → body only | Dialog wiring present |
| AC4 no bindings → no dialog | Met / false-negative on decode fail |
| AC5 PNG = JSON | Met |
| AC6 invalid → toast skip count | **Missing** (no skip-count toast) |
| AC7 unit tests | **Missing** |

`implement.md` checklist: visibility lift **done**; detection function **done but wrong mapper**; dialog **done** (`RikkaConfirmDialog` not PR AlertDialog); persistence **wired but split**; strings **done** (design keys); tests **not done**.

## Recommended implementation plan (match existing style)

1. **Do not merge/cherry-pick PR #303.** Port the **mapper** from PR into HEAD `ExportSerializer.kt` next to `SillyTavernEntry`, with `@SerialName` like `SillyTavernPreset`.
2. Keep HEAD UI: `PendingImport` + `RikkaConfirmDialog` + existing string keys (`implement.md`).
3. Change `detectWorldBook` to pass `data["character_book"]` as `JsonElement` into `tryImportCharacterBook`. Treat `extensions.world` as optional **name** (`jsonPrimitiveOrNull`) for `fallbackName` only, not as a lorebook object. Optionally still probe `extensions.character_book` if it is a `JsonObject`.
4. Persistence: single `settingsStore.update { }` adding lorebooks and assistant with `lorebookIds` (either new VM method or fold into the existing `onUpdate` using `vm.updateSettings(transform)`). Avoid a second `addAssistant` snapshot write.
5. Tests in `app/src/test/.../data/export/`, same style as `SillyTavernPresetImportTest.kt`:
   - v2/v3 `data.character_book` array with `keys` / `insertion_order` / `before_char`
   - empty `character_book` / empty `entries` → null
   - null / non-object / garbage JSON → null
   - standalone-shaped `entries: {uid: {key:...}}` is **not** this function (covered by existing lorebook import)
6. Validation (from `implement.md`): `./gradlew --no-daemon :app:compileDebugKotlin` then focused `CharacterBookImportTest`; device install after freeze.

## Related existing research (stale vs HEAD)

Files `01`–`08` under this task were written **before** `0a09549f`. They describe pre-#302 importer (no dialog, private `tryImportSillyTavern`). Use this file for HEAD/PR delta; use `03`/`04`/`08` for standalone lorebook/preset mapping that is still accurate except visibility (`internal` now).

## Caveats / not found

- No unit tests for character-card parsing on either branch.
- No UI tests for the dialog.
- `ExportSerializer` in HEAD importer is an unused import (`AssistantImporter.kt` L42).
- `ExportSerializer.DefaultJson` is the **interface companion** (`ExportSerializer.kt` L61-67), not a separate object; PR’s `ExportSerializer.DefaultJson` is valid Kotlin.
- Issue example `data.extensions.world` as embedded entries is **not** how current SillyTavern writes cards; content is `data.character_book`.
- Preset-from-card remains out of scope per `prd.md`.
