# RikkaRs optimization plan

Authoritative constraints and success criteria live in `Prompt.md`. This file is the live candidate ledger; a candidate
is accepted only after evidence, comparable measurement, focused verification, `assembleDebug`, and documentation.

## Measurement baseline

- Start: `release/rikka-arsucar` at `a07d71b3b634d3f97d519d693b58f8174ff083b5`.
- Cold protocol: `.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache
  --no-configuration-cache --quiet`, three serialized samples on the same host.
- Cold wall samples: 259.474 s, 255.280 s, 256.737 s; median 256.737 s.
- Identical APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, arm64-v8a debug, 82,140,247 bytes,
  SHA-256 `602F0A34848D96A889E21B993176624F5238EC7F849934D1AA747C89BDA0AB95`.
- JVM tests: all repository `test` tasks passed at baseline.
- detekt/ktlint: N/A; neither task nor configuration existed at baseline. Android Lint is tracked separately.

## Candidate ledger

| ID | Priority | Candidate and evidence | Expected mechanism | Risk | Validation | Status / result |
|----|----------|------------------------|--------------------|------|------------|-----------------|
| B1 | P0 | Enable Gradle parallel execution; ten modules, setting disabled in `gradle.properties` | Overlap independent module compilation/resource/native tasks | Medium: RAM contention or coupled tasks | Same 3-sample cold protocol, tests, assemble | Accepted: median 244.862 s, -4.62%; APK unchanged; install passed |
| B2 | P0 | Verify `web:buildWebUi`; wired to every `preBuild`, but baseline observed UP-TO-DATE | Avoid external pnpm work only if incrementality is broken | Medium: stale embedded UI | Two `:web:preBuild --info` runs and input-change check | Skipped: both runs UP-TO-DATE (22 s / 10 s) |
| B3 | P0 | Audit `api`/`implementation` and unused build dependencies/plugins | Reduce compile classpaths and artifact transforms | Medium/high compatibility | Dependency insight, consumer search, focused tests, cold median/APK | Pending |
| B4 | P0 | Evaluate `mavenLocal()` resolution cost only if no local artifact is consumed | Avoid local metadata lookup and improve reproducibility | High: local/private dependency breakage | Dependency resolution evidence and clean build | Pending evidence |
| R1 | P1 | Cache `MarkdownWeb` HTML template; current function rereads asset per preview | Remove repeated UI-path disk read and invariant template allocation after first use | Low/medium | Focused per-key cache tests, assemble, app install | Accepted: 2 tests pass; assemble/install pass; no Perfetto delta claimed |
| R2 | P1 | Initial Markdown/HTML parsing and Jsoup parsing occur during composition | Move expensive parse off UI thread, reduce first-frame stalls | High: blank/stale content race | Focused state tests plus trace/device long-message check | Pending evidence |
| R3 | P1 | Syntax visual transformation uses `runBlocking` for highlighting | Remove main-thread blocking during editing/recomposition | High: offsets/cursor/stale results | Editor behavior tests and device frame trace | Pending evidence |
| R4 | P1 | LRU key roulette reads/parses/writes cache under global lock per request | Remove repeat read/JSON parse per cache file while retaining synchronous writes and fairness | Medium: crash persistence/concurrency | 3 deterministic tests including 8-thread fairness, assemble, app install | Accepted: tests/assemble/install pass; no wall-time delta claimed |
| R5 | P1 | Conversation/media DAO sort/filter columns have no explicit indexes | Avoid scan/temp sort at large row counts | Medium: migration/write/storage cost | Real-scale `EXPLAIN QUERY PLAN`, migration and DAO tests | Pending evidence |
| R6 | P1 | Image Base64 encoding and TTS callback reads create large in-memory copies | Reduce peak allocation/callback blocking | Medium/high provider semantics | Focused benchmark/memory trace and provider tests | Pending evidence |
| Q1 | P2 | Existing Android Lint baseline includes actionable issues | Reduce known quality/resource debt without adding tools | Low/medium localization/resource risk | One applicable lint run, assemble, APK comparison | Pending |
| Q2 | P2 | Search for confirmed dead code and compile-classpath leaks | Reduce code/APK/maintenance only when unreachable | Medium | Full reference search, tests, assemble, APK diff | Pending |
| A1 | P3 | Analyze APK root/dex, assets, native libs, resources | Select size work from actual largest entries | Low for analysis | Exact zip/APK Analyzer breakdown | Pending |
| A2 | P3 | `useLegacyPackaging=true` for native libraries | Potential packaging/install-size improvement | High: native/workspace loader behavior | Identical APK diff and device terminal/workspace smoke test | Pending evidence |
| A3 | P3 | Review R8 keep rules/resources/baseline profile | Remove over-keep or improve startup only with release/device proof | High: reflection/startup regressions | Same variant analysis, install and startup verification | Pending evidence |
| T1 | P4 | Add regression tests for accepted behavior changes | Prevent optimization regressions | Low | Affected module test tasks and final `test` | Pending |
| T2 | P4 | Replace leaking temp-dir setup / real-time test waits when evidence supports it | Improve deterministic cleanup and reduce flakiness | Low/medium test semantics | Repeated focused test execution | Pending |

## Rejection rules

- Reject build candidates whose comparable three-sample median does not improve beyond normal baseline variance or that
  worsen reliability/memory without compensating evidence.
- Reject runtime/APK candidates without an executable bottleneck measurement or compatibility proof.
- Restore only the current uncommitted candidate after failure. Use a separate exact revert commit for committed work.
- After three same-cause failures, record the candidate in `OPTIMIZATION_NOTES.md` and move on.
