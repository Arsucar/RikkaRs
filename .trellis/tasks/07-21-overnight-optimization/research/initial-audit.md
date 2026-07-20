# Initial optimization audit

## Build and packaging

- `gradle.properties:10-13` leaves `org.gradle.parallel=true` commented out, while configuration cache and
  non-transitive R classes are enabled at lines 23-24. With ten included modules (`settings.gradle.kts:35-46`),
  parallel execution is a measurement candidate, not an assumed win.
- `web/build.gradle.kts:10-34,64-66` wires the `buildWebUi` Exec task into every `preBuild`. Inputs and outputs are
  declared, so it must first be observed with two `:web:preBuild --info` runs before any change.
- `app/build.gradle.kts:29-38` already limits APK builds to arm64, and lines 70-78 already enable release R8/resource
  shrinking. APK work should begin with artifact composition, not redundant shrink settings.
- `app/build.gradle.kts:133-137` enables legacy JNI packaging. Changing it is high risk because native/workspace loading
  must be installed and smoke-tested.
- `settings.gradle.kts:25-32` includes `mavenLocal()`. Removal requires dependency-resolution evidence that no local
  artifact is consumed.
- The pre-existing debug APK observed before baseline was 85,875,583 bytes, with compressed root/dex, assets, native
  libs, and resources as the largest groups. This is orientation only, not the formal baseline.

## Runtime candidates

- `HighlightCodeBlock.kt:513-526` uses `runBlocking` inside `HighlightCodeVisualTransformation.filter` to run syntax
  highlighting synchronously. Async replacement is high risk due to cursor offsets and stale results.
- `Markdown.kt:229-253` parses initial Markdown synchronously during composition; `MarkdownNew.kt:115-148` similarly
  generates initial HTML and runs `Jsoup.parse(html)` during composition. Trace or focused timing evidence is required.
- `MarkdownWeb.kt:15-29` rereads `assets/html/mark.html` and performs repeated full-string replacements. Callers at
  `ChatMessage.kt:271-283` and `FullScreenMarkdownViewer.kt:43-49` make template caching a lower-risk candidate.
- `ai/.../KeyRoulette.kt:56-100` reads, decodes, rewrites, and encodes the shared LRU cache under a global lock for each
  key selection. Provider and search call sites are broad, so persistence semantics and concurrency need tests.
- `ConversationEntity.kt` and `GenMediaEntity.kt` have no explicit indices while their DAOs filter/sort common columns.
  Any index requires real-scale `EXPLAIN QUERY PLAN` evidence and migration/write-cost analysis.
- Other measured-only candidates include image Base64 peak allocation (`FileEncoder.kt:52-153`), synchronous skill-file
  reads in composition (`SkillDetailPage.kt:183-186`), and TTS callback file reads (`SystemTTSProvider.kt:55-76`).

## Quality and tests

- No detekt, ktlint, or Spotless plugin/config/task declaration was found in Gradle scripts, version catalogs, or CI.
  The Prompt.md detekt/ktlint metric is therefore N/A at baseline unless runtime task discovery contradicts this.
- `app/build.gradle.kts:130-132` uses `app/lint-baseline.xml`. Existing baseline groups include UnusedResources,
  MissingTranslation, TypographyEllipsis, LocalContextGetResourceValueCall, and other issues; Android Lint is useful
  evidence but is not interchangeable with detekt/ktlint.
- There are 177 test source files. Nine module `ExampleUnitTest` files are placeholders, but tests must not be deleted.
- Temporary-directory cleanup and real-time timeouts are robustness candidates in workspace/app/ai tests. Changes must
  add deterministic coverage or cleanup without hiding behavior.
- CI workflows assemble artifacts but do not run JVM tests or lint. Release/publish workflows are out of scope and may
  not be changed.

## Baseline recommendations

- Serialize all Gradle workloads and include `--no-daemon`.
- Record `java -version`, Gradle/AGP versions, OS/CPU/RAM, branch/commit/status, device list, and disk/cache policy.
- Run baseline `assembleDebug`, `test`, static task discovery, and three identical cold `clean assembleDebug --profile`
  samples. Capture exact APK bytes and SHA-256 after the final baseline sample.
