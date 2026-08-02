# D14 — Build / Gradle / CI Audit (READ-ONLY)

**Scope:** Root + module Gradle (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, wrapper), ProGuard/R8, AndroidManifest(s), `.github/workflows/*`, web-ui `preBuild`  
**Mode:** Static analysis only — **no** Gradle compile/run, **no** production code edits  
**Date:** 2026-08-02  
**Baseline context:** fork `me.arsucar.rikka`, AGP 9.3.1 / Kotlin 2.4.10 / Gradle 9.5.0 / compileSdk·targetSdk 37 / minSdk(app) 26 / versionCode 204 · versionName 2.3.42  
**Cross-ref:** cleartext / exported / backup overlap D12; web-ui preBuild overlap D8

---

## 1. 链路与模块图

```
settings.gradle.kts
  pluginManagement: google(filtered) + mavenCentral + gradlePluginPortal + itext
  dependencyResolution: FAIL_ON_PROJECT_REPOS
    → google + mavenCentral + jitpack + mavenLocal
  includes:
    :app ──► :ai :web :document :highlight :search :speech :common :material3 :workspace
    :app:baselineprofile (android.test → :app)
    :ai ──► :common
    :search ──► :ai :common
    :speech ──► :common
    :web (Ktor server + static; preBuild → buildWebUi → web-ui/pnpm)
    :document (MuPDF consumer keep)
    :workspace (CMake NDK)
    :highlight :material3 (Compose libs)
    :common (okhttp/quickjs api surface)

Release path (fork invariant):
  tag v* | workflow_dispatch
    → .github/workflows/release-apk.yml  "Release APK (arm64)"
      → pnpm install (web-ui) → local.properties signing → :app:assembleRelease --no-daemon
      → arm64-v8a APK artifact (+ GitHub Release on tag; version bump commit on dispatch)

Other CI:
  daily-build.yml  → assembleRelease → softprops nightly prerelease (tag nightly)
  pr-apk.yml       → assemblePrTest (applicationId .pr)
  close-blank-issues.yml / project-intake.yml (non-build)
```

**Toolchain snapshot**

| Item | Value | Source |
|------|--------|--------|
| Gradle | 9.5.0 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 9.3.1 | `libs.versions.toml` |
| Kotlin | 2.4.10 | `libs.versions.toml` |
| KSP | 2.3.10 | `libs.versions.toml` |
| JVM (app) | 17 | `app/build.gradle.kts` |
| JVM (libs) | 11 | most library modules |
| ABI release | arm64-v8a only (`isUniversalApk=false`) | `app/build.gradle.kts` splits |
| R8 release | minify + shrinkResources | app release / prTest |
| Obfuscate | **disabled** (`-dontobfuscate`) | `app/proguard-rules.pro` |
| Firebase | **absent** (fork invariant OK) | no google-services plugin/json |
| applicationId | `me.arsucar.rikka` (+ `.debug` / `.pr`) | app defaultConfig / buildTypes |
| Daemon (CI) | `--no-daemon` on all assemble jobs | workflows |
| Config cache | `org.gradle.configuration-cache=true` | `gradle.properties` |
| Parallel | `org.gradle.parallel=true` | `gradle.properties` |
| Build cache | **not** enabled in properties | — |

---

## 2. Fork 不变量复核（D14 视角）

| Invariant | Status | Evidence |
|-----------|--------|----------|
| `applicationId=me.arsucar.rikka` | OK | `app/build.gradle.kts:20` |
| No Firebase / no `google-services.json` | OK | root plugins + no residual files found |
| Formal release only `Release APK (arm64)` | OK | `release-apk.yml` name + docs; no upstream `Release Build` |
| arm64-only CI product | OK | ABI split + artifact path `*arm64-v8a*` |
| CI uses `--no-daemon` | OK | release / daily / pr workflows |
| Keystore not in git | OK | `.gitignore` `*.jks` + `rikka-arsucar-release.jks` |

---

## 3. 问题清单 (F14-n)

### F14-1 — Keystore password documented in-repo plaintext

| Field | Value |
|-------|--------|
| **id** | F14-1 |
| **file:line** | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:44` |
| **severity** | **CRITICAL** |
| **description** | Fork CI guide hardcodes release keystore password habit as `` `<REDACTED>` `` in tracked documentation. Anyone with repo read access (or public fork history) learns the signing secret material if that password is still in use for the real JKS / GitHub Secrets. Compromised signing key ⇒ malicious APKs under the same package signature. |
| **evidence** | Doc §2.3: “密码沿用本地习惯：`<REDACTED>`” / “Key alias：`rikka-arsucar`”. |
| **suggested fix** | Rotate keystore password (and ideally keystore) if this was ever the production secret; scrub password from docs (use placeholders); ensure GitHub Secrets were not copied from this string alone without rotation; add secret scanning / pre-commit for password-like tokens in `docs/`. |

---

### F14-2 — Non-reproducible SNAPSHOT dependencies on release classpath

| Field | Value |
|-------|--------|
| **id** | F14-2 |
| **file:line** | `gradle/libs.versions.toml:23`, `:64`, `:98`, `:177`; consumed via `app/build.gradle.kts` (nav3 adaptive + sqlite-android) |
| **severity** | **CRITICAL** |
| **description** | Catalog pins `nav3Material = "1.0.0-SNAPSHOT"` and `sqlite-android = "-SNAPSHOT"` (JitPack `com.github.rikkahub:sqlite-android`). Release CI has **no** dependency lockfile. Two builds of the same commit can resolve different SNAPSHOT bits → silent behavior change, hard-to-bisect production bugs, and supply-chain drift (especially with `mavenLocal()` also enabled). |
| **evidence** | ```23:23:gradle/libs.versions.toml
nav3Material = "1.0.0-SNAPSHOT"
``` ```64:64:gradle/libs.versions.toml
sqlite-android = "-SNAPSHOT"
``` |
| **suggested fix** | Pin to immutable versions (Maven release, JitPack commit SHA, or `changing = false` + explicit resolved version). Add Gradle dependency locking or a verified BOM. Ban SNAPSHOT on the release configuration. |

---

### F14-3 — Global cleartext traffic enabled (no Network Security Config)

| Field | Value |
|-------|--------|
| **id** | F14-3 |
| **file:line** | `app/src/main/AndroidManifest.xml:59` |
| **severity** | **HIGH** |
| **description** | `android:usesCleartextTraffic="true"` with **no** `networkSecurityConfig` XML in the tree. Entire app process may use HTTP cleartext (AI/search/custom endpoints, LAN web server, misconfigured providers). Cross-ref D12. Intentional for local web server, but global flag is broader than needed. |
| **evidence** | Manifest application flag; `glob **/network_security_config.xml` → none. |
| **suggested fix** | Default cleartext **false**; add `network_security_config` allowing cleartext only for `localhost` / link-local / user-configured domains if required. |

---

### F14-4 — `GITHUB_API_TOKEN` compiled into `BuildConfig` string field

| Field | Value |
|-------|--------|
| **id** | F14-4 |
| **file:line** | `app/build.gradle.kts:81,87,108-112`; use site `UpdateChecker.kt:42-44` |
| **severity** | **HIGH** |
| **description** | All buildTypes inject `BuildConfig.GITHUB_API_TOKEN` from Gradle property `github.api.token`. If set (local `gradle.properties`, CI secret mapped into project props, or machine-wide props), the token becomes a **plain string constant inside the APK** (even with R8 minify; `-dontobfuscate` keeps names readable). Used as `Authorization: Bearer` for GitHub Releases API. Empty default is safe; non-empty is a classic secret-in-binary footgun. |
| **evidence** | ```81:81:app/build.gradle.kts
            buildConfigField("String", "GITHUB_API_TOKEN", "\"${project.findProperty("github.api.token")?.toString() ?: ""}\"")
``` |
| **suggested fix** | Prefer unauthenticated public API + client rate-limit UX; or inject only via runtime user setting / EncryptedSharedPreferences; never ship long-lived PATs in release APK. Document that CI must **not** pass `github.api.token` into release builds. |

---

### F14-5 — CI writes keystore secrets into `local.properties` without cleanup / validation

| Field | Value |
|-------|--------|
| **id** | F14-5 |
| **file:line** | `.github/workflows/release-apk.yml:61-74`; `daily-build.yml:79-92`; `pr-apk.yml:74-91` |
| **severity** | **HIGH** |
| **description** | Workflows decode JKS and `cat >> local.properties` with `storePassword` / `keyPassword` in plaintext. Only `pr-apk.yml` fails fast if `KEYSTORE_BASE64` empty. No step deletes JKS/`local.properties` after build; no masking beyond Actions secret redaction if passwords echo in logs. Self-hosted or compromised runner artifacts retain signing material. `release-apk` / `daily-build` can proceed with empty secrets → confusing unsigned/failed sign rather than explicit guard. |
| **evidence** | Shared pattern: `echo "$KEYSTORE_BASE64" \| base64 -d > rikka-arsucar-release.jks` + heredoc into `local.properties`. |
| **suggested fix** | Validate all four secrets non-empty; use env-based signing (AGP `signingConfigs` from `System.getenv`) to avoid disk file; `shred`/delete JKS + properties in `if: always()`; never print properties. |

---

### F14-6 — R8 minify without obfuscation + sparse consumer ProGuard rules

| Field | Value |
|-------|--------|
| **id** | F14-6 |
| **file:line** | `app/build.gradle.kts:71-78`; `app/proguard-rules.pro:30`; empty `*/consumer-rules.pro` (except `document`) |
| **severity** | **HIGH** |
| **description** | Release enables minify + resource shrink, but `-dontobfuscate` keeps class/member names. Library modules declare `consumerProguardFiles` yet almost all consumer rules are **empty** (only `document` keeps `com.artifex.mupdf.**`). App keeps `@Serializable`, jlatexmath, Jackson/auth0; Ktor/OkHttp/Room/Compose mostly rely on defaults. Risk: reflection/JNI edge crashes in release (QuickJS, Termux, workspace native, MCP SDK) and easier reverse engineering of API key handling paths. |
| **evidence** | `-dontobfuscate` in app rules; empty consumer files under ai/common/search/speech/web/highlight/material3/workspace. |
| **suggested fix** | Keep `-dontobfuscate` only if crash deobfuscation policy requires it; otherwise enable obfuscation + mapping upload. Add module consumer rules for QuickJS/Termux/native/JNI and run a release smoke matrix after rule changes. |

---

### F14-7 — `mavenLocal()` + JitPack on production resolution path

| Field | Value |
|-------|--------|
| **id** | F14-7 |
| **file:line** | `settings.gradle.kts:21-25` |
| **severity** | **HIGH** |
| **description** | `dependencyResolutionManagement` includes `maven("https://jitpack.io")` and `mavenLocal()`. `FAIL_ON_PROJECT_REPOS` is good, but **mavenLocal** lets a developer machine or CI agent with polluted `~/.m2` override artifacts (including SNAPSHOT sqlite-android). JitPack builds from Git tags/commits without the same SLA as Maven Central. Combined with F14-2, release integrity is weak. |
| **evidence** | ```21:25:settings.gradle.kts
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
``` |
| **suggested fix** | Remove `mavenLocal()` from committed settings (use init script for local dev only). Prefer published coordinates on Central; pin JitPack by commit hash. |

---

### F14-8 — Daily nightly overwrites fixed `nightly` tag with full release APK set

| Field | Value |
|-------|--------|
| **id** | F14-8 |
| **file:line** | `.github/workflows/daily-build.yml:38-39,100-111` |
| **severity** | **HIGH** |
| **description** | Scheduled job has `contents: write`, publishes prerelease tag **`nightly`** (force-overwrite semantics via softprops on fixed tag), uploads `app/build/outputs/apk/release/*.apk` (broader than arm64 filter used in formal release). Users treating nightly as “latest trusted” can be confused; write token is powerful. Check job uses default shallow checkout → `git log --since='24 hours ago'` may **miss** commits if history depth is 1 (false “no commits” skip). |
| **evidence** | `tag_name: nightly`; `files: app/build/outputs/apk/release/*.apk`; check job `actions/checkout@v4` without `fetch-depth: 0`. |
| **suggested fix** | `fetch-depth: 0` on check job; restrict artifact glob to arm64; consider immutable date tags (`nightly-YYYYMMDD`) + separate “latest” pointer; least-privilege token. |

---

### F14-9 — Exported components + broad FileProvider paths (build-manifest surface)

| Field | Value |
|-------|--------|
| **id** | F14-9 |
| **file:line** | `app/src/main/AndroidManifest.xml:61-158`; `app/src/main/res/xml/file_paths.xml` |
| **severity** | **HIGH** |
| **description** | Exported: `RouteActivity` (MAIN/SEND/PROCESS_TEXT), `ShortcutHandlerActivity` (`rikkahub://shortcut`), `McpOAuthCallbackActivity` (`rikkahub://mcp-oauth-callback`), `WorkspaceDocumentsProvider` (`MANAGE_DOCUMENTS`). FileProvider grants `cache-path`, `files-path`, `external-files-path` all `path="."` (entire trees). OAuth/deeplink schemes are fixed and documented; risk is intent spoofing / over-broad URI grants (D12). `UCropActivity` lacks explicit `exported="false"` (should be set for API 31+ clarity). `allowBackup="true"` with backup rules including `upload/` and `workspaces/`. |
| **evidence** | Manifest exported flags; file_paths root `.`; backup_rules includes workspaces. |
| **suggested fix** | Narrow FileProvider paths; explicit `exported` on all activities; review backup excludes for secrets-bearing dirs; validate OAuth callback state (app code — D12). |

---

### F14-10 — web-ui `preBuild` / pnpm toolchain inconsistency across CI

| Field | Value |
|-------|--------|
| **id** | F14-10 |
| **file:line** | `web/build.gradle.kts:10-66`; `release-apk.yml:47-59`; `daily-build.yml:54-69`; `pr-apk.yml:50-64` |
| **severity** | **MEDIUM** |
| **description** | `preBuild` depends on `buildWebUi` which runs `pnpm run build` only (no install). CI correctly pre-installs with `pnpm install --frozen-lockfile`, but **pnpm major differs**: release workflow pins **pnpm 9**, daily + pr pin **pnpm 11**, while lockfile is `lockfileVersion: '9.0'`. macOS still uses `zsh -ic "pnpm run build"` (interactive shell PATH hacks; docs claimed cleanup). No `packageManager` field in `web-ui/package.json`. Local Windows/Linux builds fail without global pnpm. Static resources under `web/src/main/resources/static` are build outputs (not source-of-truth). |
| **evidence** | release `pnpm/action-setup` version 9 vs daily/pr version 11; `Os.isFamily(MAC)` → `zsh -ic`. |
| **suggested fix** | Unify pnpm major (prefer lockfile-aligned + Corepack `packageManager`); drop `zsh -ic`; document required Node 22; optionally cache web-ui build outputs carefully. |

---

### F14-11 — Release workflow: no Gradle cache; version bump race; weak CHANGELOG gate

| Field | Value |
|-------|--------|
| **id** | F14-11 |
| **file:line** | `.github/workflows/release-apk.yml:21-38,76-77,90-123` |
| **severity** | **MEDIUM** |
| **description** | Formal release lacks Gradle dependency cache (daily/pr have `actions/cache`). `workflow_dispatch` bumps version **before** assemble; on build failure, working tree is bumped but commit step is `if: success()` — good — yet concurrent dispatches can double-bump / non-linear versions. Tag path does not fail if CHANGELOG section missing (warn + empty notes). `git push` of bump uses default `GITHUB_TOKEN` with `contents: write` on the checked-out branch (ok for fork, still no concurrency group). No ABI assertion step beyond path glob. |
| **suggested fix** | Add cache (or `setup-java` cache); concurrency group for release; fail job if CHANGELOG section absent on tag; bump **after** successful assemble in a dedicated job with fresh checkout; assert exactly one arm64 APK. |

---

### F14-12 — SDK / Java level drift across modules

| Field | Value |
|-------|--------|
| **id** | F14-12 |
| **file:line** | multiple `*/build.gradle.kts`; `app/baselineprofile/build.gradle.kts:8-21` |
| **severity** | **MEDIUM** |
| **description** | App: minSdk 26, compile/target 37, Java 17. Libraries: mostly minSdk 26/Java 11, but **search minSdk 23**, **web & highlight minSdk 24**. Baseline profile: compileSdk 36 + minorApiLevel 1, target/min 36/28. Drift complicates API assumptions and merging; not a break by itself (app raises effective min to 26) but baseline profile SDK lag can hide API 37 issues. |
| **suggested fix** | Normalize library minSdk to 26 (or document intentional lower floors); align baselineprofile compileSdk with app 37; consider Java 17 uniformly. |

---

### F14-13 — Signing config always selected; empty local signing silently incomplete

| Field | Value |
|-------|--------|
| **id** | F14-13 |
| **file:line** | `app/build.gradle.kts:41-72` |
| **severity** | **MEDIUM** |
| **description** | `signingConfigs.create("release")` only populates store fields when all four properties exist; release/prTest **always** set `signingConfig = signingConfigs.getByName("release")`. Missing `local.properties` ⇒ empty signing config → debug-key or unsigned behavior depending on AGP, easy to ship wrong signature locally or misread CI failures. |
| **suggested fix** | Fail release tasks if storeFile unset; or only assign signingConfig when complete. |

---

### F14-14 — Configuration cache on; build cache off; high heap defaults

| Field | Value |
|-------|--------|
| **id** | F14-14 |
| **file:line** | `gradle.properties:9-13,24` |
| **severity** | **MEDIUM** |
| **description** | `org.gradle.configuration-cache=true` + parallel + `-Xmx4096m`, but **no** `org.gradle.caching=true`. CI uses `--no-daemon` (good for agents). Config cache can break tasks that touch absolute paths / Exec `buildWebUi`; no documented opt-out. Local agents already stress memory (AGENTS.md multi-Gradle policy). |
| **suggested fix** | Enable build cache in CI with key on wrapper + catalogs; verify config-cache with `buildWebUi`; document agent `--no-daemon` (already in AGENTS.md). |

---

### F14-15 — Backup / data extraction includes workspace & upload trees

| Field | Value |
|-------|--------|
| **id** | F14-15 |
| **file:line** | `app/src/main/res/xml/backup_rules.xml`; `data_extraction_rules.xml`; Manifest `allowBackup="true"` |
| **severity** | **MEDIUM** |
| **description** | Cloud/device transfer backup **includes** `file/upload/` and `external/workspaces/`. Workspace may contain user documents and tool outputs; combined with cleartext and web server risks, backup surface is large. Settings/API keys live primarily in DataStore (not obviously excluded here — default include behavior for app data still applies outside these includes depending on rules mode). |
| **suggested fix** | Explicit exclude sensitive domains; encrypt backups (product); re-read auto-backup semantics for DataStore paths. |

---

### F14-16 — Catalog / dependency hygiene debt

| Field | Value |
|-------|--------|
| **id** | F14-16 |
| **file:line** | `gradle/libs.versions.toml`; `app/build.gradle.kts:325-326` |
| **severity** | **LOW** |
| **description** | `dom4j` catalog entry appears unused in module scripts; `ktor-server-caching-headers` library declared but web module does not depend on it; `app` still has `fileTree("libs")` though `app/libs` missing; `implementation(kotlin("reflect"))` pulls full reflect into app; duplicate okhttp via `:common` `api` + app `implementation`. No Gradle version catalog unused-check in CI. Alpha/rc/snapshot mixed: material3 `1.5.0-alpha25`, haze `2.0.0-alpha03`, baselineprofile beta, nav3 snapshot. |
| **suggested fix** | Periodic `dependencies` insight; remove dead catalog entries; drop empty fileTree; shrink reflect usage. |

---

### F14-17 — PluginManagement itext repo + submodule checkout cost

| Field | Value |
|-------|--------|
| **id** | F14-17 |
| **file:line** | `settings.gradle.kts:12`; `.gitmodules`; workflows `submodules: recursive` |
| **severity** | **LOW** |
| **description** | `https://repo.itextsupport.com/android` only under **pluginManagement** (not dependency repos) — low risk if unused for plugins, but extra network trust root. `material3/material-color-utilities` git submodule forces recursive submodule fetch on every CI job (latency + third-party git availability). |
| **suggested fix** | Drop itext if unused; vendor or composite-build material-color-utilities if submodule flakiness appears. |

---

### F14-18 — Permissions / FGS declarations (build-time policy surface)

| Field | Value |
|-------|--------|
| **id** | F14-18 |
| **file:line** | `app/src/main/AndroidManifest.xml:5-22,131-138`; `web` manifest NEARBY_WIFI_DEVICES |
| **severity** | **LOW** |
| **description** | Broad permissions: CAMERA, RECORD_AUDIO, PACKAGE_USAGE_STATS, calendar R/W, FOREGROUND_SERVICE_SPECIAL_USE (WebServerService with property justification — good), ACCESS_LOCAL_NETWORK, multicast. `web` module adds NEARBY_WIFI_DEVICES. Play policy review surface, not a Gradle bug. `android:largeHeap="true"` masks memory pressure. |
| **suggested fix** | Trim unused permissions; document Play special-use FGS; revisit largeHeap after profiling. |

---

### F14-19 — Library module ProGuard files are Android Studio templates only

| Field | Value |
|-------|--------|
| **id** | F14-19 |
| **file:line** | `*/proguard-rules.pro` (ai, search, speech, web, …) |
| **severity** | **LOW** |
| **description** | Library `isMinifyEnabled = false` with stock commented proguard templates. Real shrinking only at app. Harmless clutter; false sense of “module rules exist”. |
| **suggested fix** | Rely on consumer-rules; delete or replace templates with real keeps when needed. |

---

### F14-20 — prTest / debug applicationId isolation (positive control)

| Field | Value |
|-------|--------|
| **id** | F14-20 |
| **file:line** | `app/build.gradle.kts:83-113`; `pr-apk.yml` |
| **severity** | **LOW** (informational / residual risk only) |
| **description** | **Positive:** `.debug` and `.pr` suffixes prevent overwriting production `me.arsucar.rikka`. pr-apk uses read-only `contents: read`, retention 14 days, concurrency cancel-in-progress. Residual: prTest still **signed with production keystore** (same cert family as release) — sideload trust equals release cert; users may not notice `.pr` id. |
| **suggested fix** | Optional separate upload key for prTest; UI watermark already `RikkaRs PR`. |

---

## 4. CI workflow matrix (summary)

| Workflow | Trigger | Build | Daemon | Signing | Cache | Notes |
|----------|---------|-------|--------|---------|-------|-------|
| **release-apk.yml** | `workflow_dispatch`, tag `v*` | `assembleRelease` | `--no-daemon` | Secrets → local.properties | pnpm only | Formal arm64; bump on dispatch; Release on tag |
| **daily-build.yml** | cron + dispatch | `assembleRelease` | `--no-daemon` | same | pnpm + gradle | Overwrites `nightly`; shallow check risk |
| **pr-apk.yml** | dispatch | `assemblePrTest` | `--no-daemon` | same + base64 guard | pnpm + gradle | `.pr` id; artifacts 14d |
| **close-blank-issues** | issues opened | — | — | — | — | Template enforcement |
| **project-intake** | issues/PR | — | — | PAT optional | — | No code checkout from PR |

All assemble paths correctly use **`--no-daemon`**. No workflow runs `connectedDebugAndroidTest` by default (aligned with AGENTS.md).

---

## 5. R8 / ProGuard detail

**App release applies:**

- `proguard-android-optimize.txt`
- `app/proguard-rules.pro`: keep Serializable; keep jlatexmath; dontwarn re2j / java.lang.management / java.beans; keep Jackson + auth0; **`-dontobfuscate`**; keep SourceFile/LineNumberTable + Signature attributes

**Module consumer:**

- `document`: keep MuPDF
- others: empty

**Risk classes:** kotlinx.serialization (kept by annotation), Ktor CIO server, MCP SDK, QuickJS JNI, Termux `.so` (`packaging.jniLibs.useLegacyPackaging = true`, pickFirst `libtermux.so`), Room (KSP).

---

## 6. web-ui preBuild contract

```
:web:preBuild → buildWebUi (Exec)
  workingDir = web-ui/
  command = pnpm run build   (Windows: cmd /c; Mac: zsh -ic; else pnpm)
  inputs = package.json, pnpm-lock, configs, app/, public/
  outputs = web/src/main/resources/static
```

CI must `pnpm install --frozen-lockfile` **before** Gradle (implemented). Local agent without pnpm fails at preBuild. Static dir is generated — clean checkouts need network + Node.

---

## 7. Secrets inventory (build/CI)

| Secret / material | Where | Risk |
|-------------------|-------|------|
| `KEYSTORE_BASE64` / passwords / alias | GH Secrets → disk files in CI | F14-5 |
| Documented password `<REDACTED>` | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` | **F14-1 CRITICAL** |
| `github.api.token` → BuildConfig | optional Gradle prop | F14-4 |
| `ADD_TO_PROJECT_PAT` | project-intake | scoped; skipped if empty |
| User API keys | **not** in Gradle (runtime DataStore) | D12 |
| `*.jks` | gitignored | OK |

---

## 8. Positive findings

1. Version catalog centralization (`libs.versions.toml`) + root plugins `apply false`.
2. `RepositoriesMode.FAIL_ON_PROJECT_REPOS` prevents per-module rogue repos.
3. ABI split enforces arm64 product; bundle tasks disable splits appropriately.
4. Formal release workflow name matches docs; Firebase fully removed.
5. `--no-daemon` consistent on build workflows.
6. prTest isolated applicationId; pr-apk least privilege `contents: read`.
7. Room schema export path configured; lint baseline present.
8. WebServerService `exported=false` + special-use property declared.
9. FileProvider / DocumentsProvider authorities use `${applicationId}` (fork coexistence).
10. Wrapper `validateDistributionUrl=true`.

---

## 9. Severity tally

| Severity | Count | IDs |
|----------|-------|-----|
| CRITICAL | 2 | F14-1, F14-2 |
| HIGH | 7 | F14-3 … F14-9 |
| MEDIUM | 6 | F14-10 … F14-15 |
| LOW | 5 | F14-16 … F14-20 |
| **Total** | **20** | |

---

## 10. Recommended fix order (for later implement phase — not done in this audit)

1. **Rotate** signing secrets if F14-1 password was real; scrub docs.  
2. **Pin** SNAPSHOT deps; remove `mavenLocal()` from committed settings.  
3. Network security config; stop shipping tokens in BuildConfig.  
4. Harden CI signing (validate, delete, no disk props).  
5. Unify pnpm + add release Gradle cache; fix daily shallow clone.  
6. Consumer R8 rules / release smoke.  
7. Hygiene (minSdk, catalog dead entries, backup rules).

---

## 11. Top 10 (file:line + one-line)

1. **F14-1 CRITICAL** `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:44` — release keystore password committed in docs plaintext.  
2. **F14-2 CRITICAL** `gradle/libs.versions.toml:23,64` — SNAPSHOT nav3 + sqlite-android → non-reproducible releases.  
3. **F14-3 HIGH** `app/src/main/AndroidManifest.xml:59` — global `usesCleartextTraffic=true`, no NSC.  
4. **F14-4 HIGH** `app/build.gradle.kts:81` — optional GitHub PAT baked into `BuildConfig` / APK.  
5. **F14-5 HIGH** `.github/workflows/release-apk.yml:61-74` — keystore + passwords written to runner disk, weak validation.  
6. **F14-6 HIGH** `app/proguard-rules.pro:30` — minify with `-dontobfuscate` + empty module consumer rules.  
7. **F14-7 HIGH** `settings.gradle.kts:24` — `mavenLocal()` on production resolution path.  
8. **F14-8 HIGH** `.github/workflows/daily-build.yml:104-111` — fixed `nightly` tag overwrite + shallow 24h check risk.  
9. **F14-9 HIGH** `app/src/main/AndroidManifest.xml:69-158` + `file_paths.xml` — exported entry points + root FileProvider paths.  
10. **F14-10 MEDIUM** `web/build.gradle.kts:16` / workflows — pnpm 9 vs 11 mismatch + Mac `zsh -ic` preBuild.

---

*End of D14 report. Static only; no Gradle execution; no production code changes.*
