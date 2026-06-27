# 夜间无人值守审计：自独立于上游以来变更综合审查

## Goal

对本 fork（`release/rikka-arsucar`）自分支独立于上游以来所有 fork-only 变更进行**只读**综合代码审查，
产出可分级处理的优化建议清单。**本任务不修改任何代码**，所有建议以报告形式落到 journal
与 audit 报告里，明日用户起床后人工决策。

## Background

- 本仓库由上游 `rikkahub/rikkahub` 派生，远端 `origin` = `Arsucar/rikkahub`，`upstream` = 上游。
- 自独立以来已积累：
  - 大量 commit（list-card 统一重构、search 增强、sub-agent 全链路 7 个子任务、log 重写、
    Provider tags、model filter、i18n 抽取、CHANGELOG/* 中文工作流、CI/release 派生流程）。
- 工作区目前 5 文件脏（SubagentTools/ChatService/ModelList/ChatMessage/SubagentToolUIs），
  来自最近一轮 review-fixes 末尾。
- 现有 `06-28-review-fixes` 任务已闭环（commit `8be9f419` 已合并），范围仅限 5 条具体 fix，
  不替代本审查。

## Scope

纳入审查范围（自独立于上游以来的 fork-only 变更）：

1. **Subagent 全链路**：数据模型 / 权限层 / 运行时 / UI（chat + settings + global config）/
   日志 / builder / ext sections 整合。
2. **i18n**：中文硬编码抽离、en/zh/zh-rTW 同步、`values/strings.xml` 中是否有非英文串遗留、
   locale-tui 调用一致性。
3. **ChatService**：流式回调、跳转式取消、转录清理、job lifecycle、异常传播。
4. **Provider tags / model filter / ext sections / 多段 think-tag**。
5. **Log redaction / LogsTool / LogPage export / DataStore 持久化**。
6. **CHANGELOG.md / AGENTS.md release workflow / docs/**。
7. **CI / 发布派生流程**（arm64-only、applicationId 重写、Firebase 移除、material-color-utilities 子模块）。
8. **ListCard / ReorderableSwipeableItem / BaseListCard 抽象**：dedup 后是否复用一致。
9. **跨模块的 Provider 复制函数 (`copyProvider`)**：`sealed` 类外是否一致。
10. **`web-ui` 子模块**：`preBuild` 阶段构建对 release 派生是否稳健。

## Out of Scope

- 任何代码修改（仅审查与建议）。
- 重新跑 `./gradlew assembleDebug` / lint / test（审阅为只看代码，可消耗 ≤ 3 min 内轻量验证命令除外）。
- 与上游差异本质相同的代码（仅本 fork 独有的差异才纳入）。
- `web-ui/` 内部 React 代码（只审查 web 模块 Kotlin 入口）。

## Requirements

### R1 - 产出 fork-only 变更清单
- 用 `git log --no-merges upstream/master..HEAD --oneline` 列出所有 fork-only commit。
- 按主题归类（subagent/i18n/UI/log/docs/ci/...），并标注每类的 commit 数与文件数。

### R2 - 派生审查代理（并行 7 路）
- 派发以下 7 路 `trellis-research` 子代理，每路**只读**、产出 markdown 审计意见：
  1. `subagent-data`：`data/ai/subagent/` 下 SubagentProfile / SubagentTools /
     SubagentProfileForm / SubagentRepository / global store / 迁移逻辑。
  2. `subagent-runtime&permissions`：`SubagentRuntime*`、`PermissionGate`、`tool approval` 链。
  3. `subagent-ui-chat` + `subagent-ui-settings`：卡片 UI、Stream step UI、ChainOfThought、
     Composer 注入点、a11y。
  4. `i18n`：`values/strings.xml`、`values-zh*/`、`locale-tui` 调用一致性、未抽取的中文常量化。
  5. `chat-service`：`service/ChatService.kt` 流式与取消、工具审批 race、stale streaming 清理。
  6. `log-redaction`：`LogsTool` / `LogPage` / DataStore / redact 脱敏策略。
  7. `infra-docs-ci`：CHANGELOG / AGENTS.md release workflow / `docs/` / CI yaml / arm64-only。

### R3 - 评级体系
- 每条建议打 **P0/P1/P2/P3**：
  - **P0**：崩溃 / 数据丢失 / 持久化损坏 / 安全 / 已禁用行为意外开启。
  - **P1**：影响发布正确性的运行时 bug / 显著性能问题。
  - **P2**：可维护性，一致性，重复代码，命名 / 模块边界。
  - **P3**：纯风格 / nit。

### R4 - 报告产物
- 在 `.trellis/tasks/06-28-nightly-audit/audit-report.md` 落总报告（含全量建议）。
- 同报告摘要 + 明早行动建议追加写到
  `.trellis/workspace/your-name/journal-1.md` 末尾，便于起床即阅。

### R5 - 任务结束
- `task.py archive 06-28-nightly-audit`，复核未改任何源码（`git diff --stat` 为空，stash 已恢复）。
- 工作区 5 脏文件**还原**到审查前状态（先 stash 0 再 pop）。

## Acceptance Criteria

- [ ] `git log --no-merges upstream/master..HEAD --oneline | wc -l` 与 commit 清单吻合。
- [ ] 7 路审查代理全部返回 markdown 审计意见文件。
- [ ] 报告按 R3 评级，含文件路径:行级 引用。
- [ ] journal 末尾追加 ≥ 1 段「明早行动」摘要。
- [ ] 工作区状态与审查开始前 `git status` 完全一致（dirty paths 不增不减）。
- [ ] `git diff --stat` 排除 `*.log` 与预期外路径为空（或仅保留已 stash 的 pop）。

## Risks & Mitigations

- **风险**：审阅粒度过细 → 单次过度耗时。**Mitigation**：每路代理 prompt 锁定 30 分钟预算，
  超时则保留 partial 报告，标注 incomplete。
- **风险**：子代理误修改代码。**Mitigation**：dispatch prompt 显式禁止写 edit/write 工具，
  仅允许 read/grep/glob/bash（仅查询类命令）。
- **风险**：评估依赖于 build 验证。**Mitigation**：除非 bug 强证据，否则不跑 gradle；
  所有结论以源代码 + 静态分析依据为准。
