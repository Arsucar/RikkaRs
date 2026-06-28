# PRD: v2.3.6 Post-Audit Fixes

## 背景

v2.3.6 夜间审计修复已合入 `release/rikka-arsucar`（日志脱敏、子代理审批、Web 默认 localhost、workspace shell 拒绝列表、会话状态/持久化、Firebase 清理等）。代码审查发现仍有若干边界与一致性缺口，需在打 `v2.3.6` 标签前补齐。

## 目标

在不回退既有审计修复的前提下，补齐审查发现的 Blocker / Non-blocker 项，并配套测试与回归，最终合并到 `release/rikka-arsucar` 发版分支。

## 子任务（独立可验证）

| 子任务 | 范围 | 验收 |
|--------|------|------|
| **Blocker A** — `06-28-blocker-a-subagent-streaming` | 子代理流式结束时同步清理 `partialOutputText` JSON 内 `streaming` 字段，与 metadata `subagent_streaming` 一致 | 流式结束/失败/取消后，`spawn_subagent` 工具的 `text` JSON 与 metadata 的 streaming 状态一致（均为 false 或同步刷新） |
| **Blocker B** — `06-28-blocker-b-shell-hardening` | 加固 `evaluateShellCommand`：覆盖更多破坏命令、对 `rm -rf` 类变体收口、文档化「启发式拦截」语义、补充误杀与漏杀回归用例 | 高危命令被拒、workspace 内合法命令不被误杀；测试覆盖正反两类 |
| **Non-blockers C** — `06-28-nonblockers-cleanup` | LogPage 卡片 `redacted()` memoize、`ChatService` 状态锁写路径全量审计、`syncMessageNodes` 单测、baseline Firebase 残留说明 | 见子 PRD |
| **Tests D** — `06-28-tests-regression` | 为 A/B/C 补单测、运行 `test` + `lint`、确认编译通过 | 见子 PRD |

## 约束

- 不回退 v2.3.6 已有审计修复（脱敏、审批、Web 默认、删除 release.yml 等）。
- 不改公开发版 workflow 名称（仍为 `Release APK (arm64)`）。
- 不引入新的 Firebase / google-services 依赖。
- 子任务可并行；Blocker A/B 修复就绪后 Tests D 才能跑相关回归。
- 最终合并到 `release/rikka-arsucar`；**不**从 `local/agent-trellis-setup` 开 upstream PR。
- 遵循 `.editorconfig`：Kotlin 4 空格、行宽 120；XML/JSON 2 空格。

## 跨子任务验收（父任务负责）

1. `git diff --stat release/rikka-arsucar...HEAD` 仅含本任务范围内文件。
2. `./gradlew :app:compileDebugKotlin --no-daemon` 通过（最后一个子代理负责完整编译验证）。
3. `./gradlew test --no-daemon` 通过（含新增测试）。
4. `./gradlew lint --no-daemon` 无新增 error。
5. CHANGELOG `v2.3.6` 段落补「子代理流式一致性 / shell 启发式拦截」说明。
6. 不提交 `*.jks`、`.omc/`、`.trellis/tasks/06-28-post-audit-fixes-v2.3.6/**` 之外的本地工具文件。

## 不在本任务范围

- 重生 baseline profile（仅补充说明，不改 `startup-prof.txt`）。
- 重写 workspace sandbox 为基于 cwd 的强隔离（仅加固启发式规则）。
- i18n 全量审校（已在独立审计任务处理）。
