# RikkaRs overnight optimization report

Date: 2026-07-21
Branch: `release/rikka-arsucar`
Starting commit: `a07d71b3b634d3f97d519d693b58f8174ff083b5`
Final report commit: pending (`docs: add overnight optimization report`)

## Outcome

The run produced four independently reversible optimization commits plus baseline planning documentation. The build
speed and APK-size gates are measured honestly: APK growth stayed below the 5% limit, while the 20% cold-build reduction
target was not reached. JVM tests and app installation passed. detekt/ktlint is N/A because neither tool existed at
baseline. Android Lint remains a known repository quality gap and failed its final gate; its baseline was not rewritten.

## Metrics

| Metric | Baseline | Final | Delta / status |
|--------|----------|-------|----------------|
| Cold `clean assembleDebug --profile` wall median | 256.737 s (259.474 / 255.280 / 256.737) | 245.547 s (240.570 / 245.547 / 252.214) | -4.36%; target -20% unmet |
| Gradle parallel-only experiment | 256.737 s baseline median | 244.862 s (241.007 / 244.862 / 248.219) | -4.62%; accepted independently |
| APK `app-arm64-v8a-debug.apk` | 82,140,247 bytes; SHA-256 `602F0A34848D96A889E21B993176624F5238EC7F849934D1AA747C89BDA0AB95` | 82,141,867 bytes; SHA-256 `3661534C85491023590C9C290E5E7A47C8DFBA3979CB88E0A48FF820380FFCC2` | +1,620 bytes (+0.002%); target <=5% met |
| JVM tests | PASS; baseline `test` | 960 tests, 0 failures, 0 errors, 3 skipped; final `assembleDebug test` PASS | 100% non-skipped tests pass |
| detekt/ktlint | N/A; tasks/config absent | N/A | target not applicable |
| Android Lint | Existing baseline configured | 180 errors, 61 warnings, 1 hint; task failed | gap recorded; no baseline regeneration |
| Device install | `ebc3de22` available | `:app:installDebug` installed successfully; offline `100.99.129.110:5555` skipped | PASS |

Cold command for both sets: `.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache --no-configuration-cache --quiet`.
The APK path, variant, ABI, host, JDK, Gradle, and AGP remained fixed as recorded in `Documentation.md`.

## Accepted commits

- `de7d45e8` `docs: record optimization baseline and plan`
- `a8575336` `perf(build): enable parallel Gradle execution`
- `9e225402` `perf(app): cache markdown preview template`
- `fe91967d` `perf(ai): cache LRU key roulette reads`
- `a3316a88` `test(workspace): clean temporary scanner directories`

R1 removes repeated asset-template reads with a weak `AssetManager`-keyed cache and two focused tests. R4 keeps
synchronous persistence but avoids repeated read/JSON parsing per cache file, with persistence, provider-isolation, and
8-thread fairness tests. T2 cleans temporary directories after every workspace scanner test. None of the runtime changes
claims a numeric frame-time or allocation improvement without a profiler harness.

## Skipped candidates and gaps

See `OPTIMIZATION_NOTES.md` for candidate-level rationale. In short, dependency removal, Room indexes, asynchronous
rich-text work, native packaging/R8/resource changes, and unconfirmed dead-code removal lacked proportionate evidence or
carried compatibility risk. The final lint failure is pre-existing quality debt, not silently attributed to these commits.

## Recommendations

1. Add a maintained Macrobenchmark/Perfetto path before attempting R2/R3/R6, with long Markdown, editing, image, and TTS
   scenarios plus device-specific acceptance thresholds.
2. Establish a lint cleanup task separate from this optimization run; review the 180 un-baselined errors before changing
   the baseline.
3. Revisit B3/B4 and R5 only with dependency-resolution traces and production-sized Room query plans/migrations.
