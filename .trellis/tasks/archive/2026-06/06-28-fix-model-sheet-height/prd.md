# PRD: Improve ModelListSheet height

## Problem
`ModelListSheet` (ModelList.kt:285-306) uses `fillMaxHeight(0.85f)` on its inner Column, but the provider badge row at the bottom (ModelList.kt:827-903) — when `providerTabsExpanded=true` — is constrained by `heightIn(max = 200.dp)` and placed **after** the `LazyColumn(weight=1f)`. Because the Column layout distributes remaining space to the weighted LazyColumn first, the expanded provider tab area competes for vertical space and the user sees only ~1/3 of screen for the model list content.

## Requirements
1. When provider tabs are collapsed (single LazyRow), the sheet should behave as-is (85% height, LazyColumn fills remaining space after search + tags + badge row).
2. When provider tabs are expanded (`providerTabsExpanded=true`), the expanded provider list should **not** drastically reduce the model list area. The LazyColumn should keep the majority of vertical space.
3. The expanded provider tab area caps at a reasonable height (current 200.dp is fine), but it must be placed such that it does not starve the LazyColumn.
4. No behavioral change to search, tag filtering, or model selection.

## Acceptance Criteria
- [ ] With provider tabs expanded, the model list LazyColumn occupies most of the sheet height (not squeezed to ~1/3).
- [ ] With provider tabs collapsed, layout is visually identical to current.
- [ ] The expanded provider chips area scrolls within its 200.dp cap.
- [ ] Sheet drag/swipe dismiss still works.
