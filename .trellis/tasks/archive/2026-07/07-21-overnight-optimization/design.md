# Technical design

## Boundaries

Optimization proceeds as a sequence of isolated candidates. The root contract files and Trellis task retain the full
scope; each candidate changes the smallest responsible module and has its own measurement, verification, and commit.
Public API and user-visible behavior remain compatible.

## Measurement contract

- Baseline and final cold builds use the same checkout, JDK, Gradle invocation, environment, cache-clearing strategy,
  variant, and `--profile` mode. Three samples are compared by median.
- APK comparisons use the same debug arm64 artifact path and exact byte count plus SHA-256.
- Static-analysis metrics use only tasks present at baseline. Initial audit found no detekt/ktlint configuration, so
  this dimension is expected to be N/A.
- Runtime candidates require a code-level bottleneck plus an executable proxy test, benchmark, query plan, trace, or
  focused behavioral test. Unmeasured intuition is insufficient.

## Candidate architecture

1. P0 build candidates: measure parallel execution, `web:preBuild` incremental behavior, dependency resolution, and
   task graph/profile evidence. Accept only repeatable median improvements without correctness or memory regressions.
2. P1 runtime candidates: prioritize synchronous rich-text parsing/highlighting, repeated asset/template I/O, key
   roulette persistence, and database query plans. Implement only candidates whose behavior can be protected.
3. P2 quality candidates: use existing Android Lint evidence and precise dead-code/reference searches. Do not add
   detekt/ktlint. Preserve all tests.
4. P3 packaging/startup candidates: inspect APK composition, native packaging, assets, resources, R8 rules, and
   baseline profiles. Native/resource changes require install smoke tests and identical-artifact measurements.
5. P4 tests: add focused regression coverage for accepted optimization behavior and replace fragile setup only when
   it improves deterministic coverage without deleting tests.

## Compatibility and rollback

- Dependency/plugin or module-boundary changes require consumer search, compatibility analysis, focused tests, and APK
  comparison before acceptance.
- An uncommitted failed candidate is restored by reversing only files/hunks introduced by that candidate.
- A later-discovered failure in a committed candidate is handled by an exact, separate revert commit.
- Three same-cause failures retire the candidate into `OPTIMIZATION_NOTES.md`.

## Operational concerns

- Only one Gradle workload runs at a time to avoid memory contention and invalid timing comparisons.
- Baseline measurement precedes all source/build edits. Documentation updates accompany each candidate.
- Device validation follows `AGENTS.md`; unavailable devices produce an explicit gap and local APK evidence.
