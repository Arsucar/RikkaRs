# Implementation plan

## 1. Baseline and inventory

- Capture branch, commit, status, host hardware/OS, JDK, Gradle, AGP, disk/memory context, and device availability.
- Discover baseline Gradle tasks and confirm detekt/ktlint absence/presence without adding tools.
- Run baseline `assembleDebug`, all JVM tests, and three serialized `clean assembleDebug --profile` samples using one
  documented cache strategy.
- Record the chosen arm64 debug APK path, exact bytes, SHA-256, and archive the relevant profile report identifiers.
- Inspect dependency/task/module graphs and APK composition, then create root `OPTIMIZATION_PLAN.md`.

## 2. P0 build loop

- Benchmark Gradle parallel execution with the same three-sample method before editing `gradle.properties`.
- Check `:web:preBuild --info` twice to determine whether pnpm work is truly incremental.
- Inspect dependencies and `api`/`implementation` boundaries; evaluate `mavenLocal()` only after resolution evidence.
- For each accepted candidate: update documentation, run relevant focused checks, pass `assembleDebug`, inspect staged
  diff/check, and create one reversible commit.

## 3. P1 runtime loop

- Measure or protect the highest-confidence candidates: rich-text synchronous parsing/highlighting, preview template
  I/O, key roulette persistence, and Room query plans.
- Read all exact target code and consumers before editing. Add focused tests or executable benchmarks/query-plan checks.
- Apply candidate-level verification, install app changes when applicable, document boundaries, and commit atomically.

## 4. P2-P4 loops

- Use existing Android Lint output/baseline for actionable quality work; keep detekt/ktlint marked N/A if absent.
- Confirm dead code by complete reference search and build verification before removal.
- Analyze identical APK contents before packaging/resource/R8/profile changes; smoke-test native/workspace behavior.
- Add deterministic regression tests that protect accepted optimizations; never remove existing tests.

## 5. Final verification and report

- Run final focused checks, `assembleDebug`, all JVM tests, applicable lint, and device installation verification.
- Repeat the exact three-sample cold-build protocol and identical APK measurement.
- Complete `OPTIMIZATION_NOTES.md` for failures/skips/gaps and `OPTIMIZATION_REPORT.md` for exact deltas, commits,
  outcomes, unmet targets, and recommendations.
- Pass `git diff --cached --check` and review staged content before every commit.
- Commit the final report as exactly `docs: add overnight optimization report`; do not push.

## High-risk rollback points

- Gradle/plugin/dependency changes: task graph, cache compatibility, and web resource generation.
- Async rich-text changes: stale result races, initial blank content, and cursor offset correctness.
- Room indexes: migration version, write amplification, storage, and query-plan compatibility.
- Native packaging/R8/resources: native loading, workspace terminal behavior, reflection, and installed APK behavior.

## Start gate

- User authorization is already explicit in the optimization objective and `Prompt.md`.
- Do not modify source or build configuration until baseline and root `OPTIMIZATION_PLAN.md` are complete.
