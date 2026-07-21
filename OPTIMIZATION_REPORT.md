# RikkaRs overnight optimization report (Plan resume)

Date: 2026-07-21
Branch: `release/rikka-arsucar`
Starting commit: `a07d71b3b634d3f97d519d693b58f8174ff083b5`
Resume task: `.trellis/tasks/07-21-resume-plan-apk-opt`
Final work commit: `b70b1ec5` (no push)

## Outcome (honest)

### Process correction

The first overnight pass completed B1, R1, R4, T2 and then incorrectly treated the run as finished while most `Plan.md` P0–P4 examination items remained unchecked. The premature `OPTIMIZATION_REPORT.md` and “目标已完成” narrative were wrong: **`4.36%` cold-build improvement was a first-round partial result**, not full Plan coverage.

This resume round re-opened from **P0 item 3** (`api`/`implementation`) and processed every remaining Plan bullet with either an implementation or an evidence-rich skip. Stage 6 metrics below replace the first-round “final” numbers for cold build and APK.

### Target status

| Goal | Status |
|------|--------|
| Cold build ≤ −20% vs baseline median 256.737 s | **Improve but unmet** — resume median 251.119 s (**−2.19%**) |
| APK growth ≤ 5% vs baseline 82,140,247 B | **Met** — final 82,133,445 B (**−0.008%**) |
| JVM tests all non-skipped pass | **Met** — 960 tests, 0 fail, 0 error, 3 skipped |
| detekt/ktlint −50% warnings | **N/A** — tools absent at baseline; not added |
| Plan.md P0–P4 examination complete | **Met** (this resume) |
| Device install after app changes | **Met** — `ebc3de22` installDebug |

## Metrics

| Metric | Baseline | First-round “final” (partial Plan) | Resume stage-6 final | Delta vs baseline / status |
|--------|----------|-------------------------------------|----------------------|----------------------------|
| Cold `clean assembleDebug --profile` wall median | 256.737 s (259.474 / 255.280 / 256.737) | 245.547 s (−4.36%) | **251.119 s** (252.749 / 251.119 / 250.572) | **−2.19%**; target −20% **unmet** |
| Gradle parallel-only (B1) | 256.737 s | 244.862 s (−4.62%) | (retained; not re-isolated) | Accepted independently in first round |
| APK `app-arm64-v8a-debug.apk` | 82,140,247 B; SHA-256 `602F0A34…BDA0AB95` | 82,141,867 B (+0.002%) | **82,133,445 B**; SHA-256 `37754831F6E0D064466B99C7CE33131B03F296AC175BB5C242594BC3BAAAF56D` | **−6,802 B (−0.008%)**; ≤5% **met** |
| JVM tests | PASS | 960 / 0 / 0 / 3 | 960 / 0 / 0 / 3 PASS (`--no-daemon test`) | **Met** |
| detekt/ktlint | N/A | N/A | N/A | N/A |
| Android Lint | baseline present | 180e/61w failed | not re-run this resume | gap recorded; baseline not rewritten |
| Device install | — | PASS `ebc3de22` | PASS `ebc3de22` (`:app:installDebug`) | **Met** |

Cold command (all samples):
`.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache --no-configuration-cache --quiet`

APK path: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (arm64-v8a debug).

**Note on cold variance:** Resume median (251.119 s) is slightly *slower* than the first-round partial median (245.547 s) on the same host protocol. That does not invalidate B1’s earlier isolated −4.62% experiment; host noise and additional classpath/source churn from B3-R/R7/A4 can move wall clock within noise. **No −20% claim is made.**

## Plan coverage matrix

| Stage | Items | Resolution |
|-------|------:|------------|
| 0 Baseline | 7/7 | Complete (first round) |
| P0 Build | 6/6 | B1 accepted; B3-R demotions; B5 kapt N/A; B6 ObjectBox dead config; profile protocol documented |
| P1 Runtime | 6/6 | R1/R4 prior; R7 entity-ID keys + Coil remember; suggestion key reviewed-skip; R2/R3/R5-R/R6/R8 evidence-rich skip |
| P2 Quality | 5/5 | detekt/ktlint N/A; A4 patreon drawable; coupling/dup/tests documented |
| P3 Resources/R8 | 4/4 | A4 unused drawable; R8/packaging/baseline profile reviewed-skip |
| P4 Tests | 3/3 | No new forced tests for entity-ID key/Coil identity/Gradle-only changes; final test run by check; no CI/AndroidTest |
| Stage 6 Finish | 6/6 | Remeasured; assemble+test+install; NOTES/REPORT revised; work commit `b70b1ec5` |

## Accepted work (commits + resume uncommitted)

### First-round commits (retained)

- `de7d45e8` `docs: record optimization baseline and plan`
- `a8575336` `perf(build): enable parallel Gradle execution`
- `9e225402` `perf(app): cache markdown preview template`
- `fe91967d` `perf(ai): cache LRU key roulette reads`
- `a3316a88` `test(workspace): clean temporary scanner directories`
- `e54e7e92` premature report (superseded by this document)

### Resume-round changes

| ID | Change | Check |
|----|--------|-------|
| B3-R | Demote non-public `api` deps in common/ai/search/highlight; app direct deps | compile + test + install PASS |
| B6 | Remove dead ObjectBox `resolutionStrategy` | settings-only |
| R7 | ChatDrawer entity-ID keys; `ZoomableAsyncImage` remember ImageRequest; suggestion pseudo-key removed after review | compile + install PASS |
| A4 | Delete unused `drawable/patreon.xml` | resource only |

## Skipped with examination evidence

See `OPTIMIZATION_NOTES.md` (P0/P1/P2–P4 resume sections). Highlights:

- **B4** `mavenLocal()` kept (SNAPSHOT/local resolution risk)
- **B5** zero kapt; Room already KSP
- **R2/R3** async Markdown / remove `runBlocking` highlight — no Macrobenchmark; high UI risk
- **R5-R** Room indexes — no EXPLAIN fixture
- **R6/R8** allocation / multi OkHttp — no harness; intentional isolation
- **A2/A3/A5** packaging/R8 trim/baseline regen — release/device proof insufficient
- **Q1-R** detekt/ktlint N/A

## Recommendations

1. Add Macrobenchmark/Perfetto before R2/R3/R6.
2. Separate lint cleanup task (180 errors) before baseline rewrite.
3. Revisit Room indexes only with production-scale `EXPLAIN QUERY PLAN`.
4. Treat cold-build −20% as **still open**; further wins need larger structural candidates (not more thin demotions).

## Verification log (resume final check)

- `.\gradlew --no-daemon :app:compileDebugKotlin test` — PASS after final review fixes; 960 tests, 0 failures, 0 errors, 3 skipped
- `.\gradlew --no-daemon :app:installDebug` — PASS after final review fixes on `ebc3de22`
- Cold ×3 — 252.749 / 251.119 / 250.572 s; median **251.119 s**
