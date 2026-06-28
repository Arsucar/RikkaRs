# implement.md — 基础设施审计修复（CI / 文档 / Gradle catalog）

- **任务目录**：`.trellis/tasks/06-28-audit-infra`
- **策略锁定**：**方案 B**（PRD §2.1）— 保留 `release-apk.yml` 的 `workflow_dispatch` + `push: tags: v*`；**仅 dispatch 自动 bump**；**tag 路径不 bump**，发 tag 前人工对齐 `CHANGELOG.md` 与 `app/build.gradle.kts` 版本。
- **YAML 原则**：F-03/F-04 **不改** `release-apk.yml` 的 `on:` / bump 条件（已与方案 B 一致）；以文档与 `AGENTS.md` 对齐行为。
- **实施基线**（`app/build.gradle.kts`，实施时若已变以文件为准）：`applicationId = "me.arsucar.rikka"`，`versionCode = 167`，`versionName = "2.3.4"`。

---

## 1. 有序检查清单

### 阶段 0 — 开工前（一次性）

| # | 动作 | 位置 | 具体改动 |
|---|------|------|----------|
| 0.1 | 确认策略 | PRD §2.1 | 不选方案 A/C；本 implement 全程按 **方案 B** 写文档。 |
| 0.2 | 记录基线版本 | `app/build.gradle.kts:20-24` | 实施 F-06/F-08 时从该文件读取 `applicationId` / `versionCode` / `versionName`，勿硬编码 PRD 示例 `165`/`2.3.2`。 |

---

### 阶段 1 — P1：标注误用 workflow，不做删除（F-02 / F-07 / F-09）

**决策**：`release.yml` 为上游 workflow，fork 未修改过且仅有 `workflow_dispatch` 触发。删除它会在下次 merge 上游时产生 conflict，且实际误点风险极低。改为不动文件，仅在文档中标注。

| # | 动作 | 位置 | 具体改动 |
|---|------|------|----------|
| 1.1 | **文档标注** | `AGENTS.md` Rikka-arsucar 发包小节（约 `:74-92`） | 明确：正式发版 workflow 名称**仅** `Release APK (arm64)`；上游 `Release Build` 不适用于 fork（无 Firebase、无 submodule），请勿使用。 |
| 1.2 | 同上 | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §7 标题或 §9 清单 | 增加一句：上游遗留 `release.yml` 保留不删但请勿使用；Actions 选择 `Release APK (arm64)`。 |

**注意**：`release.yml` 中的 `GOOGLE_SERVICES_JSON` secret 在 fork 上不存在，就算误点也会因 secret 缺失而失败，不会造成危害。

---

### 阶段 2 — P1：发版文档与方案 B（F-03 / F-04 / F-08）

**不修改** `release-apk.yml` 以下已符合方案 B 的片段（仅作对照）：

- `on:`：`:4-7` `workflow_dispatch` + `push.tags: v*`
- Bump：`:21-22`、`:100-107` 仅 `workflow_dispatch`
- Tag Release：`:85-98` `startsWith(github.ref, 'refs/tags/')` 创建 GitHub Release

| # | 动作 | 位置 | 具体改动 |
|---|------|------|----------|
| 2.1 | 重写 §2.6 触发与 bump | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 约 `:60-72` | 删除「**`workflow_dispatch` only**」「每次正式 workflow +1」的单一表述。改为表格：**路径 A — dispatch**：自动 patch+1 / versionCode+1，成功后 commit push（对应当前 YAML）。**路径 B — push `v*` tag**：**不 bump**；用 tag 指向的提交内已有 `versionName`/`versionCode` 构建；创建 GitHub Release + arm64 APK（对应当前 YAML `:91-98`）。版本基线写「以 `app/build.gradle.kts` 为准」，删除硬编码 `165` / `2.3.2`。 |
| 2.2 | §2.3 签名说明 | 同文档约 `:47-52` | 注明 `app/build.gradle.kts` 已支持 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`，并**兼容**旧 `local.properties` 键名（`keystore.path` 等），避免读者以为仅文档键名有效。 |
| 2.3 | §1 / §2.5 分支 | 约 `:14`、`:56-58` | 将「当前开发分支」叙事改为 **`release/rikka-arsucar`**（替代 `local/agent-trellis-setup` 作为目标开发分支描述）。 |
| 2.4 | §3 现状核对 | 约 `:78-89` | 由「尚无 `.github/workflows/`、需新建」改为**现状清单**：已有 `release-apk.yml`；Firebase 已移除；CI 仅 arm64 产物；`google-services.json` 不需要。 |
| 2.5 | §7.2 触发器 | 约 `:308-311` | `on:` 示例改为与仓库一致：`workflow_dispatch` + `push: tags: ["v*"]`；说明 dispatch 可 bump、tag 不 bump。 |
| 2.6 | §7.3 步骤 | 约 `:317-336` | 在 bump 步骤加 `if: github.event_name == 'workflow_dispatch'` 说明；补充 tag 路径步骤：上传 artifact 命名、Create GitHub Release（无版本 commit push）。 |
| 2.7 | §7.4 骨架示例 | 约 `:344-346` | 示例 `on:` 与真实 YAML 一致（含 tag）；避免读者复制「仅 dispatch」旧骨架。 |
| 2.8 | §10 范围表 | 约 `:458-467` | `release-apk.yml` 行改为 **已存在**；「自动 GitHub Release / tag」改为 **已选（tag push）**；dispatch 仍主要为 artifact + bump push。 |
| 2.9 | §11 AI 提示 | 约 `:472-480` | 第 5 条改为：双路径发版 — dispatch 自动 bump；**tag 发版前**须更新 `CHANGELOG.md` 且 `versionName` 与 tag（如 `v2.3.5` → `2.3.5`）一致，**tag 触发不 bump**。 |
| 2.10 | **tag 发版清单（F-04）** | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 新增小节（建议 §9 后或并入 §9） | 条目：① 更新 `CHANGELOG.md`；② `app/build.gradle.kts` 中 `versionName`/`versionCode` 与待发 tag 一致；③ `git push origin <tag>`；④ 不在 tag 路径期待 CI 改版本号。可选 follow-up：workflow tag 前校验 `refs/tags/v*` 与 `versionName` 不一致则 fail（**本任务默认不做**，除非产品明确要求）。 |
| 2.11 | 对齐 `AGENTS.md` | 约 `:17`、`:74-92` | **:17** 见阶段 3。**发包**：与 RIKKA 方案 B 一致 — **tag**：先 CHANGELOG + 版本对齐再 `git tag`/`push`（会 GitHub Release，**不**自动 bump）；**dispatch**：`gh workflow run "Release APK (arm64)"` 自动 bump 并 push。强调勿再引用 `Release Build`。示例 tag 版本号可改为占位「与 `build.gradle.kts` 一致」避免过时 `v2.3.2`。 |

---

### 阶段 3 — P2：Gradle catalog（F-05）

| # | 动作 | 位置 | 具体改动 |
|---|------|------|----------|
| 3.1 | 删除 version 键 | `gradle/libs.versions.toml` `:40-42` | 移除 `google-services`、`firebase-bom`、`firebase-crashlytics` 三行。 |
| 3.2 | 删除 library 条目 | 同文件 `:134-137` | 移除 `firebase-bom`、`firebase-analytics`、`firebase-crashlytics`、`firebase-config` 四行。 |
| 3.3 | 删除 plugin 条目 | 同文件 `:192-193` | 移除 `google-services`、`firebase-crashlytics` 两个 `[plugins]` 条目。 |
| 3.4 | 全仓引用扫描 | 仓库根 | `grep`/搜索确认无 `libs.firebase`、`libs.plugins.google.services`、`libs.plugins.firebase.crashlytics`（含 `build.gradle.kts`、脚本）。`docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §5 中的**历史删除说明**可保留（教学用），不视为 catalog 引用。 |
| 3.5 | RIKKA §12 | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §12 | 保留/补一句：merge 上游时勿把 Firebase catalog  blindly 合回；本 fork 已删条目。 |

---

### 阶段 4 — P2：README / AGENTS Firebase 与过时信息（F-06）

| # | 动作 | 位置 | 具体改动 |
|---|------|------|----------|
| 4.1 | Fork 构建说明 | `AGENTS.md:17` | 改为：Rikka-arsucar fork **不需要** `google-services.json`；保留 `web` 模块需本地 `pnpm` 一句。 |
| 4.2 | 英文 README | `README.md:73` 附近 TIP | 替换为：fork 已移除 Firebase，无需 `google-services.json`；可链 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。 |
| 4.3 | 简体 README | `README_ZH_CN.md:68` | 同上（中文）。 |
| 4.4 | 繁体 README | `README_ZH_TW.md:66` | 同上（繁体）。 |
| 4.5 | Agent README | `README_FOR_AGENT.md` | `:23` 技术栈删除 Firebase 三件套；`:54` 改为无需 google-services；`:57-58` 改为 `me.arsucar.rikka` 与当前 `versionName`/`versionCode`（来自 `app/build.gradle.kts`）；`:68` 删除 Remote Config 初始化 bullet。 |

**说明**：`app/.gitignore:3` 保留 `google-services.json` 忽略规则（PRD F-11 旁注 — 无害）。**不实施** F-11 baseline 重生（可选 follow-up）。

---

## 2. 推荐实施顺序与并行化

```
阶段 0 → 阶段 1（删 release.yml）
         ↓
    ┌────┴────┬────────────┐
    ↓         ↓            ↓
 阶段 3     阶段 4       阶段 2
 (F-05)    (F-06)    (RIKKA + AGENTS 发版)
    └────┬────┴────────────┘
         ↓
      验证（§3）
```

| 可并行批次 | 包含项 | 注意 |
|------------|--------|------|
| **串行优先** | 1.1 删除 `release.yml` | 先做完再改文档中「唯一 workflow」表述，避免中间态矛盾。 |
| **批次 P**（可并行） | 3.1–3.3 catalog；4.1–4.5 README 系；2.1–2.11 RIKKA；2.11 AGENTS 发包段 | **同文件 `AGENTS.md`**：4.1 与 2.11 合并为一次编辑，避免冲突。 |
| **批次 P 内依赖** | 2.10 tag 清单依赖 2.1 方案 B 表述 | 先写清 §2.6 再写清单。 |
| **最后** | 1.2–1.3 若未在阶段 2 一并完成 | 与 2.11 重复内容只保留一处权威（推荐 AGENTS + RIKKA §7）。 |

---

## 3. 验证步骤

在仓库根目录（Windows：`.\gradlew`）：

| # | 命令 / 检查 | 期望 |
|---|-------------|------|
| V1 | `rg GOOGLE_SERVICES_JSON .github/workflows` 或等效 grep | 无匹配 |
| V2 | `Test-Path .github/workflows/release.yml` | `False` |
| V3 | `rg -i "firebase|google-services" gradle/libs.versions.toml` | 无 version/library/plugin 键（仅注释若有可接受） |
| V4 | `rg "libs\.firebase|libs\.plugins\.google\.services|libs\.plugins\.firebase\.crashlytics" --glob "*.{kts,toml,gradle*}"` | 无引用 |
| V5 | `.\gradlew :app:assembleDebug` | 成功（PRD F-05 验收） |
| V6 | `rg -i google-services README.md README_ZH_CN.md README_ZH_TW.md AGENTS.md` | 构建语境下无「必须提供」类表述 |
| V7 | `README_FOR_AGENT.md` 人工扫一眼 | 无 Firebase 三件套为当前依赖；ID/版本与 `app/build.gradle.kts` 一致 |
| V8 | 对照 `release-apk.yml:3-7` 与 RIKKA §2.6 / §7.2 | 双触发一致；文档明确 dispatch bump / tag 不 bump |
| V9 | `git diff --name-only` | 无 `*.jks`、无业务 Kotlin 无关 infra 外泄 |

**可选（不阻塞本任务）**：`.\gradlew :app:generateReleaseBaselineProfile` 处理 F-11。

---

## 4. 回滚计划

| 场景 | 操作 |
|------|------|
| 单次提交回滚 | `git restore --source=HEAD~1 -- <paths>` 或 `git revert <commit>` |
| 恢复 `release.yml` | `git checkout HEAD~1 -- .github/workflows/release.yml`（仅当误删需临时恢复；fork 策略上应再次删除） |
| catalog 回滚 | `git restore gradle/libs.versions.toml` |
| 文档回滚 | `git restore AGENTS.md README*.md README_FOR_AGENT.md docs/RIKKA_ARSUCAR_FORK_AND_CI.md` |
| 验证回滚结果 | 重跑 V5；若恢复了 `release.yml`，V1/V2 将失败 — 确认是否 intentional |

---

## 5. PRD 验收对照（完成后勾选）

- [ ] F-02 / F-07 / F-09：无 `GOOGLE_SERVICES_JSON` workflow；无 `Release Build` 可执行路径
- [ ] F-03 / F-04 / F-08：RIKKA + AGENTS 与 `release-apk.yml` 一致（方案 B）；tag 清单已文档化
- [ ] F-05：catalog 已清理；`assembleDebug` 通过
- [ ] F-06：README 系 + AGENTS 不要求 google-services（fork 语境）

---

## 6. 参考

- `.trellis/tasks/06-28-audit-infra/prd.md`
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-f-infra.md`
- 仓库现状：`.github/workflows/release-apk.yml`、已删除目标 `release.yml`