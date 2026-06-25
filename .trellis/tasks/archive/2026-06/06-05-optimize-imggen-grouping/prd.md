# Optimize image generation grouping

## Goal

Improve image generation history grouping so similar prompt variants are grouped accurately without the Union-Find
transitive-match failure mode, while keeping regrouping fast enough for the full generated media history flow.

The user value is a grouped image gallery that keeps useful Midjourney-style variants together, avoids losing valid
variant pairs when a broad connected component has no shared global template, and does not become sluggish as image
history grows.

## Confirmed Facts

- `ImgGenVM.groupedImages` observes all non-trash generated media through `GenMediaRepository.observeAllMedia()` and
  rebuilds groups on each emitted list.
- `GenMediaDAO.observeAllMedia()` returns all non-trash `GenMediaEntity` rows ordered by `create_at DESC`.
- `buildGeneratedImageGroups()` is an `internal` pure function covered by JVM tests in
  `ImgGenGroupingTest.kt`.
- Current behavior first groups exact normalized prompts, then uses pairwise prompt-template matches and Union-Find to
  create connected components.
- The current component-level fallback can split every item in a connected component into single prompt groups if the
  whole component does not share one valid `PromptTemplate`, even when valid subgroups exist.
- Current pairwise matching scans every exact prompt group against every later group, so the matching step is O(N^2).
- The grouped gallery is a user-facing history display mode, selected from `ImageGalleryDisplayMode.GROUPED`.
- Existing tests cover inserted variants, replaced variants, and a same-scene rewritten prompt grouping case.
- The user-provided same-scene campus-road prompt set with base, inserted-person-detail, and rewritten-clothing/zoom
  variants produces a valid shared template under the current template rules and under the planned greedy clustering
  order when the rewritten prompt is newest.

## Requirements

- Preserve exact prompt grouping: images with the same normalized prompt remain one `GeneratedImageGroup`.
- Preserve useful prompt-variant grouping for inserted and replaced prompt differences already covered by tests.
- Preserve the user-provided three-prompt campus-road scenario as one variant group.
- Replace Union-Find connected-component clustering with logic that does not discard valid local variant groups because
  of transitive matches that cannot share one global template.
- Bound fuzzy prompt comparisons to the next 30 recent exact-prompt groups so the grouping path no longer performs
  full-history O(N^2) matching.
- Keep output groups sorted by newest image timestamp descending.
- Keep variant sections sorted consistently with current behavior: newest exact prompt groups first.
- Keep the core grouping logic as pure, testable Kotlin functions.
- Add or update JVM tests that prove the transitive-match bug is fixed and existing grouping cases still pass.

## Acceptance Criteria

- [x] A chain case where A matches B and B matches C, but A/B/C share no valid global template, does not collapse into
      all single-image groups; at least the valid center-based/local pair grouping is preserved.
- [x] Existing `ImgGenGroupingTest` cases continue to pass.
- [x] The user-provided campus-road base/inserted/rewritten prompt scenario remains one grouped variant set.
- [x] Fuzzy matching is bounded by a documented constant or helper with a fixed search window of 30 exact-prompt groups.
- [x] The grouped gallery continues to receive `GeneratedImageGroup` objects with the same public data shape.
- [x] JVM test command for the app grouping tests passes.

## Out of Scope

- Database schema changes or migrations.
- UI redesign of the grouped gallery.
- User-configurable grouping thresholds unless explicitly requested.
- Paging-aware grouped history redesign.

## Open Questions

- None.
