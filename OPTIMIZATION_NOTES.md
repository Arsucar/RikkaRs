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
- B3 (first round): thin skip without full public-surface audit — **superseded by B3-R** in the Plan resume round.
- B4 `mavenLocal()`: reconfirmed still present in `settings.gradle.kts` `dependencyResolutionManagement.repositories`.
  Catalog coordinates are mostly public (Google/MavenCentral/JitPack). `sqlite-android` is version `-SNAPSHOT` via
  `com.github.rikkahub:sqlite-android`; some developer hosts may still need `mavenLocal()`. No resolution
  `--scan`/dependency-insight log proving zero local-only artifacts was produced in this session → skip removal.
- R2/R3 asynchronous Markdown/highlighting changes: no Macrobenchmark/Perfetto harness exists in the repository, and
  cursor/stale-result behavior is high risk without a protected UI test.
- R5 Room indexes: no production-scale database or `EXPLAIN QUERY PLAN` fixture was available; adding migrations based
  on inference alone was rejected.
- R6 image/TTS allocation changes: no memory/trace benchmark was available and provider/callback semantics are broad.
- Q2 dead-code removal: no item met the complete-reference and behavior-preservation threshold during this run.
- P3 native packaging/R8/resource changes: release/device compatibility evidence was insufficient; existing arm64 split
  and release shrinking were retained.

## Resume round — P0 examination (2026-07-21, task `07-21-resume-plan-apk-opt`)

Coverage matrix: `.trellis/tasks/07-21-resume-plan-apk-opt/research/coverage-matrix.md`.

### B3-R — `api` / `implementation` audit (accepted; check verified)

**Steps taken**

1. Listed all module `build.gradle.kts` and every `api(` / `implementation(project` edge.
2. Confirmed **no** `api(project(...))` module edges; graph is app-centric with `implementation` only.
3. For each third-party `api` dep, checked defining-module sources for usage and consumer imports of those packages.

**Confirmed non-public / unused re-exports (changed)**

| Change | Evidence |
|--------|----------|
| `common`: remove `api(okhttp-logging)` | No `okhttp3.logging` import under `common/src`; only app `DataSourceModule` uses `HttpLoggingInterceptor` |
| `common`: remove `api(kotlinx-datetime)` | Zero `kotlinx.datetime` under `common/src`; app uses datetime via `ai` API (`Message` timestamps) and own code |
| `common`: remove `api(commons-text)` | Zero commons-text under `common/src`; `ai` GoogleProvider + app `StringUtils` use it |
| `common`: remove `api(floatingx)` / `api(floatingx-compose)` | Zero FloatingX under `common/src`; only `app/.../FloatingWindow.kt` uses `com.petterp.floatingx` (core, not compose artifact) |
| `ai`: remove `api(okhttp-logging)` | No logging interceptor usage under `ai/src` |
| `ai`: add `implementation(commons-text)` | Needed after common stopped re-exporting; GoogleProvider imports `StringEscapeUtils` |
| `search`: `api(jsoup)` → `implementation` | Only `BingSearchService` uses Jsoup; public search types do not expose `org.jsoup` |
| `highlight`: `api(quickjs)` → `implementation` | `Highlighter` public API returns `HighlightToken`; QuickJS is private field |
| `app`: add direct `implementation` for `okhttp-logging`, `floatingx`, `jsoup` | Replace lost transitive compile classpath after demotions |

**Retained as `api` (public surface)**

- `common`/`ai`: `okhttp`, `okhttp-sse`, serialization, coroutines (and `ai` datetime) — appear in provider/manager/message signatures.
- `common` `quickjs`: `QuickJSContext.injectFetch` is a public extension used by `search` CustomJsSearchService.
- `web` ktor `api` set: app Web routes import `io.ktor.server.*` types from the web entrypoint.

**Not changed (risk)**

- Demoting `ai` okhttp `api` would force every provider consumer to declare okhttp; app already does, but other modules may rely on transitive compile — left as intentional API of `:ai`.
- `web` ktor `api` kept; app routing code is tightly coupled to server types.

**Validation (final check agent, 2026-07-21)**

- `.\gradlew --no-daemon :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (~1m 36s); no missing-import fixes required
- `.\gradlew --no-daemon test` → **BUILD SUCCESSFUL** (~1m 10s); 960 tests, 0 failures, 0 errors, 3 skipped
- `.\gradlew --no-daemon :app:installDebug` → installed on `ebc3de22` (PJF110 / Android 16); offline `100.99.129.110:5555` skipped by Gradle
- Cold×3 wall-clock for B3-R deferred to **stage 6** (no build-time claim in this check)

### B5 — kapt → KSP (reviewed-skip / N/A)

**Steps taken**

1. Grep entire repo for `\bkapt\b`, `kotlin-kapt`, `annotationProcessor` in Gradle/TOML/sources → **zero matches**.
2. Inventory processors: only `ksp(libs.androidx.room.compiler)` in `app/build.gradle.kts` with `ksp { arg("room.schemaLocation", ...) }`.
3. Root `build.gradle.kts` declares `libs.plugins.ksp apply false`; app applies `alias(libs.plugins.ksp)`.
4. `settings.gradle.kts` had a leftover ObjectBox plugin resolution branch (unrelated to kapt; removed under B6).

**Cost/benefit**

| Option | Cost | Benefit |
|--------|------|---------|
| Migrate kapt → KSP | N/A — no kapt | N/A |
| Add detekt/ktlint for “migration hygiene” | Out of scope (Prompt: do not add tools for metrics) | N/A |

**Decision**: document N/A; no code change for processors.

### B6 — unused plugins / deps / tasks (partial implement)

**Steps taken**

1. Grep `objectbox` / `ObjectBox` across sources → only `settings.gradle.kts` resolutionStrategy (no plugin applied, no deps).
2. Removed dead ObjectBox `resolutionStrategy.eachPlugin` block from `settings.gradle.kts`.
3. Catalog entries with **no** `libs.` consumer in any `build.gradle.kts`: `dom4j`, `sqlite-vector`, `androidx-navigation2` (nav2), `ktor-server-caching-headers`, `leakcanary-android` (commented only). These do not download until referenced; removing them is catalog hygiene only, not a measured build win → **not deleted** this round (avoids noisy TOML churn without profile).
4. `app` `fileTree(libs/*.jar|*.aar)`: `app/libs` directory does not exist; empty fileTree is a no-op. Left as intentional local-binary hook.
5. `web:buildWebUi` still required by `preBuild` (B2 already validated UP-TO-DATE behavior).
6. No unused Android `plugins { }` applications found beyond legitimate module needs (compose/serialization/ksp/baselineprofile).

**Decision**: ship ObjectBox dead-config removal only; leave catalog orphans documented.

### Profile method note (Plan P0 last bullet)

- B1 already used the cold protocol (median 244.862 s, -4.62%).
- B3-R / B5 / B6: no wall-clock claim in this implement dispatch; profile N/A until final check runs cold×3 after compile green.
- No new accepted B* candidate claims improvement without the same 3-sample protocol.

## Resume round — P1 runtime examination (2026-07-21, task `07-21-resume-plan-apk-opt`)

Implement agent only; **no Gradle** in this dispatch. Compile/install owned by final check.

### Plan: Compose stability / keys / recomposition (partial implement R7)

**Steps taken**

1. Grep chat UI for `LazyColumn`/`LazyRow`/`items(`/`key =`.
2. Read `ChatList.kt` (message list already uses `key = { index, item -> item.id }` and nested `key = node.id`).
3. Read `ConversationList.kt` (Paging `itemKey` for date/pinned/conversation IDs).
4. Read `ChatDrawer.kt` folder/assistant sheets and folder chips: `items(folders)` / `items(activeAssistants())` **without** keys.
5. Read `ChatList.kt` `ChatSuggestionsRow`: `items(conversation.chatSuggestions)` without keys.
6. Spot-check non-chat lists (`BuiltinToolUIs`, settings): many static/short lists still keyless; only hot chat paths changed this round.

**Findings**

| Location | Status |
|----------|--------|
| Chat message list / conversation paging | Already keyed |
| Chat drawer folders / move-assistant sheet | **Fixed**: stable entity-ID keys |
| Chat suggestions | Reviewed-skip: `List<String>` has no stable ID and may contain duplicates; positional identity retained |
| `MarkdownBlock` parse | Off-main via `flowOn(Dispatchers.Default)` after first composition |
| `MarkdownNew` Jsoup | `remember(html)` still on composition thread after HTML ready (R2 residual) |
| `HighlightCodeVisualTransformation` | `runBlocking` on UI (R3) |

**Implemented (R7)**

- `ChatDrawer.kt`: `items(..., key = { it.id })` for folders (×2) and assistants.
- `ChatList.kt`: no explicit suggestion key. Final review rejected `index + text` because insertion/reorder changes the key,
  while text-only keys can collide for valid duplicate suggestions. A model-level ID is required before adding a key.

**Skipped high-risk**

- R2 full async first-frame Markdown/Jsoup: initial `remember { parseMarkdown(content) }` / `Jsoup.parse` still on first composition; moving requires blank-frame / race handling + device trace.
- R3 remove `runBlocking` from `HighlightCodeVisualTransformation.filter` (`HighlightCodeBlock.kt:519`): VisualTransformation API is synchronous; async would need different architecture (precomputed spans / deferred transform) and cursor/offset tests.

### Plan: Room N+1 / indexes / main-thread DB (R5-R reviewed-skip)

**Steps taken**

1. Inventory `@Entity` indices under `data/db/entity/`.
2. Read `ConversationDAO`, `GenMediaDAO`, `MessageNodeEntity` indices.
3. Grep `allowMainThreadQueries` → **zero** in app main.
4. Grep DAO usage patterns: list UIs use `LightConversationEntity` projections and `PagingSource`; full `SELECT *` retained for detail/search-all-flow paths.

**Entity index coverage**

| Entity | Indexes | List/sort columns used without index |
|--------|---------|--------------------------------------|
| ConversationEntity | **none** | `assistant_id`, `is_pinned`, `update_at`, `folder_id`, `title LIKE` |
| GenMediaEntity | **none** | `type`, `create_at`, prompt/model LIKE |
| MessageNodeEntity | `conversation_id` | OK for per-conversation nodes |
| Tags/hooks/memory tables | Present | Not the R5 hotspot |
| MemoryEntity | none | Small table; filtered by assistant_id in repo |

**N+1**

- No classic per-row secondary query in ConversationDAO list paths; message nodes loaded by conversation id with index.
- Hook/memory table committers use `@Transaction` and explicit DAO calls (service layer, not UI hot path).

**Decision**: do **not** add migrations without `EXPLAIN QUERY PLAN` on production-scale DB. Residual risk: large conversation counts may full-scan ORDER BY; mitigate later with measured indexes + migration tests.

### Plan: OkHttp interceptors / reuse (R8 reviewed-skip)

**Steps taken**

1. Grep all `OkHttpClient.Builder` / `OkHttpClient(` in main sources.
2. Read `DataSourceModule` app singleton: connect 20s / read 10min / write 120s; Accept-Language + UA; Content-Type network fix; RequestLogging; AIRequest; HttpLogging HEADERS; then `SearchService.init(it)`.
3. `McpManager`: private builder same timeouts, **no** interceptors; used for MCP SSE/HTTP isolation.
4. `speech/.../*TTSProvider.kt`: each provider owns a private client (typically long read timeout) — not DI-injected.
5. Tests construct bare `OkHttpClient()` — N/A for production.

**Findings**

- App path correctly uses **one** shared client for AI/search (connection pool reuse).
- TTS multiplicity is structural (module boundary, no app OkHttp injection into speech providers). Unifying would need speech API redesign + timeout policy agreement.
- MCP private client avoids leaking AI logging interceptors into MCP traffic — intentional.

**Decision**: no client merge this round. Residual: TTS creates N pools; acceptable without connection-churn metrics.

### Plan: Coil reuse / transforms (R7 partial implement)

**Steps taken**

1. Read `RouteActivity` `setSingletonImageLoaderFactory`: single ImageLoader, crossfade, OkHttp fetcher with app `okHttpClient`, CacheControl strategy, GIF/Animated/SVG decoders.
2. Read `ZoomableAsyncImage`: rebuilt `ImageRequest` every composition → **remembered**.
3. Read `AIIcon`: already `remember(path, contentColor, context)` for ImageRequest.
4. Spot-check `Favicon`/`Export`/`ImgGenPage`: mostly pass model URLs/strings to AsyncImage (Coil caches by data); Export builds one-shot request for share path.

**Decision**: only ZoomableAsyncImage request identity fix. No disk-cache size / transform pipeline changes without image-load metrics.

### Plan: main-thread IO / JSON / DataStore/Flow

**Steps taken**

1. Grep `runBlocking` in `app/src/main` → 3 sites: `ChatService` subagent workspace factory (suspend-context bridge), `HighlightCodeBlock` (R3), tests N/A.
2. Confirm R1 `MarkdownWeb.kt` WeakHashMap asset template cache (`9e225402`).
3. Confirm R4 LRU key roulette in-memory JSON cache (`fe91967d`).
4. Grep `settingsFlow.collectAsStateWithLifecycle` / `collectAsState`: page-scoped (RouteActivity, TTS/ASR hooks, settings pages, ImgGen tabs). No global multi-collector storm found; Settings hook centralizes common UI path.
5. File IO in UI: WorkspaceCwdPicker / ChatMessage / skills VMs use `Dispatchers.IO`.

**Decision**: no new code beyond R7. R6 Base64/TTS full-buffer reads remain skip (Export encode path and provider response bodies need allocation benchmarks).

### Plan: evidence-only implements

| Candidate | Action | Verification boundary |
|-----------|--------|------------------------|
| R7 entity-ID keys + Coil remember | Implemented | Behavior-compatible; suggestions remain positional because they have no stable ID; no FPS claim; needs `:app:compileDebugKotlin` + install if app path exercised |
| R1/R4 | Prior accepted | Do not re-implement; SHAs cited |
| R2/R3/R5-R/R6/R8 | Evidence-rich skip | See above examination steps |

## Quality gaps

- detekt and ktlint were not configured at baseline, so their warning target is N/A; no new analyzer was introduced.
- The one final `:app:lintDebug` gate found 180 errors, 61 warnings, and 1 hint (with additional baseline-filtered
  findings). The first error was an existing `LocalContextGetResourceValueCall` at `ChatInput.kt:566`. The baseline was
  not regenerated and release workflows were not changed.
- Runtime candidates R1 and R4 have mechanism/regression tests but no numeric frame-time, I/O, or allocation delta;
  the final report does not claim one.

## Resume round — P2/P3/P4 examination (2026-07-21, task `07-21-resume-plan-apk-opt`)

Implement agent only; **no Gradle** in this dispatch. Stage 6 left unchecked. Compile/assemble/test/cold×3/report
revision owned by final check / parent after this batch.

### Stage 3 — P2 quality & module boundary

#### Q1-R — detekt / ktlint (N/A)

**Steps taken**

1. Grep repo for `detekt` / `ktlint` in `*.gradle.kts`, `*.toml`, `*.yml`, docs, Trellis artifacts.
2. Hits only in Plan/Prompt/NOTES/REPORT (metric discussion) and archive task docs — **no** plugin id, dependency,
   config file (`.detekt*`, `ktlint*`), or CI job.
3. `libs.versions.toml` has no detekt/ktlint version or plugin alias.
4. Prompt forbids adding tools solely to satisfy the warning-reduction metric.

**Decision**: Plan item resolved as **N/A**; do not introduce detekt/ktlint. Android Lint remains separate debt
(180 errors historically; baseline not regenerated this round).

#### Q2-R — dead code / unused refs

**Steps taken**

1. Grep `@Deprecated`, `@file:Suppress("unused")`, TODO-remove patterns under main sources.
2. Inspected `UIMessagePart.Search` / `ToolCall` / `ToolResult` (`Message.kt`): still referenced in
   `toSortedMessageParts`, merge paths, and serialization (`@SerialName`) for historical messages — **not** deletable.
3. `ImageUtils` / `DataTable`: suppress-unused file annotations but **live call sites** (SettingProvider, AssistantImporter,
   ImgGen, ChatPage; Markdown/SimpleHtml/MemoryTable).
4. Drawable full scan of `app/src/main/res/drawable*` (13 files) against `app/src` `*.kt`/`*.xml`:
   - **Zero refs**: `patreon.xml` only → deleted as **A4**.
   - All others ≥1 ref (donate kofi/afdian, provider icons, placeholders, etc.).
5. No private top-level function with zero references was confirmed safe for bulk delete without compiler-assisted
   unused analysis (not run; implement has no Gradle).

**Decision**: ship only `patreon.xml` removal; leave deprecated message parts and live utilities.

#### Q3 — duplicate implementations

**Steps taken**

1. Compared `MarkdownBlock` vs `MarkdownNew`: public `MarkdownBlock` delegates to `MarkdownNew` when enabled —
   facade/migration path, not copy-paste duplication.
2. OkHttp client multiplicity already reviewed under R8 (app singleton + TTS/MCP isolation).
3. No third 80%-similar component pair selected for merge without abstraction cost.

**Decision**: no merge this round.

#### Q4 — module coupling

**Steps taken**

1. Grep `api(project` across `*.gradle.kts` → **zero** matches (reconfirm after B3-R).
2. `settings.gradle.kts` modules: app, highlight, ai, search, speech, common, document, web, material3, workspace,
   app:baselineprofile.
3. Project graph remains app-centric `implementation` star; libraries do not re-export sibling modules via `api(project)`.
4. Residual cost is intentional (app owns product composition). Mass module split out of scope and high risk.

**Decision**: no further graph surgery beyond B3-R third-party demotions already staged.

#### Focused tests for refactors (Plan P2 last bullet)

- No behavior refactor in P2 beyond A4 resource delete.
- R7 (prior dispatch) is entity-ID key/`remember` only; suggestion pseudo-key was removed in final review → optional UI test skipped.
- R1/R4/T2 already carry mechanism tests from first round.

### Stage 4 — P3 resources / R8 / startup

#### A4 — unused resources (partial implement)

| Resource | Refs | Action |
|----------|------|--------|
| `drawable/patreon.xml` | 0 (lint-baseline also flagged unused) | **Deleted** |
| Other 12 drawables | ≥1 each | Keep |
| `assets/simple_dict/*` (jieba) | runtime NLP | Keep (~11+ MB total) |
| `assets/banner/banner-*.png` | UI banners | Keep (~1.7 MB each) |

**Validation (final check)**: resource linking via `compileDebugKotlin` + `installDebug` (packageDebug) **PASS**.
APK exact bytes/SHA-256 remeasure deferred to stage 6 cold protocol; size gate still ≤5% vs 82,140,247 baseline.

#### A3-R — R8 / ProGuard keep review

**`app/proguard-rules.pro` patterns**

| Pattern | Purpose |
|---------|---------|
| `-keepattributes SourceFile,LineNumberTable` | Stack traces |
| `-keep @kotlinx.serialization.Serializable class *` | Kotlinx serialization |
| `-keep class org.scilab.forge.jlatexmath.**` | Math rendering |
| `-dontwarn com.google.re2j.**` | Optional RE2J |
| `-dontobfuscate` | Debuggable release mapping preference |
| `-dontwarn java.lang.management.*` / `java.beans.*` | JVM-only APIs on Android |
| `-keepattributes Signature, InnerClasses, EnclosingMethod` + jackson/auth0 keep | JWT/Jackson TypeReference |

**Library consumer-rules**: empty (0 chars) for ai/common/highlight/search/speech/web/workspace/material3;
`document/consumer-rules.pro` keeps `com.artifex.mupdf.**`.

**Release config**: `isMinifyEnabled = true`, `isShrinkResources = true`, default optimize + app rules.

**Decision**: no keep trim without release-variant install + crash/smoke proof.

#### A2-R — packaging / bitmaps / shrink

**Steps taken**

1. `app/build.gradle.kts` packaging: `jniLibs.useLegacyPackaging = true`, `pickFirsts += "lib/*/libtermux.so"`.
2. Debug does not enable minify/shrink; size gate measured on debug arm64 APK per Plan/Prompt protocol.
3. Large assets inventoried (see A4 table); compression of jieba dicts would need encoding/runtime proof.
4. A2 original skip on flipping `useLegacyPackaging` reconfirmed (workspace/native loader risk).

**Decision**: no packaging flag change; no banner recompress this round.

#### A5 — baseline profile / startup hot path

**Present**

- Plugin: root `alias(libs.plugins.baselineprofile) apply false`; app applies plugin +
  `baselineProfile(project(":app:baselineprofile"))`.
- Generator: `app/baselineprofile/.../BaselineProfileGenerator.kt`, `StartupBenchmarks.kt`.
- Generated: `app/src/release/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`
  (~5,178,576 bytes each).

**Decision**: do **not** regenerate or edit profiles without connected device +
`generateReleaseBaselineProfile` / benchmark workflow; startup path code changes out of scope without Macrobenchmark.

### Stage 5 — P4 tests

| Item | Result |
|------|--------|
| New regression tests for resume-round changes | **Skip** with reason: B3-R Gradle-only; R7 entity-ID key/Coil identity UI; A4 drawable delete |
| Affected module tests (for final check) | `:app:compileDebugKotlin`; full `test`; optional focused app tests; modules: common, ai, search, highlight, app |
| CI / AndroidTest policy | `.github/workflows` clean this round; no default `connectedDebugAndroidTest` |

### Plan coverage after this dispatch

| Stage | Status |
|-------|--------|
| 0, P0, P1 | Checked prior + residual done |
| P2 (5) | Checked this dispatch |
| P3 (4) | Checked this dispatch |
| P4 (3) | Checked this dispatch |
| Stage 6 (6) | **Still unchecked** — main session remeasure + REPORT revise |

### Code change this dispatch

- Deleted: `app/src/main/res/drawable/patreon.xml`
- Docs: `Plan.md`, `OPTIMIZATION_PLAN.md`, `OPTIMIZATION_NOTES.md`, task `research/coverage-matrix.md`
- Prior uncommitted product code (B3-R, R7, B6) entered final check; the invalid suggestion pseudo-key was later removed

## Final check agent verification (2026-07-21)

| Gate | Result |
|------|--------|
| Spec/code review of B3-R demotions | OK — direct app deps cover HttpLoggingInterceptor, FloatingX, jsoup; ai keeps commons-text |
| R7 Compose keys + Coil remember | Corrected after review — ChatDrawer uses entity IDs; suggestions retain positional identity until the model has stable IDs |
| A4 patreon delete | OK — zero product refs; obsolete lint-baseline entry removed during final review |
| `:app:compileDebugKotlin --no-daemon` | PASS (~1m 36s) |
| `test --no-daemon` | PASS; 960 tests, 0 failures, 0 errors, 3 skipped |
| `:app:installDebug --no-daemon` | PASS on `ebc3de22` (PJF110 API 16); skipped offline `100.99.129.110:5555` |
| Code fixes by check agent | **None required** |
| Stage 6 cold×3 / REPORT | Completed by main session — see below |

Final review fixes were revalidated with `:app:compileDebugKotlin test` (PASS, 222 tasks) and
`:app:installDebug` (PASS on `ebc3de22`). The obsolete Patreon lint-baseline entry and suggestion pseudo-key were
removed before the archive work commit.

## Stage 6 remeasure (main session, 2026-07-21)

Cold protocol (same as baseline):
`.\gradlew --no-daemon clean assembleDebug --profile --no-build-cache --no-configuration-cache --quiet`

| Sample | Wall (s) |
|--------|----------|
| 1 | 252.749 |
| 2 | 251.119 |
| 3 | 250.572 |
| **Median** | **251.119** |

- Baseline median: 256.737 s → **−2.19%** (target −20% **unmet**)
- First-round partial median 245.547 s is **not** used as resume final; host noise + B3-R/R7/A4 churn can move wall clock
- APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
  - Bytes: **82,133,445** (baseline 82,140,247 → **−6,802 / −0.008%**; ≤5% **met**)
  - SHA-256: `37754831F6E0D064466B99C7CE33131B03F296AC175BB5C242594BC3BAAAF56D`
- Check gates already green: compile, 960 tests, install on `ebc3de22`
- `OPTIMIZATION_REPORT.md` rewritten as Plan-resume report with coverage matrix
- Git commit pending until the archive work commit is created
