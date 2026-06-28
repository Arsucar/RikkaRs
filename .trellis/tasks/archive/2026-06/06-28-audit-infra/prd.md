# PRD：审计基础设施修复（CI / 文档 / Gradle catalog）

- **任务目录**：`.trellis/tasks/06-28-audit-infra`
- **来源**：夜间审计 Subagent F（`06-28-nightly-audit` → `research/subagent-f-infra.md`、`audit-report.md` § 构建/CI）
- **分支**：`release/rikka-arsucar`
- **范围**：**P1/P2 基础设施项**（F-02～F-09）。**不含** P0 F-01（`v2.3.5` tag 与 `versionName` 脱节），该条由发版/合规专项单独处理。
- **原则**：fork **无 Firebase**；正式发版唯一入口为 **`Release APK (arm64)`**（`release-apk.yml`）。

---

## 1. 背景与目标

Fork 已在应用层移除 Firebase，但上游遗留 workflow、版本 catalog 与多份 README/AGENTS 仍描述 Firebase 与旧发版流程。`release-apk.yml` 已支持 **tag push + workflow_dispatch**，与 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` 的「仅 dispatch」矛盾；且 **bump 仅在 dispatch**，tag 路径易出现「Release 名 v2.3.x、APK 内版本未 bump」类问题（审计已记录 `v2.3.5` 案例）。

**本任务目标**：消除误用上游 workflow 的风险；统一「如何发版」的文档与 YAML 行为；清理 Firebase catalog 与误导性构建说明。

---

## 2. 产品决策（实施前锁定其一）

### 2.1 发版触发策略（解决 F-03 / F-04）

当前事实：

| 来源 | 触发 | bump |
|------|------|------|
| `release-apk.yml:3-7` | `workflow_dispatch` + `push: tags: v*` | 仅 `workflow_dispatch`（`:21-22`, `:100-107`） |
| `AGENTS.md:82-92` | 主推 **打 tag** 出 GitHub Release；亦可用 `gh workflow run`（dispatch，会 bump） |
| `RIKKA_ARSUCAR_FORK_AND_CI.md:66-69` | 文档写 **仅 `workflow_dispatch`**；每次 patch+1 并 push |

**须在 PRD 实施前与维护者确认一种策略**（推荐 **方案 B**，与当前 `AGENTS.md` 和已有 tag Release 步骤一致）：

| 方案 | workflow | 文档 | 说明 |
|------|----------|------|------|
| **A** | 删除 `push: tags`，仅 dispatch | 保持「仅 dispatch + 每次 bump」 | tag 不再自动发 Release；发版全靠 Actions 手动 Run |
| **B（推荐）** | 保留 tag + dispatch | 更新 RIKKA §2.6、§7.2、§10；明确双路径 | **dispatch**：bump → 构建 → commit push（无 tag 时仅 artifact）。**tag push**：**不 bump**（tag 指向的提交须已含正确 `versionName`/`versionCode` 与 CHANGELOG）；构建并发 GitHub Release（`:85-98`） |
| **C** | 保留 tag + dispatch | 同 B | **tag push 也 bump**（易与 tag 语义冲突，一般不推荐） |

**本 PRD 验收默认按方案 B**：若选 A/C，替换下表 F-03/F-04 的「应改成」描述。

---

## 3. 需求清单（按优先级）

### P1 — F-02：上游 workflow 仍写入 `google-services.json`

| 项 | 内容 |
|----|------|
| **ID** | F-02 |
| **位置** | `.github/workflows/release.yml:37-38`（整块 `Prepare build files` 亦含上游 secret 名：`KEY_BASE64`、`SIGNING_CONFIG`，`:31-35`） |
| **问题** | 手动运行 **Release Build** 会向 `app/google-services.json` 写入 `GOOGLE_SERVICES_JSON`；fork 应用构建链已不需要，且与无 Firebase 策略冲突。 |
| **应改成** | **删除整个 `release.yml`**，或重命名为禁用态并在文件头 `workflow_dispatch` 改为不可触发 + 删除 google-services 步骤（**优先：删除文件**，避免 Actions 列表误点）。若保留文件仅作归档，须在 PR 说明中禁止在 fork 上启用。 |
| **验收标准** | ① 仓库中无会写入 `app/google-services.json` 的 workflow；② `grep -R GOOGLE_SERVICES_JSON .github/workflows` 无结果；③ `AGENTS.md` / RIKKA 文档标明正式发版 workflow 名称仅为 `Release APK (arm64)`。 |
| **优先级** | P1 |

---

### P1 — F-03：workflow 触发与文档不一致

| 项 | 内容 |
|----|------|
| **ID** | F-03 |
| **位置** | `.github/workflows/release-apk.yml:3-7` vs `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:66`、§7.2 `:308-311`、§10 `:467`（仍写「未选 tag / 仅 artifact」与「需新建 workflow」） |
| **问题** | 实现已支持 `push: tags: v*` 且会创建 GitHub Release（`:91-98`）；文档仍写 **`workflow_dispatch` only** 与「无 workflows」。 |
| **应改成** | 按 §2.1 选定方案更新 **RIKKA** 全文一致表述：触发方式、是否创建 GitHub Release、artifact 命名；§3「无 `.github/workflows/`」改为「以 `release-apk.yml` 为准」；§10 表格更新为「已存在」。**同步** `AGENTS.md:82-92` 一句说明与 RIKKA 一致（避免 AGENTS 写 tag、RIKKA 写仅 dispatch）。 |
| **验收标准** | ① 任意读者仅读 RIKKA §2.6 + §7.2 能准确描述当前 YAML 的 `on:` 块；② 无「仅 workflow_dispatch」与 YAML 矛盾的句子（除非执行方案 A 并改 YAML）；③ §10 / §11 启动提示不再要求「新建 release-apk.yml」。 |
| **优先级** | P1 |

---

### P1 — F-04：tag 触发不 bump，与「每次正式发版 +1」文档冲突

| 项 | 内容 |
|----|------|
| **ID** | F-04 |
| **位置** | `.github/workflows/release-apk.yml:21-22`（`if: github.event_name == 'workflow_dispatch'`）、`:100-107`（commit bump 同条件）；对比 `:4-7` tag 触发 |
| **问题** | Push `v*` tag 时直接用当前 `app/build.gradle.kts` 构建；**不**执行 bump、**不** push 版本 commit。若 tag 打在错误版本提交上，会出现 Release 资产名与 APK 内 `versionName` 不一致（审计 F-01 即此路径后果）。 |
| **应改成** | **方案 B**：不改 bump 条件；在 RIKKA + `AGENTS.md` 增加 **tag 发版检查清单**：打 tag 前必须已更新 `CHANGELOG.md`、`versionName`/`versionCode` 与 tag 一致并已 push。**方案 A**：移除 tag 触发，仅 dispatch bump。**方案 C**：为 tag 增加 bump 步骤（需明确 tag 与 commit 顺序，本 PRD 不默认）。 |
| **验收标准** | ① 文档明确写出「dispatch 会 bump」「tag 不会 bump」；② `CHANGELOG_GUIDE.md` 或 RIKKA 交叉引用「tag 前版本对齐」；③ （可选）workflow 在 tag 构建前增加 fail-fast：解析 tag 与 `versionName` 不一致则 `exit 1`（若产品同意，可作为本任务或 follow-up）。 |
| **优先级** | P1 |

---

### P2 — F-05：Firebase / google-services catalog 残留

| 项 | 内容 |
|----|------|
| **ID** | F-05 |
| **位置** | `gradle/libs.versions.toml:40-42`（`[versions]`）、`:134-137`（`[libraries]`）、`:192-193`（`[plugins]`） |
| **问题** | `app/build.gradle.kts` 与根 `build.gradle.kts` 已不再引用；merge 上游时易被重新接上 Firebase。 |
| **应改成** | 删除上述 Firebase / google-services 的 version、library、plugin 条目；全仓库 `grep` 确认无 `libs.firebase`、`libs.plugins.google.services`、`libs.plugins.firebase.crashlytics` 引用（含未跟踪脚本）。若上游 merge 需要保留条目，则在 **本 fork** 仍删除，并在 RIKKA §12 保留 merge 提示。 |
| **验收标准** | ① `libs.versions.toml` 无 `firebase`、`google-services` 键（插件段）；② `./gradlew :app:assembleDebug` 成功；③ 无新增对删除 catalog 名的引用。 |
| **优先级** | P2 |

---

### P2 — F-06：AGENTS / README 仍要求或描述 Firebase

| 项 | 内容 |
|----|------|
| **ID** | F-06 |
| **位置** | `AGENTS.md:17`；`README.md:73`；`README_ZH_CN.md:68`；`README_ZH_TW.md:66`；`README_FOR_AGENT.md:23,54,57,68` |
| **问题** | 仍要求 `google-services.json` 或描述 Firebase 三件套 / Remote Config；`README_FOR_AGENT.md:57` 仍为上游 `applicationId` 与旧版本号。 |
| **应改成** | **AGENTS.md:17**：改为 fork 说明——**不需要** `google-services.json`；本地 Debug 需 `pnpm`（保留原 web 句）。**README*.md**：删除或替换 TIP 为「Rikka-arsucar fork 已移除 Firebase，无需 google-services.json」；可链到 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。**README_FOR_AGENT.md**：技术栈去掉 Firebase；构建注意改为无 google-services；`applicationId`/`versionName`/`versionCode` 与当前 `app/build.gradle.kts` 一致（实施时以文件为准）；`RikkaHubApp`  bullets 删除 Remote Config。 |
| **验收标准** | ① `grep -i google-services README.md README_ZH_CN.md README_ZH_TW.md AGENTS.md` 在构建要求语境下无「必须提供」；② `README_FOR_AGENT.md` 无 Firebase Analytics/Crashlytics/Remote Config 作为当前依赖；③ fork 相关 ID/版本与 `app/build.gradle.kts` 一致。 |
| **优先级** | P2 |

---

### P2 — F-07：上游 `release.yml` 无 submodule / pnpm / arm64 约束

| 项 | 内容 |
|----|------|
| **ID** | F-07 |
| **位置** | `.github/workflows/release.yml` 全文（`:11-13` 无 `submodules: recursive`；无 pnpm；`:43` `assembleRelease` + `:50` `*.apk` 未限定 arm64） |
| **问题** | 与 fork 构建前提（`material3` 子模块、`web` preBuild + pnpm、仅 arm64 发版）不兼容。 |
| **应改成** | 与 **F-02** 一并 **删除** `release.yml`；不在此文件上修补。若必须保留历史文件，则整个 job `if: false` 并文档说明废弃。 |
| **验收标准** | ① Fork 上不可通过 Actions 跑通「无 submodule、无 pnpm」的 release 路径；② 文档仅指向 `release-apk.yml` 的步骤链（checkout submodules、pnpm、`*arm64-v8a*`）。 |
| **优先级** | P2（与 F-02 同提交关闭） |

---

### P2 — F-08：RIKKA 实施前速查与基线过时

| 项 | 内容 |
|----|------|
| **ID** | F-08 |
| **位置** | `docs/RIKKA_ARSUCAR_FORK_AND_CI.md:14`（历史分支 `local/agent-trellis-setup`）；`:72`（`versionCode=165`, `2.3.2`）；§3 `:86-89`（无 workflows、双 ABI universal）；`:47` keystore 键名不一致（**代码已兼容**，文档可改为「已兼容 keystore.path」）；§10 `:460-467` |
| **问题** | 新 AI/协作者按 §3/§11 会误判仓库未改造完成。 |
| **应改成** | 更新 §2.6 基线为「以 `app/build.gradle.kts` 为准」并删除硬编码 165/2.3.2；§3 改为现状核对清单（已有 workflows、仅 arm64 split、Firebase 已移除）；§14 开发分支改为 `release/rikka-arsucar`；签名 §2.3 注明 `build.gradle.kts` 已兼容旧 `local.properties` 键名。 |
| **验收标准** | ① §3 不再声称「无 `.github/workflows/`」；② §2.6 不硬编码过时 versionCode/versionName；③ 与 F-03 更新后的触发策略同篇一致、无内部矛盾。 |
| **优先级** | P2 |

---

### P2 — F-09：双 Release workflow 易误用

| 项 | 内容 |
|----|------|
| **ID** | F-09 |
| **位置** | `.github/workflows/release.yml`（name: `Release Build`）vs `release-apk.yml`（name: `Release APK (arm64)`）；`AGENTS.md:92` 已点名正确 workflow |
| **问题** | Actions UI 两个 Release 名称相近，易选错上游路径（Firebase + 全量 APK）。 |
| **应改成** | 删除 `release.yml`（F-02/F-07）；在 `AGENTS.md`「Rikka-arsucar：提交与发包」首句加粗：**禁止**使用 `Release Build`；RIKKA §7 标题下增加「勿使用已删除的上游 Release Build workflow」。 |
| **验收标准** | ① `.github/workflows/` 仅保留与 fork 相关的 workflow（至少含 `release-apk.yml`）；② 文档明确唯一正式发版入口名称。 |
| **优先级** | P2 |

---

## 4. 非范围（本任务不做）

| ID | 说明 |
|----|------|
| F-01 | P0：`v2.3.5` 与内嵌 `versionName` — 需发版修复或重打 tag，不单靠 infra 文档 |
| F-10 | `AGENTS.md` 发版示例 `v2.3.2` 过旧 — 可在 F-06 顺带改为「与 CHANGELOG 最新版一致」 |
| F-11 | `baselineProfiles/startup-prof.txt` Firebase 符号 — 可选 `./gradlew :app:generateReleaseBaselineProfile` 重生 |
| F-12 | `README_FOR_AGENT.md` 路径硬编码 — 可在 F-06 顺带弱化 |

---

## 5. 实施顺序建议

1. **锁定 §2.1 发版策略**（B / A / C）。
2. **P1**：删除或彻底禁用 `release.yml`（F-02、F-07、F-09）。
3. **P1**：更新 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` + `AGENTS.md` 触发/bump/tag 清单（F-03、F-04、F-08）。
4. **P2**：清理 `libs.versions.toml`（F-05）→ `./gradlew :app:assembleDebug`。
5. **P2**：批量文档 Firebase / 版本 / applicationId（F-06）。

---

## 6. 整体验收

- [ ] `.github/workflows` 无 `GOOGLE_SERVICES_JSON`、无未废弃的 `Release Build` 可执行路径。
- [ ] `release-apk.yml` 行为与 RIKKA + AGENTS 发版章节一致（含 tag 是否 bump）。
- [ ] `libs.versions.toml` 无 Firebase catalog；Debug 构建通过。
- [ ] README 系 + AGENTS 不再要求 `google-services.json`（fork 语境）。
- [ ] `git diff` 不涉及 `*.jks`、secrets、业务 Kotlin 逻辑（纯 infra/docs）。

---

## 7. 参考

- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`（F-02～F-09）
- `.trellis/tasks/archive/2026-06/06-28-nightly-audit/research/subagent-f-infra.md`
- 父任务语境：审计批次 E「构建/CI/文档」— 本任务为 **infra 子任务 PRD**