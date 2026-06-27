# 构建 / 发布 / CI / 文档 / 派生流程 — 综合审计（Subagent F）

- **仓库路径**: `D:\2026Code\Group_android\rikkahub`
- **HEAD**: `3ebfbca45893deefb8c61768a4bdb0e01255a5f6` (`chore(task): archive 06-28-06-28-fix-review-issues`)
- **统计**: 约 2557 commits；近 50 次提交 `497 files changed, 147133 insertions(+), 4811 deletions(-)`（含大量 Trellis/任务归档内容）
- **审计时间**: 2026-06-28
- **范围**: `app/build.gradle.kts`、`build.gradle.kts`、`settings.gradle.kts`、`.github/workflows/*`、`CHANGELOG.md`、`AGENTS.md`、`docs/CHANGELOG_GUIDE.md`、`docs/RIKKA_ARSUCAR_FORK_AND_CI.md`、`gradle/wrapper`、`gradle.properties`、`gradle/libs.versions.toml`、子模块与密钥相关跟踪情况

---

## 1. applicationId 派生一致性

| 位置 | 内容 |
|------|------|
| `app/build.gradle.kts:20` | `applicationId = "me.arsucar.rikka"` |
| `app/build.gradle.kts:83` | `debug` → `applicationIdSuffix = ".debug"` → `me.arsucar.rikka.debug` |
| `app/build.gradle.kts:16` | `namespace = "me.rerere.rikkahub"`（与 fork 文档「方案 A」一致） |
| `app/src/main/AndroidManifest.xml:111,120,130` | `${applicationId}` 用于 FileProvider / DocumentsProvider / Startup |

- **无** `productFlavors` / `flavorDimensions`（全仓库 `*.gradle.kts` 无匹配）。
- **P3** `README_FOR_AGENT.md:57` 仍写 `applicationId=me.rerere.rikkahub`、`versionName=2.2.6`、`versionCode=162`，与当前 `app/build.gradle.kts` 不一致。

---

## 2. Firebase 移除干净度

### 已移除（应用构建链）

| 位置 | 状态 |
|------|------|
| `app/build.gradle.kts` plugins / dependencies | 无 `google-services`、无 Firebase BOM/库 |
| `build.gradle.kts` | 无 Firebase 插件 `apply false` |
| `app/src/main/**`（Grep） | 无 `Firebase` / `google-services` / `crashlytics` 源码引用 |

### 残留

| 严重度 | 位置 | 说明 |
|--------|------|------|
| **P1** | `.github/workflows/release.yml:37-38` | 仍写入 `app/google-services.json`（`GOOGLE_SERVICES_JSON` secret） |
| **P2** | `gradle/libs.versions.toml:40-42,134-137,192-193` | Firebase / google-services 版本与插件 catalog 条目仍存在（未在 app 引用，但 merge 上游易误用） |
| **P2** | `AGENTS.md:17` | 仍要求 `app/google-services.json`（Firebase） |
| **P2** | `README.md` / `README_ZH_CN.md` | 仍提示需要 `google-services.json` |
| **P2** | `README_FOR_AGENT.md:23,54,68` | 仍描述 Firebase 三件套与 Remote Config 初始化 |
| **P3** | `app/src/release/generated/baselineProfiles/startup-prof.txt` | 生成物中含大量 `com/google/firebase/*` 符号（可能为历史 baseline 未重生成） |
| **P3** | `app/.gitignore:3` | `google-services.json` 忽略规则保留（无害） |

`docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 与实现方向一致（无 Firebase）；**遗留上游 workflow `release.yml` 与多份 README/AGENTS 与 fork 策略冲突**。

---

## 3. arm64-only 发布

| 位置 | 内容 |
|------|------|
| `app/build.gradle.kts:29-38` | `splits.abi`：`include("arm64-v8a")`，`isUniversalApk = false`；打 bundle 时 `isEnable = false` |
| `.github/workflows/release-apk.yml:77,83,88` | `:app:assembleRelease`，产物路径 `*arm64-v8a*release*.apk` |
| `ai/build.gradle.kts:21` | `abiFilters` 仅注释，未强制 |

- **P3** `.github/workflows/release.yml:43,50` 上游 workflow 使用 `assembleRelease` + `*.apk`，未限定 arm64，且**无** ABI split 文档化行为（若被手动触发可能上传非预期 APK 集合）。

---

## 4. material-color-utilities 子模块

| 位置 | 内容 |
|------|------|
| `.gitmodules:1-3` | `material3/material-color-utilities` → `material-foundation/material-color-utilities` |
| `material3/build.gradle.kts:25` | `kotlin.srcDir("material-color-utilities/kotlin")` |
| 本地 `git submodule status` | `6fd88eb... material3/material-color-utilities (heads/main)`；`kotlin` 目录存在 |
| `.github/workflows/release-apk.yml:19` | `checkout` → `submodules: recursive` |

- **P2** `.github/workflows/release.yml:11-13` **未** `submodules: recursive`；若仅跑该 workflow，`material3` 编译可能缺子模块源码（当前 fork 主发版路径为 `release-apk.yml`）。

---

## 5. web-ui / pnpm / preBuild

| 位置 | 内容 |
|------|------|
| `web/build.gradle.kts:8-18` | `buildWebUi`：`cmd /c pnpm run build`（Windows）或 `pnpm run build`（非 Windows） |
| `web/build.gradle.kts:63-65` | `preBuild` dependsOn `buildWebUi` |
| `release-apk.yml:47-59` | `pnpm/action-setup@v4`、`setup-node`、cache、`web-ui` 下 `pnpm install --frozen-lockfile` |

- CI 主发版流程在 Gradle 前安装 `web-ui` 依赖，与 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §5.5 一致。
- **P2** 本地/CI 若未初始化子模块或未装 pnpm，`preBuild` 会在 `web` 模块失败（文档已说明 static 未入 git）。

---

## 6. CHANGELOG / AGENTS / 指南一致性

| 文档 | 发版要点 |
|------|----------|
| `CHANGELOG.md:6-11` | 打标签前必须先更新 CHANGELOG；tag message / Release body 取自对应段落 |
| `docs/CHANGELOG_GUIDE.md:7-11` | 同上 + `gh release create` 示例 |
| `AGENTS.md:82-98` | 标签发 Release APK；**发版前更新 CHANGELOG**；`gh workflow run "Release APK (arm64)"`；示例标签 `v2.3.2` |
| `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:66-69` | 写明 **`workflow_dispatch` only**、每次 bump patch + versionCode、写回 Git |

**不一致（事实描述）**:

| 严重度 | 位置 | 说明 |
|--------|------|------|
| **P1** | `release-apk.yml:4-7` vs `RIKKA_ARSUCAR_FORK_AND_CI.md:66` | Workflow 同时支持 **`push: tags: v*`** 与 `workflow_dispatch`；文档称仅手动 dispatch |
| **P1** | `release-apk.yml:21-38` vs `4-7` | **Bump 仅在 `workflow_dispatch`**；**push tag 时不 bump**，直接用当前 `app/build.gradle.kts` 构建并发 Release |
| **P2** | `AGENTS.md:85` vs `CHANGELOG.md` 最新版 | 示例仍为 `v2.3.2`，CHANGELOG 已有 `v2.3.5` |
| **P2** | `RIKKA_ARSUCAR_FORK_AND_CI.md:72,86-89` | 基线版本与「无 workflows」等实施前速查**已过时**（仓库已有 workflows 且版本为 2.3.4/167） |

`CHANGELOG.md` 与 `CHANGELOG_GUIDE` 中英双语流程彼此一致。

---

## 7. 私钥 / keystore 入库风险

| 位置 | 内容 |
|------|------|
| `.gitignore:16-17` | `*.jks`、`rikka-arsucar-release.jks` |
| `AGENTS.md:76` | 勿提交 `*.jks` |
| `git ls-files` | **无** `*.jks` / `keystore` / `google-services.json` 被跟踪 |
| `images/` 跟踪文件数 | **0** |

- **P3** `release-apk.yml:68` CI 在 runner 工作区生成 `rikka-arsucar-release.jks`（预期行为，非入库）。
- **P2** `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:47` 仍提示 `keystore.path` 与 `storeFile` 不一致风险；当前 `app/build.gradle.kts:49-56` **已**兼容 `keystore.path` / `keystore.password` 等旧键名。

---

## 8. versionCode / versionName 与 tag 一致

**当前工作树** (`app/build.gradle.kts:23-24`): `versionCode = 167`, `versionName = "2.3.4"`

**各 tag 指向的 `app/build.gradle.kts`（`git show <tag>:...`）**:

| Tag | versionName | versionCode | 与 tag 名 |
|-----|-------------|-------------|-----------|
| v2.3.3 | 2.3.3 | 166 | 一致 |
| v2.3.4 | 2.3.4 | 167 | 一致 |
| v2.3.5 | **2.3.4** | 167 | **不一致**（Release 资产名用 `github.ref_name` → `v2.3.5`，APK 内版本仍为 2.3.4） |

- **P0** `v2.3.5` 标签对应构建配置未含 `versionName = "2.3.5"`；`CHANGELOG.md:15` 已有 v2.3.5 段落，与 tag 构建内嵌版本脱节。
- **P2** 当前 HEAD 在 `v2.3.5` tag 之后无 bump，仍停留在 2.3.4(167)，若下一发版为 2.3.6 需人工或 dispatch bump。

---

## 9. upstream / 本地脚本残留

| 位置 | 说明 |
|------|------|
| `.github/workflows/release.yml` | 上游式 Release（`KEY_BASE64`、`SIGNING_CONFIG`、`GOOGLE_SERVICES_JSON`），与 fork 发版路径并行存在 |
| `README_FOR_AGENT.md:12` | 硬编码 Windows 路径 `D:\2026Code\Group_android\rikkahub` |
| `AGENTS.md` Trellis 块 | 明确本地 agent 文件勿入 upstream PR（与 fork 推送策略文档化） |
| `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:14` | 仍写历史分支 `local/agent-trellis-setup` |

- **P2** 双 Release workflow 并存，Actions 页手动选错 workflow 会走 Firebase/非 arm64 约束路径。

---

## 10. Gradle / 版本目录（速查）

| 文件 | 要点 |
|------|------|
| `gradle/wrapper/gradle-wrapper.properties:4` | Gradle **9.4.1** |
| `gradle.properties:9,24` | JVM 4G，`android.useAndroidX=true`，`configuration-cache=true` |
| `settings.gradle.kts` | 含 `:app`、`:web`、`:material3` 等，**无** `material-color-utilities` 为独立 Gradle module（仅 git submodule 源码目录） |
| `app/build.gradle.kts` | 无 `app/build.gradle`（仅 kts） |

---

## 发现汇总（按严重度）

| 级别 | ID | 位置 | 摘要 |
|------|-----|------|------|
| P0 | F-01 | tag `v2.3.5` + `app/build.gradle.kts`（tag 树） | 标签 v2.3.5 构建时 `versionName` 仍为 2.3.4 |
| P1 | F-02 | `.github/workflows/release.yml:37-38` | 仍部署 `google-services.json` |
| P1 | F-03 | `release-apk.yml:4-7` vs `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:66` | Tag push 自动发版 vs 文档「仅 workflow_dispatch」 |
| P1 | F-04 | `release-apk.yml:21-22` | Tag 触发不发 bump，与「每次正式发版 patch+1」文档在 tag 路径上不一致 |
| P2 | F-05 | `gradle/libs.versions.toml` | Firebase catalog 残留 |
| P2 | F-06 | `AGENTS.md:17`、`README*.md`、`README_FOR_AGENT.md` | 仍要求/描述 Firebase 与过时版本信息 |
| P2 | F-07 | `.github/workflows/release.yml` | 上游 workflow 无 submodule/pnpm/arm64 约束 |
| P2 | F-08 | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §3/§2.6 | 实施前速查与触发策略过时 |
| P2 | F-09 | 双 workflow | `Release Build` vs `Release APK (arm64)` 易误用 |
| P3 | F-10 | `AGENTS.md:85` | 发版示例版本号过旧 |
| P3 | F-11 | `baselineProfiles/startup-prof.txt` | 可能含历史 Firebase 符号 |
| P3 | F-12 | `README_FOR_AGENT.md:57` | applicationId/版本与 fork 不符 |

---

## 明早 Top 5

1. **P0 — `v2.3.5` 标签与内嵌 `versionName` 2.3.4 不一致**（`git show v2.3.5:app/build.gradle.kts` vs `CHANGELOG.md` v2.3.5 段落）。
2. **P1 — 删除或禁用 `.github/workflows/release.yml`**，避免 Firebase secret 路径与 fork 策略冲突。
3. **P1 — 统一发版触发文档与 `release-apk.yml`**（仅 tag / 仅 dispatch / tag 是否 bump）并修正 `RIKKA_ARSUCAR_FORK_AND_CI.md` §2.6、§3。
4. **P2 — 批量更新 `AGENTS.md`、README、`README_FOR_AGENT.md`**：去掉 `google-services.json` 硬性要求，更正 applicationId 与版本基线。
5. **P2 — 清理 `libs.versions.toml` Firebase 条目**（或注明 fork 禁止引用），并确认 `release-apk.yml` 为唯一正式发版入口。