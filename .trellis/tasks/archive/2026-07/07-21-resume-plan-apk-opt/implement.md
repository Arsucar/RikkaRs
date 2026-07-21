# Implement: Resume Plan.md optimization

## Preconditions

- [x] Task created: `.trellis/tasks/07-21-resume-plan-apk-opt`
- [x] User reviewed the delivery state and requested fixes plus archive
- [x] `python ./.trellis/scripts/task.py start` completed (status → in_progress)
- [x] `trellis-before-dev` / relevant `.trellis/spec` indexes loaded before final review fixes

## Checklist (ordered)

### A. Inventory sync (no product code)

1. Diff `Plan.md` vs `OPTIMIZATION_PLAN.md` vs commits `a8575336..a3316a88` + report `e54e7e92`.
2. Write a short coverage matrix into task `research/` or NOTES draft:
   - Plan item → prior status → required residual work.
3. **Do not** check Plan boxes until residual work for that item is done this round.

### B. P0 residual (start here)

Order matches `Plan.md` unchecked items:

1. **api/implementation 泄漏与不必要模块依赖**
   - Module dependency graph + `api` usage audit
   - Fix confirmed leaks **or** evidence-rich skip (insight logs summary in NOTES)
2. **kapt → KSP / 插件迁移评估**
   - Inventory; cost/benefit; migrate only if justified; else documented decision
3. **移除确认无用的插件、依赖、生成任务**
   - Only with reference/build proof
4. **每候选用同一 profile 方法对比**
   - For each new accepted/rejected build candidate this round

Validation after each accepted B candidate: focused compile/tests + optional cold sample set if claiming build gain.

### C. P1 residual

For each Plan bullet (Compose / Room / OkHttp / Coil / main-thread IO / evidence-only implements):

1. Code-backed review note
2. Implement only low-risk, evidence-backed items
3. Focused tests; app install if app module behavior changes
4. Check Plan box + update PLAN ledger

Already shipped R1/R4: document as done with commit SHAs when checking related bullets (do not re-implement).

### D. P2 residual

1. detekt/ktlint → reconfirm N/A
2. Dead code / unused refs with proof
3. Duplication merge only if clear win
4. Module coupling that expands compile/runtime cost
5. Tests for refactored critical logic

### E. P3 residual

1. Unused resources (reference + build proof)
2. R8/ProGuard keep review
3. Bitmap/vector/shrink/packaging duplicates
4. Baseline profile / startup path (only if build+install+verify possible)

APK size gate vs recorded baseline.

### F. P4 residual

1. Regression tests for **this round’s** behavior changes
2. Affected module tests
3. No CI workflow edits; no default AndroidTest

### G. 阶段 6 收尾（仅当 A–F 的 Plan 项均已勾选）

1. Cold build ×3 median (same protocol)
2. Prefer single Gradle invocation for final assemble + test where possible
3. `assembleDebug`, full `test`, install if app code changed
4. Complete/update `OPTIMIZATION_NOTES.md`
5. Revise `OPTIMIZATION_REPORT.md` with:
   - Plan coverage matrix (all items)
   - Resume-round vs first-round metrics
   - Honest target status
6. Docs commit message when user authorizes: e.g. `docs: revise overnight optimization report after Plan resume`

## Validation commands

```powershell
# Device
adb devices
# if needed: adb connect 100.99.129.110:5555

# Focused / final (always --no-daemon)
.\gradlew --no-daemon :app:compileDebugKotlin
.\gradlew --no-daemon test
.\gradlew --no-daemon :app:installDebug

# Cold protocol (final only, 3×)
.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache --no-configuration-cache --quiet
```

Do **not** default `:app:lintDebug` unless user asks or change is lint-high-risk.

## Review gates

| Gate | Condition |
|------|-----------|
| G1 | No stage-6 until Plan P0–P4 all resolved |
| G2 | Skip without examination steps logged → reject |
| G3 | trellis-check after implementation batches |
| G4 | Report claims ⊆ measured/documented evidence |

## Rollback points

- RP1: after each candidate before commit
- RP2: after bad commit → revert commit
- RP3: after failed remeasure → do not claim improvement

## Definition of done

- [x] Plan.md fully processed
- [x] REPORT revised and honest
- [x] Metrics remeasured
- [x] Quality check passed for code changes
- [x] User informed of residual unmet targets (e.g. -20% build if still unmet)
