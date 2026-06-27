# Subagent Settings UI

## Goal

Let users configure subagent behavior per assistant: enable the feature, set nesting depth, and manage built-in and custom subagent profiles through the existing assistant settings flow. All changes persist with the assistant and survive app restart.

## Parent

- Parent task: `06-27-subagent-mvp` (Subagent System MVP)

## Dependencies

- **Child: `06-27-subagent-model` (Data Model)** — `SubagentProfile`, `WorkspaceAccess`, `WorkspaceApproval`, built-in registry (`explore`, `coder`, `reviewer`), and `Assistant` fields (`enableSubagents`, `subagentMaxDepth`, `subagentProfiles`, `disabledBuiltinSubagents`) must exist and serialize before this UI can bind to them.

## Requirements

### 1. Assistant detail page additions

On the assistant detail hub (same navigation and ViewModel pattern as other assistant sub-settings):

| Control | Requirement |
|---------|-------------|
| Master switch | Label **「启用子代理」**; binds to `enableSubagents`. |
| Max depth | User-adjustable integer in range **1–3**, default **2** when unset; binds to `subagentMaxDepth`. Control may be a slider or number picker; value must always remain within range. |
| Profile list entry | A navigation row opens the subagent profile list for this assistant. |

When subagents are disabled, depth and profile entry may remain visible but must communicate that profiles apply only after enable (or follow the same gating pattern as other assistant feature toggles—consistent with the rest of assistant detail).

### 2. Subagent profile list page

Display the **effective** profile set for the assistant:

- **Built-in** profiles from the registry: `explore`, `coder`, `reviewer`.
- **Custom** profiles from `assistant.subagentProfiles` (entries not replacing a built-in name, or user-defined names only).

Each profile card must show at minimum:

- Display name
- Description (truncated if long)
- Workspace access level (user-facing labels aligned with enum: **无** / **只读** / **完整** for `NONE` / `READ_ONLY` / `FULL`)
- Approval strategy (`INHERIT` / `AUTO` / `OVERRIDE` — user-readable labels)
- Whether the profile **can spawn** other subagents (`canSpawn`)

Built-in profiles must show a **「内置」** badge.

**Actions:**

| Action | Applicability |
|--------|----------------|
| Create new custom profile | Always (navigates to profile editor in create mode) |
| Edit | All profiles (built-in edits produce or update an override in `subagentProfiles` per model merge rules) |
| Clone | All profiles (creates a new custom profile with a new `name`) |
| Disable | Built-in only — adds `name` to `disabledBuiltinSubagents` |
| Delete | Custom only — removes from `subagentProfiles`; built-ins cannot be deleted |

Disabled built-ins must appear **greyed out** with a control to **re-enable** (remove from `disabledBuiltinSubagents`).

### 3. Profile edit page

Editor for one `SubagentProfile`. **First-class (★) settings** — prompt, model, workspace access, approval, spawn — are grouped at the top; remaining fields follow in a logical order below.

| Field | UI requirement |
|-------|----------------|
| `name` ★ | Text field; pattern **`[a-z][a-z0-9_]*`**; **editable only when creating** a new custom profile; immutable on edit (including built-in override). |
| `displayName` ★ | Text field |
| `description` ★ | Text field; copy must state this text is shown to the **main AI** for profile selection |
| `systemPrompt` ★ | Large multiline text area |
| `chatModelId` ★ | Model picker; optional; blank = inherit assistant/global default |
| `temperature` | Optional; blank = inherit |
| `topP` | Optional; blank = inherit |
| `maxTokens` | Optional; blank = inherit |
| `reasoningLevel` | Enum selector consistent with main assistant reasoning UI |
| `maxSteps` | Number picker, range **1–256** |
| `workspaceAccess` ★ | Enum: **无** / **只读** / **完整** |
| `workspaceApproval` ★ | Enum: **INHERIT** / **AUTO** / **OVERRIDE** (localized labels) |
| `allowedPathPrefixes` | Chip group: add/remove paths; default includes **`/workspace`** when appropriate for new profiles |
| `canSpawn` ★ | Switch |
| `streamOutput` | Switch |
| `inheritTools` | Switch; when **on**, show `excludedTools` subsection; when **off**, show explicit tool/skill/MCP selectors below |
| `excludedTools` | Chip group; editable when `inheritTools` is true |
| `localTools` | Multi-select; editable when `inheritTools` is false |
| `enabledSkills` | Multi-select; editable when `inheritTools` is false |
| `mcpServerIds` | Multi-select; editable when `inheritTools` is false |
| `enableMemory` | Switch |

**Validation on save:**

- `name` required for create; must match pattern and not collide with reserved built-in names unless editing that built-in override per product rules.
- Invalid or empty required first-class fields block save with clear inline or dialog feedback.

### 4. State persistence

- All mutations go through **`AssistantDetailVM.updateAssistant`** (or equivalent single assistant update path used by other detail sub-pages).
- Custom profiles: stored in **`assistant.subagentProfiles`**.
- Disabled built-ins: stored in **`assistant.disabledBuiltinSubagents`**.
- Enable flag and max depth: **`enableSubagents`**, **`subagentMaxDepth`** on the same `Assistant` instance.
- Persisted via the same assistant settings storage as existing fields; reload after process death must restore UI state.

### 5. Product rules and empty states

- User cannot **enable** subagents without **at least one spawnable profile** (built-in enabled or custom present); show clear copy when enable is blocked.
- When **max depth is 1**, explain that nested subagent delegation is not available (copy distinct from depth 2–3).
- Settings changes apply to **new** spawns only; in-flight runs follow parent MVP cancel/complete rules (no requirement to implement runtime here—UI must not imply immediate kill).

## Out of scope

- Chat subagent cards, spawn tool registration, runtime nesting, permission enforcement implementation
- Trellis / IDE subagent injection
- Automated tests in this PRD (acceptance requires verifiable behavior in the app)

## Acceptance Criteria

- [ ] Assistant detail shows **「启用子代理」** toggle, max depth control (1–3, default 2), and navigation to the profile list.
- [ ] Toggle enables/disables subagent feature for the assistant; persisted and restored after restart.
- [ ] Profile list shows all built-in and custom profiles with display name, description, workspace access, approval strategy, and `canSpawn`; built-ins show **「内置」** badge.
- [ ] Built-in profiles cannot be deleted; disable greys out card and sets `disabledBuiltinSubagents`; re-enable clears disable.
- [ ] Custom profiles can be created, edited, cloned, and deleted; built-ins can be edited/cloned/disabled per requirements.
- [ ] Profile edit page exposes all listed fields with appropriate controls; first-class fields grouped at top.
- [ ] `name` is editable only on create and validated as `[a-z][a-z0-9_]*`.
- [ ] `inheritTools` toggles visibility/editability of excluded vs explicit tool/skill/MCP sections.
- [ ] Changes flow through `AssistantDetailVM.updateAssistant` and survive app restart.
- [ ] Enable blocked when no usable profile exists, with understandable empty-state messaging.
- [ ] Max depth 1 vs higher explained in UI copy where depth is configured.

## Notes

- Align model picker and reasoning level controls with existing assistant basic/detail pages for consistency.
- Built-in override merge semantics follow `06-27-subagent-model` PRD (same `name` as built-in).
- Parent cross-child criteria: disabled builtins are not spawnable; settings changes affect new spawns only.