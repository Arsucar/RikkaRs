# Optimization notes

## Verification failures resolved

- R1's first focused test compilation used `kotlin.test`, which is not declared by the app module. The test was
  corrected to the repository's JUnit4 dependency and then passed.
- R4's first two focused compilations could not infer `LruCache` around `emptyMap`/`Json.decodeFromString`. The
  implementation now uses explicit generic types; the third run and the expanded concurrency test passed.

These were implementation/test authoring errors, not evidence that the optimization mechanisms were unsafe. Neither
candidate reached three same-cause failures.

## Skipped or not measurable

- B2 `web:buildWebUi`: two consecutive `:web:preBuild --info` runs were UP-TO-DATE (22 s and 10 s); no rerun evidence.
- B3/B4 dependency and `mavenLocal()` changes: no confirmed classpath leak or local-only artifact was established;
  compatibility risk outweighed speculative build gains.
- R2/R3 asynchronous Markdown/highlighting changes: no Macrobenchmark/Perfetto harness exists in the repository, and
  cursor/stale-result behavior is high risk without a protected UI test.
- R5 Room indexes: no production-scale database or `EXPLAIN QUERY PLAN` fixture was available; adding migrations based
  on inference alone was rejected.
- R6 image/TTS allocation changes: no memory/trace benchmark was available and provider/callback semantics are broad.
- Q2 dead-code removal: no item met the complete-reference and behavior-preservation threshold during this run.
- P3 native packaging/R8/resource changes: release/device compatibility evidence was insufficient; existing arm64 split
  and release shrinking were retained.

## Quality gaps

- detekt and ktlint were not configured at baseline, so their warning target is N/A; no new analyzer was introduced.
- The one final `:app:lintDebug` gate found 180 errors, 61 warnings, and 1 hint (with additional baseline-filtered
  findings). The first error was an existing `LocalContextGetResourceValueCall` at `ChatInput.kt:566`. The baseline was
  not regenerated and release workflows were not changed.
- Runtime candidates R1 and R4 have mechanism/regression tests but no numeric frame-time, I/O, or allocation delta;
  the final report does not claim one.
