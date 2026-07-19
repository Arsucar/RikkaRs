# PRD: Assistant tools page UX regression (#163)

## Problem

After catalog/diagnostics work (#153–#157), `AssistantToolsPage` became a debug console: vertical `copySummary` text, reason-over-description rows, always-open groups, empty checkboxes, English preset slugs, silent preset apply, confusing `effective/total` counts.

## Goals

Restore a settings-first empowerment tools page while keeping domain permission/catalog semantics unchanged.

## Requirements

### Diagnostics
- Show one user-readable line: e.g. 「有效 n / 共 m」using user-facing total.
- Do not put `copySummary()` into both headline and supporting.
- Copy button still copies desensitized whitelist summary from `copySummary()`.
- Expanded diagnostics stay secondary; no wrong `CardGroup`-per-capability pollution of main path.

### Tool rows & groups
- Closed category: collapse tool rows (no「此助手未选择」spam).
- Expanded: Chinese description + status tag coexist; reason must not replace description.
- MCP default `configuredOnly = true`; provide show-all toggle.
- Connection status localized (no raw enum `.name`).

### Multi-select (issue comment)
- Default: no leading Checkbox.
- Long-press row → multi-select mode with checkboxes.
- Exit multi-select restores default list.
- Non-multi-select tap still opens single-tool permission sheet.
- Batch permission remains available only in multi-select.

### Presets
- Chinese display names for built-in presets.
- Apply tighten → success feedback.
- Apply relax → confirmation; reject shows reason.

### Information architecture
- Diagnostics / presets / connection / orphan under advanced section (collapsed by default).
- Main path: group switches + tool list.

### Counts
- Entry badge + page title use user-facing total:
  - numerator: existing `effectiveCount`
  - denominator: capabilities that are `configured || effective` (exclude pure unbound/unselected catalog noise)
- Document the formula in code via helper name/comments only as needed for tests; no product FAQ required beyond clear UI numbers.

### Domain freeze
- Do not change: `ChatService.applyAssistantToolPermissions` path, `applyAssistantToolPermissions`, catalog effective resolution, `copySummary` string contract, preset UUIDs.

## Acceptance

1. Chinese narrow width: no vertical char stacking in diagnostics.
2. Closed groups collapse tool rows.
3. Tool row shows description + status together.
4. Entry count explainable (`effective / configured-or-effective`).
5. Preset: tighten success feedback; relax confirm or explicit reject.
6. Advanced blocks do not block main switches (default collapsed OK).
7. Permission/runtime behavior unchanged unless documented.
8. Unit tests for UI helpers + `copySummary` contract + multi-select state helpers as feasible without full Compose instrumentation unless already set up.

## Non-goals

- Full diagnostic repair navigation UX.
- Changing backend permission resolution.
