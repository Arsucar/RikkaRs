# Design: Merge upstream tip `8349ef25` into `release/rikka-arsucar` (#197)

## Architecture / Boundaries

- **单任务整并**（不拆 parent/child）：交付物是一次受控 merge + fork 身份修复 + 可编译树；无多份可独立发版的产品切片。
- **基底**：本地 `HEAD`（含 #191–#196，`2.3.41`/`203`），**不是**过时的 origin `6ffb6bf` / #197 正文。
- **源**：`upstream/master` @ **`8349ef25`**（D1）。
- **策略**：优先 **一次** `git merge upstream/master`（或等价 `8349ef25`）；若冲突爆炸再按主题分批（AI → app/workspace/MCP → highlight/deps），仍以同一 tip 为终点。
- **权威不变量**：`docs/RIKKA_ARSUCAR_FORK_AND_CI.md` + PRD Decisions D1–D3。

## Data flow / Merge contract

```
upstream/master (8349ef25)
        │
        ▼
  three-way merge  ←──  local HEAD (fork features + 2.3.41/203)
        │
        ▼
  post-merge gate:
    · applicationId / version 仍 fork
    · 剥离 Firebase / google-services
    · LICENSE/README 叙事 ours (D3)
    · #59 压缩上下文 + 上游阶梯截断 语义合成
    · highlight 取上游原生实现 (D2)
        │
        ▼
  compile → focused tests → installDebug（有设备）
```

### 冲突热区与合成规则

| 区域 | 规则 |
|------|------|
| `app/build.gradle.kts` | **ours 身份**：`applicationId=me.arsucar.rikka`；`versionCode`/`versionName` ≥ 合并前本地（203 / 2.3.41），**禁止**上游 172/2.4.5；去掉 firebase 插件与依赖；可吸收上游与 fork 无关的 build 改动（若有） |
| `gradle/libs.versions.toml` | **可吸收** AGP/Kotlin/material3 等升级（上游约 agp 9.3.1、kotlin 2.4.10、material3 alpha25）；**拒绝**重新引入 `google-services` / `firebase-*` catalog 与 plugins |
| root / app plugins | 不 apply `google-services` / `firebase.crashlytics` |
| `.github/workflows/` | **保留** fork：`release-apk.yml`、`pr-apk.yml` 等；上游仅有 `daily-build` / `close-blank-issues` 时可共存（#68）；**勿删** `release-apk.yml` |
| `LICENSE` + 根 README* | **ours**（D3 分段双许可 + RikkaRs 叙事） |
| `highlight/` | **theirs 能力**（D2 原生高亮）；fork 既有模块被上游实现替换；保留 settings include |
| `Assistant` / 上下文 | **合成**：保留 fork **#59** 自动压缩阈值/UI（`compressTargetTokens`、threshold、keep recent 等）；吸收上游 **阶梯截断**（`2d61ba95`）与 tip i18n「无限制」；注意上游曾 `b0698524` 移除 `contextMessageSize` 后又加阶梯——以 tip 语义 + fork 压缩为准，禁止只留一边 |
| `McpManager` 系 | 吸收上游拆分与错误详情；保留 fork 对 MCP 的既有修复 |
| `ChatService` / `ChatVM` / `BackupVM` | 双边均改：优先保留 fork 行为（backup 生命周期、Chat 并发等），再 cherry 上游语义（删会话后再跳转、助手归属等） |
| `PlaceholderTransformer` | 吸收移除 cur_time 等上游改动时，确认与 fork 模板/时间行为不冲突 |
| `PresetTheme*` | 吸收极简白 / Claude / 网格选主题 |
| DI / `AppModule` / `ChatVM` | merge 后扫 Firebase 引用，保持删除态 |
| skills / `.agents` / `.claude` 大体量文档 | 可随 tip 进入；不单独产品验收 |

## Compatibility

- **versionCode**：上游 172 << fork 203 → 合并后仍 ≥ 203；发版 bump 另走 CI/流程，本设计不强制在 merge commit 再 +1（实现清单可选「合并后保持 2.3.41/203，发版时再 bump」）。
- **API / 行为**：助手级搜索存储、Kimi K3、MCP 结构、workspace SAF/预览等以合入上游为准；fork 独有 stats/memory/draft 等尽量不动，冲突时最小合成。
- **AGP/Kotlin 升级**：接受上游版本线风险；编译失败则在 merge 分支内修，不回退 tip。

## Trade-offs

| 选择 | 理由 |
|------|------|
| 一次 merge 到 tip | behind=0；比 56 次 cherry-pick 少漏依赖 |
| highlight 全收 | D2；#197 wip 暂缓已过时 |
| LICENSE ours | D3；产品策略非代码同步 |
| 不拆 child task | 单次集成验收；分批只是执行战术 |

## Rollback

- merge 未 push：`git merge --abort` 或 `git reset --hard` 到 merge 前 HEAD。
- 已局部提交未 push：`git reset --hard <pre-merge>`。
- 已 push：revert merge commit（需 `-m 1`）或新 commit 修复；**禁止** force-push 除非用户明示。
- 记录 pre-merge SHA 于 implement 执行日志。

## Out of design scope

- 向上游 PR、改 keystore/Secrets、全量 lint 基线治理、默认 instrumented tests。
