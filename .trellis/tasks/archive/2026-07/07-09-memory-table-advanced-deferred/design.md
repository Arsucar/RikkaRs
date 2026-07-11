# Deferred Memory Table Advanced Features Design

## Status

This task intentionally remains a planning/deferred package. The prerequisite
foundation work is now complete:

- Validation and readable errors (#81/#83/#85/#87)
- Row identity and row/document delete semantics (#86/#90/#92)
- Observability, template lifecycle, and scope safety (#84/#88/#91/#95)

The remaining issues are larger capabilities that should be implemented as
separate final-phase tasks, not mixed into the current broad fix batch.

## Recommended Order

1. #93 `injectPolicy.enabled` per-table injection gate.
   - Lowest-risk advanced feature.
   - Uses current schema JSON and injection transformer only.
   - Does not require DB migration.

2. #97 `query` action for row/field filtering.
   - Builds on row identity and readable errors.
   - Gives agents a safer way to locate rows before writes.

3. #98 `apply_ops` batch writes.
   - Should reuse #97/#92 selectors and row-key resolution.
   - Needs explicit atomicity tests so failures do not partially mutate payload.

4. #94 trigger-send row filtering.
   - Depends on #93 table gates and benefits from #97 row matching helpers.
   - Should share injection rendering with automatic/macro output.

5. #99 manual memory-table macros/placeholders.
   - Should reuse the same render pipeline as #93/#94.
   - Requires a product decision about `auto`, `macro_only`, and duplicate
     injection prevention.

6. #100 JSON import/export bundle.
   - Needs a versioned format, scope mapping, conflict policy, and size limits.
   - Best as its own design task with UI/tool entry decision.

7. #96 revision snapshots and rollback.
   - Requires Room schema/migration design and retention policy.
   - Should be its own high-risk data-migration task.

## Deferred Decisions

- Whether #99 is configured globally, per assistant, or only through prompt
  macro detection.
- Whether #100 exposes import through AI tools, UI only, or both.
- #96 retention policy, delete behavior, and migration path.
- Whether #94 row filtering uses simple contains matching only, or shares future
  query predicate code from #97.

## Boundaries

- Do not implement #96/#100 inside small bugfix batches.
- Do not introduce another row selector syntax for #98; reuse #92/#97 strategy.
- Injection features must share one renderer so automatic injection, future row
  filtering, and future macros do not diverge.
