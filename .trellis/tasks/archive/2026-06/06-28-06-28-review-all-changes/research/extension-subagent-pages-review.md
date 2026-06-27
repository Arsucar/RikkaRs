# Code Review: Extension Subagent Pages (New Files)

- **Scope**: `ExtensionSubagentProfilePage.kt`, `ExtensionSubagentsPage.kt` on `release/rikka-arsucar` (uncommitted new files)
- **Date**: 2026-06-28
- **Reviewer role**: Architecture / Compose / navigation alignment (read-only)

---

## Summary

| File | Verdict | Severity highlights |
|------|---------|---------------------|
| `ExtensionSubagentsPage.kt` | **Mostly aligned** with Skills/Workspace list pattern | Low–medium UX/consistency |
| `ExtensionSubagentProfilePage.kt` | **Good reuse** of `SubagentProfileForm` | **Medium** create/persist logic gaps |

Navigation wiring in `RouteActivity.kt` and entry from `ExtensionsPage.kt` are consistent with other extension sub-routes.

---

## 1. `ExtensionSubagentsPage.kt`

### Strengths

- Matches extension list pages: `LargeFlexibleTopAppBar`, `nestedScroll`, `CustomColors.topBarColors`, `LazyColumn` + `innerPadding + PaddingValues(16.dp)`.
- Uses `SettingVM` + `collectAsStateWithLifecycle` for `globalSubagentProfiles` (appropriate for global settings).
- Create flow: `SubagentProfile.IdentifierRegex` + duplicate name check mirrors assistant subagent create patterns.
- Delete: `pendingDelete` + `AlertDialog` + `vm.deleteGlobalSubagent` aligns with PRD and `AssistantSubagentPage` delete pattern.
- Strings: dedicated `extensions_subagents_page_*` keys present in `values` and locale variants.
- Routing: `Screen.ExtensionSubagents` list → `Screen.ExtensionSubagentProfile(name, createMode)` registered in `RouteActivity`.

### Issues & suggestions

| # | Severity | Issue | Suggestion |
|---|----------|-------|------------|
| 1.1 | Low | **Redundant navigation**: row `onClick` and trailing `ArrowRight01` `IconButton` both navigate to the same profile editor. | Keep row click only (like other list pages) or remove arrow button to avoid duplicate affordances. |
| 1.2 | Low | **One `CardGroup` per list item** (`items { CardGroup { item(...) } }`). Other pages often use a single `CardGroup` with multiple `item {}` children. | Consider one `CardGroup` wrapping all profiles for visual parity with dense lists (optional polish). |
| 1.3 | Low | **Empty state inside `LazyColumn` `item`**: fine functionally; ensure empty state doesn’t scroll under collapsed top bar oddly (same as peers—acceptable). | No change required unless design wants centered empty state. |
| 1.4 | Info | **TopBar `+` vs Skills `FAB`**: Subagents uses TopBar `IconButton` + overflow menu; Skills uses FAB + bottom sheet. | Documented product choice (restore defaults in overflow); not a bug. |
| 1.5 | Low | **`supportingContent`**: `profile.description.take(80)` with no ellipsis; long text may clip awkwardly. | Use `take(80) + if (length > 80) "…"` or `maxLines` + `overflow = Ellipsis`. |
| 1.6 | Info | **`!!` on `pendingDelete`**: scoped inside `if (pendingDelete != null)`; safe but style-sensitive teams prefer `let`. | Optional: `pendingDelete?.let { vm.deleteGlobalSubagent(it.name) }`. |

### Unused imports / dead code

- **No unused imports detected** in the reviewed snapshot.
- All state (`showCreateDialog`, `newProfileName`, `pendingDelete`, `showOverflowMenu`) is used.

---

## 2. `ExtensionSubagentProfilePage.kt`

### Strengths

- **Correct architectural split**: global profile editing via `SettingVM.updateSettings` instead of `AssistantDetailVM`.
- Reuses **`SubagentProfileForm`** from `AssistantSubagentProfilePage.kt` (shared form UI)—matches task design (`06-27-sub-agent-global-config`).
- Scaffold/layout matches assistant profile page: scrollable column, `imePadding`, `LargeFlexibleTopAppBar` + `BackButton`.
- Loads skills via `SkillManager.listSkills()` in `LaunchedEffect`—parallel to assistant VM’s skills list for the form.

### Issues & suggestions

| # | Severity | Issue | Suggestion |
|---|----------|-------|------------|
| 2.1 | **Medium** | **`SubagentRegistry` import unused** | Remove `import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry`. |
| 2.2 | **Medium** | **`globalProfiles` omitted** on `SubagentProfileForm`: assistant path passes `globalProfiles = settings.globalSubagentProfiles`. Extension page does not pass `globalProfiles` (defaults to `emptyList()`). | Pass `globalProfiles = globalProfiles` so any form logic keyed off `globalProfiles` (e.g. `createMode && profileName !in globalProfiles.map { it.name }`) behaves consistently. |
| 2.3 | **Medium** | **`persist()` append logic**: `newProfiles` uses `if (profileName !in it.map { p -> p.name }) it + updated`. On **create**, navigating with a new name before first field edit may not append until first `onPersist`—usually OK because edits call `persist`. Edge case: user opens create route and backs out without editing—no phantom entry (good). If user edits only after rename flows exist elsewhere, verify name-key stability. | Align with PRD R4.2 if profile **rename** is added: update route args when `name` changes. Current form keeps `name` read-only—low risk until rename is supported. |
| 2.4 | **Medium** | **Create flow does not pre-insert profile**: List page navigates to `ExtensionSubagentProfile(newProfileName, true)` without calling `vm` to add stub profile. `resolved` falls back to `SubagentProfile(name = profileName)` until first persist. | Confirm `SubagentProfileForm` + first `persist` always runs on meaningful edits; consider explicit insert on enter create mode if form expects profile to exist in settings (compare assistant `upsertSubagentProfile` flow). |
| 2.5 | Low | **No `SubagentRegistry.resolveProfile`**: Assistant page resolves merged assistant/global semantics; extension page only reads `globalProfiles.firstOrNull`. | Correct for global-only editor; document intent in KDoc if helpful. |
| 2.6 | Low | **Fully qualified `SkillMetadata` in `mutableStateOf`** | Use imported type alias or import `SkillMetadata` for readability (minor). |
| 2.7 | Info | **`createMode` passed but not used in page** beyond forwarding to form | OK if form handles it; ensure create UX (read-only identifier field) is visible when `createMode == true`. |

### Unused imports / dead code

- **`SubagentRegistry`**: imported, never referenced → **remove**.
- `pathDraft` / `excludedDraft`: passed into form—**used** (not dead).

---

## 3. Cross-cutting: Assistant vs Extension alignment

| Concern | Assistant | Extension (new) | Aligned? |
|---------|-----------|-----------------|----------|
| List CRUD | `AssistantSubagentPage` + local profiles | `ExtensionSubagentsPage` + `SettingVM` | Yes (different data scope) |
| Profile editor shell | `AssistantSubagentProfileContent` + global read-only banner | Direct `SubagentProfileForm`, `readOnly = false` | Yes for global edit |
| Shared form | `SubagentProfileForm` (internal) | Same composable | **Yes** |
| Global profile from assistant | Navigate to `Screen.ExtensionSubagentProfile` | N/A | Wired in `AssistantSubagentPage` |
| VM layer | `AssistantDetailVM` | `SettingVM` | Correct |

---

## 4. Navigation & routing

- `Screen.ExtensionSubagents` (`data object`) and `Screen.ExtensionSubagentProfile(profileName, createMode)` match serialized routes in `RouteActivity.kt`.
- `ExtensionsPage` entry: `Screen.ExtensionSubagents` after Workspaces—matches PRD `06-27-ext-subagent-section` / `06-27-builtin-subagent-global`.
- **No missing route entries** found for these pages.

---

## 5. Compose best practices

- **Lifecycle**: `collectAsStateWithLifecycle` on settings—good.
- **Keys**: `items(profiles, key = { it.name })`—good for stable list identity.
- **Scroll**: `exitUntilCollapsedScrollBehavior` + `nestedScroll` on Scaffold—consistent.
- **Dialogs**: create/delete dialogs reset state on dismiss—good.
- **Accessibility**: several `Icon(..., contentDescription = null)`—same as nearby pages; consider `stringResource` CD for Add/Delete/More if project a11y bar is rising.

---

## 6. Recommended follow-ups (for implementer, not done in this review)

1. Remove unused `SubagentRegistry` import in `ExtensionSubagentProfilePage.kt`.
2. Pass `globalProfiles` into `SubagentProfileForm` on extension profile page.
3. Manually test: create profile → edit fields → back → list shows new entry; delete → confirm removed; restore defaults → toast + builtins added.
4. Optional: dedupe list row navigation (click vs arrow).
5. If product requires profile rename in global editor, plan route param sync (task `06-27-fix-modellist-subagent-prod` R4.2).

---

## Files referenced (not in review scope but used for comparison)

- `ExtensionsPage.kt`, `RouteActivity.kt`
- `AssistantSubagentPage.kt`, `AssistantSubagentProfilePage.kt` (`SubagentProfileForm`)
- `SettingVM.kt` (`deleteGlobalSubagent`, `restoreDefaultSubagents`)
- `skills/SkillsPage.kt` (list page pattern)