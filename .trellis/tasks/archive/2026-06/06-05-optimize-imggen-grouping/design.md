# Optimize Image Generation Grouping - Design

## Architecture And Boundaries

The change stays inside `ImgGenVM.kt` grouping helpers and `ImgGenGroupingTest.kt`.

Public UI-facing data classes remain unchanged:

- `GeneratedImage`
- `GeneratedImageGroup`
- `GeneratedImageVariantGroup`

The ViewModel continues exposing `groupedImages: StateFlow<List<GeneratedImageGroup>>`. No repository, DAO, schema, or
Compose UI contract changes are planned.

## Data Flow

1. `GenMediaDAO.observeAllMedia()` emits all non-trash media ordered by `create_at DESC`.
2. `ImgGenVM.groupedImages` maps entities to `GeneratedImage`.
3. `buildGeneratedImageGroups()` normalizes prompts and creates exact prompt groups.
4. Exact prompt groups are sorted by newest timestamp descending.
5. Fuzzy variant grouping is built with a fixed recent search window of 30 exact prompt groups.
6. Final `GeneratedImageGroup` values are sorted by newest timestamp descending.

## Grouping Algorithm

Replace Union-Find connected components with template-stable greedy clustering:

1. Take the newest unassigned exact prompt group as the cluster head.
2. Scan at most `MAX_VARIANT_SEARCH_WINDOW` later exact prompt positions, skipping candidates already assigned to a
   newer cluster.
3. Accept a candidate only if the whole proposed cluster still produces a valid `PromptTemplate`.
4. Store the latest valid template whenever a candidate is accepted.
5. Emit a template group when the cluster has at least two members and a valid template; otherwise emit the exact group.

This avoids the transitive-match bug:

- A can match B.
- B can match C.
- A, B, C might not share a valid global template.

With template-stable greedy clustering, once B groups with A, C is accepted only if B/A/C still share a valid template.
If not, the already valid B/A group survives instead of the entire component falling back to single groups.

## Prompt Template Helpers

Keep existing template semantics:

- Prompts must be non-blank and not all identical.
- Common prefix plus suffix must meet the current score threshold.
- Extracted variants must be useful and not exceed the existing grouping length cap.

Refactor common prefix/suffix calculation to use Kotlin standard library helpers:

- `commonPrefixWith`
- `commonSuffixWith`

Suffix comparison must run on strings with the accepted prefix removed so prefix and suffix do not overlap.

## Compatibility

No migration is needed. The grouping output shape is unchanged. Some fuzzy groups may change when prompts are separated
by more than 30 exact prompt groups; this is intentional and keeps the full-history regrouping path bounded.

## Trade-Offs

- Fixed window `30`: predictable cost and no settings surface, but variants separated by more than 30 newer exact
  prompt groups may not group.
- Greedy clustering: deterministic and cheap, but not globally optimal. A prompt that could belong to multiple variant
  pairs is assigned to the first valid newest cluster.
- Template-stable admission: avoids invalid broad groups, but may produce smaller clusters than pairwise connectivity.

## Rollback

Rollback is local: restore the previous Union-Find helper and related tests in `ImgGenVM.kt` / `ImgGenGroupingTest.kt`.
No data migration or persisted state needs cleanup.
