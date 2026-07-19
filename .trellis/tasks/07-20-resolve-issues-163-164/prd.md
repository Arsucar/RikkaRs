# PRD: Resolve issues #163 and #164

## Goal

Deliver two independent product fixes on `release/rikka-arsucar`:

1. **#163** — Restore usable assistant「赋能工具」page UX after catalog/diagnostics regression.
2. **#164** — Allow subagent profiles to inject selected memory tables into subagent context.

## Parent ownership

- Source requirements: GitHub issues #163 and #164.
- Child map:
  - `07-20-assistant-tools-page-ux` → #163
  - `07-20-subagent-memory-table-injection` → #164
- Cross-child acceptance:
  - Both land on `release/rikka-arsucar`.
  - Domain permission/runtime contracts for tools and memory tables remain intact unless a child PRD explicitly changes them.
  - Close both issues with bilingual delivery comments after verification.

## Product decisions (no further user decisions)

### #163
- User-facing count = `effectiveCount / userFacingTotal`, where `userFacingTotal` counts capabilities that are configured **or** effective (excludes unbound workspace×N and unselected MCP noise).
- Diagnostics: one readable line in UI; raw `copySummary()` only for copy action.
- Advanced blocks (diagnostics detail, presets, connection, orphan) default collapsed under「高级」.
- Multi-select: long-press enter; default no checkboxes.
- Preset apply: tighten → snackbar success; relax → confirm dialog; reject reason snackbar.
- MCP default: configured/selected only; toggle「显示全部」.
- Domain: do **not** change `applyAssistantToolPermissions` / catalog effective resolution semantics.

### #164
- Persist `injectedMemoryTableIds: Set<String>` (template document/template stable ids — document/template id strings already used by MemoryTableRepository) on `SubagentProfile`.
- Default empty → no injection (compat).
- At spawn: filter selected ids by parent assistant access + inject-allowed schema + non-empty payload; skip invalid silently.
- Reuse `buildMemoryTablePrompt` / `MemoryTableInjectionTransformer` formatting and budget settings.
- Read-only injection only; does not grant memory-table write tools.
- Snapshot at spawn start for stream consistency.

## Out of scope

- Full #155 repair-navigation UX beyond stopping wrong containers from polluting main list.
- Changing main-agent memory table injection behavior.
- Opening PRs to upstream.

## Acceptance (parent)

- [ ] #163 child acceptance met and installable.
- [ ] #164 child acceptance met and installable.
- [ ] Issues closed with CN + EN comments (commit, verify evidence, known bounds).
