# 夜间审计修复（父任务）

## 概述

协调 5 个可并行子任务，修复夜间无人值守审计（`.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`）中的 P0/P1 项。父任务**不直接改业务代码**，仅负责范围划分、子任务映射与整体验收。

## 背景与约束

- 审计性质：只读；修复基于报告中的 file:line 与评级。
- 基线分支：`release/rikka-arsucar`。
- 父任务优先级以子任务 P0 安全/合规为准；未列入子任务范围的 P2/P3 不在本父任务验收范围内。

## 子任务

| Slug | 目录 | 范围（审计条目） |
|------|------|------------------|
| `audit-security` | `06-28-audit-security` | **P0 #1** 子代理 sandbox 绕过审批流（A-01）；**P0 #2** LogPage 详情明文（L1）；**P0 #4** tag 与 `versionName` 对齐（F-01）；**P1 #9** Web JWT 默认与 LAN 暴露（G-01）、Workspace shell 无白名单（G-02） |
| `audit-state` | `06-28-audit-state` | **P0 #3** 会话加载未清 stale `subagent_streaming`（S1）；**P1 #10** `SubagentToolUIs` `remember` 依赖（R1）；**P1** CAS 限流（S3）及报告批次 B 相关持久化/清理项（如 S2、DS-1，在子任务 PRD 内细化） |
| `audit-i18n` | `06-28-audit-i18n` | **P0 #5** ImgGenPage 硬编码 a11y（L-01）；**P0 #6** `safe_mode_enter_app`（L-03）；**P0 #7** ja/ko/ru/zh-TW 缺 102 keys（L-02）；可选纳入报告 P1 i18n（L-04～L-07）由子任务 PRD 界定 |
| `audit-ui` | `06-28-audit-ui` | **P1 #11** ModelList tag 与收藏不一致（E-P1-1）；**P1 #12** 助手扩展孤立 ID 不 prune（E-P1-2）；**P1 #13** `ThinkTagTransformer` 单测（E-P1-3） |
| `audit-infra` | `06-28-audit-infra` | **P1** F-02～F-04：release workflow 清理、`release-apk` 与文档触发策略对齐、tag push 与 dispatch bump 行为一致 |

## 依赖关系

- **子任务之间无硬依赖**，默认可并行。
- 若某子任务在实现中发现与另一子任务文件冲突，在对应子任务 `implement.md` 中记录合并顺序，**不**在父任务内实现。

## 父任务职责（明确不做）

- 不提交功能修复 PR/补丁。
- 不替代子任务做 `design.md` / `implement.md` 级技术方案（复杂子任务各自维护）。
- 不扩展审计报告未映射到上表的范围，除非新开子任务并更新本 PRD。

## 整体验收标准

- [ ] 上述 5 个子任务均达到各自 PRD 验收标准并归档或标记完成。
- [ ] 现有功能无已知回归（聊天、子代理、设置、Web、发版相关路径 smoke 由子任务 check 覆盖，父任务做汇总确认）。
- [ ] `./gradlew lint`（或仓库约定的 lint 门禁）通过；子任务引入的测试/单测在其 check 中已执行。
- [ ] P0 项（#1～#7 及 F-01）在对应子任务中均有可验证修复或 documented 例外（须产品/安全同意）。

## 参考

- 审计总报告：`.trellis/tasks/archive/2026-06/06-28-nightly-audit/audit-report.md`
- 子代理明细：`research/subagent-*.md`（同归档目录）