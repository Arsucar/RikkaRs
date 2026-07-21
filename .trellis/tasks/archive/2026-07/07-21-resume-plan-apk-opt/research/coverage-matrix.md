# Coverage matrix — Plan.md vs prior work vs residual

Task: `07-21-resume-plan-apk-opt`  
Source of truth: repo-root `Plan.md`  
Prior accepted commits (do not re-implement): `a8575336` (B1), `9e225402` (R1), `fe91967d` (R4), `a3316a88` (T2)  
Premature report: `e54e7e92` (not Plan-complete evidence)

## Stage 0 — baseline

| Plan item | Prior status | Residual this resume |
|-----------|--------------|----------------------|
| Read guides / Prompt / Plan | Done (baseline docs `de7d45e8`) | None |
| Record env / git status | Done | None |
| Baseline assemble/test; detekt/ktlint N/A | Done | None |
| Cold×3 median | Done (256.737 s) | Remeasure only at stage 6 |
| APK path/ABI/bytes/SHA | Done (82,140,247) | Remeasure at stage 6 |
| Module/plugin/hotspot analysis | Done | Reuse for P0 audit |
| Create OPTIMIZATION_PLAN.md | Done | Update ledger rows as items resolve |

## Stage 1 — P0 build

| Plan item | Prior status | Residual this resume | Resolution (A+B dispatch) |
|-----------|--------------|----------------------|---------------------------|
| Gradle config/plugins/tasks review | Checked (first round) | Leave checked | — |
| configuration cache / build cache / parallel / JVM | B1 accepted `a8575336`; parallel=true | Leave checked | — |
| `api`/`implementation` leaks & unnecessary module deps | B3 thin skip (insufficient) | Full audit + fix confirmed leaks | **Implemented B3-R**: demote non-public `api`; move FloatingX to app; remove dead common deps |
| kapt → KSP / plugin migration | Not examined with inventory | Inventory + cost/benefit | **Reviewed-skip B5**: zero `kapt`; Room already on KSP |
| Remove confirmed unused plugins/deps/tasks | Partial narrative only | Reference search + remove only proven | **Implemented B6** (objectbox resolution dead code) + catalog orphans documented not removed |
| Each candidate same profile method | Only B1 profiled | For new B* this round: note main-session cold×3 | **Documented B3-R**: compile/test/cold×3 owned by final check; no multi-hour cold here |

## Stage 2 — P1 runtime

| Plan item | Prior status | Residual | Resolution (P1 implement dispatch) |
|-----------|--------------|----------|-------------------------------------|
| Compose stability/recomposition/keys | Thin skip | Code-backed review + low-risk keys | **R7 implement**: ChatDrawer entity-ID keys; ChatSuggestions reviewed-skip because `List<String>` has no stable ID and allows duplicates; R2/R3 skip with code cites |
| Room N+1 / indexes / main-thread DB | R5 thin skip | Strengthen evidence | **R5-R reviewed-skip**: entity/DAO inventory; no EXPLAIN; no migration |
| OkHttp interceptors/clients | Thin | Structured review | **R8 reviewed-skip**: singleton app client OK; TTS/MCP isolation intentional |
| Coil reuse/transforms | Thin | Structured review | **R7 implement**: remember ImageRequest in ZoomableAsyncImage; singleton loader OK |
| Main-thread IO / JSON / DataStore/Flow | R1/R4 done; R2/R3/R6 thin | Document SHAs + re-review | R1 `9e225402`, R4 `fe91967d` cited; R3/R6 skip strengthened |
| Evidence-only implements | Partial | Mechanism-safe only | R7 entity-ID keys + Coil remember this round; suggestion pseudo-key removed after final review |

## Stage 3 — P2 quality

| Plan item | Prior status | Residual | Resolution (P2–P4 implement dispatch) |
|-----------|--------------|----------|----------------------------------------|
| detekt/ktlint warnings | N/A claimed | Reconfirm N/A once | **Q1-R N/A**: no plugin/config/task in Gradle/TOML/CI; do not add tools |
| Dead code / unused refs | Q2 thin | Full reference search; no mass delete | **Q2-R + A4**: Kotlin no safe delete; deleted zero-ref `patreon.xml` |
| Merge duplication | Not done | Only clear wins | **Q3 skip**: Markdown facade; TTS/OkHttp intentional multi-path |
| Module coupling cost | Thin | Edges with compile/runtime proof | **Q4 skip**: zero `api(project)`; app star graph OK; B3-R already demoted 3p api |
| Tests for refactors | Partial T1 | Only if this round refactors | **T1-R skip**: no behavior refactor needing new unit tests |

## Stage 4 — P3 size/startup

| Plan item | Prior status | Residual | Resolution (P2–P4 implement dispatch) |
|-----------|--------------|----------|----------------------------------------|
| Unused resources | A1 analysis only | Reference + build proof before delete | **A4 implement**: drawable scan; delete `patreon.xml` only |
| R8/ProGuard keep | A3 thin skip | Keep-rule review artifact | **A3-R reviewed-skip**: keep inventory documented; no rule edit without release proof |
| Bitmap/vector/shrink/packaging | A2 skip | Re-review with size gate | **A2-R reviewed-skip**: keep legacy jni packaging; shrink already on release; large assets functional |
| Baseline profile / startup | baselineprofile module exists | Only if build+install+verify | **A5 reviewed-skip**: profiles already generated; no regen without device Macrobenchmark |

## Stage 5 — P4 tests

| Plan item | Prior status | Residual | Resolution (P2–P4 implement dispatch) |
|-----------|--------------|----------|----------------------------------------|
| Regression tests for optimizations | T1 via R1/R4; T2 done | Tests for **this round** if behavior risk | **T1-R skip**: B3-R Gradle-only; R7 entity-ID key/Coil identity UI; A4 resource-only |
| Affected module tests | Partial | After code changes | **T3 documented** for check: compileDebugKotlin + `test` + install; modules common/ai/search/highlight/app |
| No CI / no default AndroidTest | Policy | Keep | **T4 confirmed**: `.github/workflows` zero diff this round |

## Stage 6 — finish

| Plan item | Prior status | Residual |
|-----------|--------------|----------|
| Cold×3 + APK + static | Premature report | Full remeasure after P0–P4 resolved |
| Merge final Gradle work | Partial | Prefer single invocation |
| assembleDebug / test / install | First-round install OK | Repeat if app code changed |
| OPTIMIZATION_NOTES.md | Exists; needs resume evidence | Append P0 audit |
| OPTIMIZATION_REPORT.md | Premature (`e54e7e92`) | Revise with coverage matrix + honesty |
| Docs commit message | Pending user auth | Do not push |

## Module dependency graph (static)

```
app → ai, web, document, highlight, search, speech, common, material3, workspace
ai → common
search → ai, common
speech → common
highlight, document, web, material3, workspace → (no project deps)
```

All project edges use `implementation` (no `api(project(...))` leaks).

## `api(...)` inventory (pre-B3-R)

| Module | `api` deps | Public-surface justification |
|--------|------------|------------------------------|
| common | okhttp, okhttp-sse, okhttp-logging, serialization, coroutines, datetime, commons-text, floatingx×2, quickjs | okhttp/sse/quickjs/serialization/coroutines: yes; logging/datetime/commons-text/floatingx: **no** (unused in common sources) |
| ai | okhttp, okhttp-sse, okhttp-logging, serialization, coroutines, datetime | logging: unused in ai; rest appear in provider/message surfaces |
| search | jsoup | Internal to BingSearchService only |
| highlight | quickjs | Internal to Highlighter; public returns HighlightToken |
| web | ktor server auth/core/content-negotiation/status-pages/sse/cio | App Web API imports these types |

## Examination commands used (resume A+B)

- Glob all `**/build.gradle.kts`
- Grep `\bapi\(|\bkapt\(|\bksp\(` across Gradle
- Per-module import counts for okhttp3, jsoup, quickjs, floatingx, commons.text, ktor.server, datetime, serialization, coroutines
- Public signature / consumer checks for each demotion candidate
- Grep `objectbox`, `kapt`, catalog orphans (`dom4j`, `sqlite-vector`, `navigation2`, etc.)
