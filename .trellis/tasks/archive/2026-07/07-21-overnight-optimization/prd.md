# Overnight measured optimization

## Goal

Deep-optimize RikkaRs using reproducible measurements and independently reversible changes across build speed,
runtime performance, code quality, APK size, and test robustness.

## Confirmed Facts

- The authoritative contract is the repository-root `Prompt.md`; `Plan.md`, `Implement.md`, and `Documentation.md`
  define the execution sequence and evidence log.
- The starting branch and commit are `release/rikka-arsucar` at `a07d71b3`.
- `Prompt.md`, `Plan.md`, `Implement.md`, and `Documentation.md` were untracked before optimization work began and
  must be preserved.
- The repository has no pre-existing detekt or ktlint task/configuration. That target is therefore N/A unless later
  evidence contradicts the initial static audit; no tool may be added solely to manufacture the metric.
- Configuration cache and non-transitive R classes are already enabled. Parallel Gradle execution is not enabled.
- Debug APKs are already arm64-only; release already enables code and resource shrinking.

## Requirements

- Establish a reproducible baseline before changing source or build configuration.
- Create and maintain `OPTIMIZATION_PLAN.md`, `Documentation.md`, optional `OPTIMIZATION_NOTES.md`, and final
  `OPTIMIZATION_REPORT.md`.
- Evaluate P0-P4 candidates only when supported by code or measurement evidence.
- Keep each accepted candidate independently reversible and record its focused verification and metric impact.
- Run every Gradle command with `--no-daemon`; pass `assembleDebug` before every optimization commit.
- Run focused tests for logic changes and all JVM tests at final verification.
- Perform applicable app installation verification according to `AGENTS.md`, or record the exact device gap.
- Preserve user-visible behavior, public contracts, existing tests, and all pre-existing worktree changes.
- Never modify LICENSE, applicationId, signing configuration, `.gitmodules`, or release/publish workflows; never push,
  change `.git/config`, fabricate measurements, or use destructive git recovery.
- After three failures caused by the same candidate, record it in `OPTIMIZATION_NOTES.md` and move on.

## Acceptance Criteria

- [ ] Baseline records host, JDK, Gradle, AGP, branch/commit, initial status, three cold-build samples and median,
  selected APK exact bytes/SHA-256, JVM test outcome, configured static-analysis outcome, and device availability.
- [ ] `OPTIMIZATION_PLAN.md` records every P0-P4 candidate's evidence, expected mechanism, risk, validation, status,
  and result.
- [ ] Every committed candidate passed its focused checks and `./gradlew --no-daemon assembleDebug`.
- [ ] All safe evidence-backed candidates are implemented or have an explicit skip/rejection rationale.
- [ ] Final comparable three-sample cold-build median is reported against baseline, targeting at least 20% reduction.
- [ ] The identical APK artifact grows no more than 5%; exact before/after bytes and hashes are reported.
- [ ] Existing detekt/ktlint warnings fall at least 50%, or the metric is honestly reported N/A because the tasks did
  not exist at baseline.
- [ ] Final `assembleDebug` and all JVM tests pass, with applicable device installation evidence or an explicit gap.
- [ ] Final report includes commits, skipped/failed items, unmet targets, verification gaps, and recommendations.
- [ ] `OPTIMIZATION_REPORT.md` is committed as `docs: add overnight optimization report` without pushing.

## Out of Scope

- Product features, UI/UX redesign, global architecture rewrites, release signing workarounds, CI release/publish
  changes, or behavior/test removal.

## Open Questions

- None. The user has explicitly authorized execution under `Prompt.md`; candidate selection remains evidence-gated.
