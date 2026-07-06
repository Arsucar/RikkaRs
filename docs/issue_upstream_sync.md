### 功能描述 | Feature Description

希望在 **Arsucar/rikkahub** 仓库建立**可重复、可追踪**的流程，将发行与日常开发分支 **`release/rikka-arsucar`** 与上游 **`rikkahub/rikkahub`** 的 **`master`** 定期对齐并合并，在吸收上游功能与修复的同时，稳定保留本 fork 的产品与工程约束。

当前相对 `upstream/master` 的提交差异（在 `release/rikka-arsucar` 上执行 `git rev-list --left-right --count HEAD...upstream/master`）：

| 指标 | 数量 | 含义 |
|------|------|------|
| **ahead（领先上游）** | **34** | 仅存在于本分支、尚未进入上游的提交 |
| **behind（落后上游）** | **2574** | 上游 `master` 已有、本分支尚未合并的提交 |

期望产出包括但不限于：合并节奏（如每 N 周或每发版前）、冲突处理清单、合并后验证步骤（`assembleDebug`、关键路径冒烟）、以及在 `CHANGELOG.md` 中记录上游合并（可参考 **v2.3.10** 中「合并 `upstream/master` 至 `release/rikka-arsucar`」的写法）。

实施与风险边界应明确写入仓库文档，并与现有说明一致：

- **`docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §12（风险与 merge 上游提示）**：仅改 `applicationId` 时以应用代码合并为主；**勿**在 merge 中盲目恢复 Firebase / `google-services` / catalog 中的 Firebase 条目；Deep link `rikkahub://` 与上游相同不阻止共存。
- **`AGENTS.md` — Git Commit and Upstream PR Rules**：本仓库为独立下游，**不向** `rikkahub/rikkahub` 提 PR；若将来有面向上游的贡献，须从干净 `master` 拉 `feat/*`，且 diff **不得**包含 `.trellis/`、`.codex/`、`.agents/`、`README_FOR_AGENT.md` 等本地 Agent 工具链文件。
- **`CHANGELOG.md`**：发版与合并上游时维护变更记录（**v2.3.10** 已有一次上游合并记录，可作为模板）。

本 fork **必须持续遵守**的差异化约束（合并流程文档中应显式列出，避免被上游 diff 覆盖）：

- **无 Firebase**（Analytics / Crashlytics / Remote Config）；不需要 `google-services.json`。
- **Release `applicationId`**：`me.arsucar.rikka`（Debug：`me.arsucar.rikka.debug`）；显示名 **Rikka-arsucar**。
- **正式发版仅** `.github/workflows/release-apk.yml`（**Release APK (arm64)**）；勿恢复上游已删除的 Release Build workflow。
- **压缩上下文对话框保留 fork 交互**：若合并上游 commit `0edcd81bccbf3287ebd59b5baf7822f457a6e835`
  （`refactor(ui): 压缩上下文对话框保留消息数改为手动输入`）涉及
  `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt` 冲突，
  **以本 fork 为准**，保留 `CustomNumberSelector` 的 `0/16/32/64 + 自定义` 分段选数，不接受上游纯手动输入替换。
- **勿将 Trellis / 本地 Agent 专用改动推向上游**；Trellis 文件可在 **Arsucar/rikkahub** 公开，但不属于上游合并目标。

### 使用场景 | Usage Scenario

1. **定期同步**：维护者在约定周期内 `git fetch upstream`，将 `upstream/master` 合并入 `release/rikka-arsucar`，解决冲突后 push 到 `origin`，并更新 `CHANGELOG.md`。
2. **发版前对齐**：在路径 A（`v*` tag）或路径 B（`workflow_dispatch`）发版前，先完成或评估一次上游合并，减少长期 behind 导致的巨型冲突（当前 behind **2574** 说明需尽快规划分批合并策略）。
3. **新协作者 / 新 AI 窗口**：阅读 `docs/RIKKA_ARSUCAR_FORK_AND_CI.md` §12 与本文档化流程后，可按检查清单执行 merge，而不重复争论 Firebase、applicationId、CI 入口等已决事项。
4. **冲突高发区**：`app/build.gradle.kts`、`gradle/libs.versions.toml`（Firebase catalog）、`web/build.gradle.kts`（pnpm 跨平台）、以及上游重新引入的 Firebase Kotlin/DI — 合并时按 §12 保持「无 Firebase」策略。
5. **已知冲突锚点**：`CompressContextDialog.kt` 遇到上游 `0edcd81bccbf3287ebd59b5baf7822f457a6e835`
   时用 `checkout --ours` 或手工保留 fork 版本；合并后回归目标 token、保留条数分段、自定义数字、确认/取消和加载态。

### 替代方案 | Alternatives Considered

1. **仅按需 cherry-pick 上游提交**：冲突面小，但难以系统跟进 `master`，长期 behind 会恶化；当前 behind 规模较小（**9**），cherry-pick 可作为补充而非主路径；若再次长期滞后再评估。
2. **长期不合并、完全分叉**：维护成本低短期看似可行，但安全修复、AI/搜索等新能力无法延续，与「独立下游但共享核心代码」目标不符。
3. **向上游提 PR 合并 fork 定制**：与产品决策（独立应用、不同 `applicationId`、无 Firebase、专用签名与 CI）冲突；且 `AGENTS.md` 已规定勿将 Trellis 等文件纳入上游 PR。
4. **自动化 GitHub Action 定时 merge**：可减少人工遗忘，但冲突与策略判断（Firebase、workflow 文件）仍需人工审查；可作为流程建立后的二期增强，而非替代文档化检查清单。

**建议采纳**：以文档化、可重复的 **手动/半自动 merge `upstream/master` → `release/rikka-arsucar`** 为主流程，配合 `CHANGELOG` 记录与发版前验证；必要时在 issue/PR 模板中增加「上游合并」勾选项。
