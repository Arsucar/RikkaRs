# PRD: 处理全部开放 issues（2026-08-09 batch）

## 背景

仓库 `Arsucar/rikkahub` 在 `release/rikka-arsucar` 上共有 **8 个 open issue**，需全部实现/修复并关闭。

## 目标

按依赖与风险处理全部开放 issue，每条有独立可验收交付；完成后按仓库规范发中英关闭评论并关闭 issue。

## Issue 地图（子任务）

| 优先级 | Issue | 子任务 | 类型 | 依赖 |
|--------|-------|--------|------|------|
| P0 | #245 | `08-09-issue-245-config-only-switch` | bug | 无 |
| P0 | #248 | `08-09-issue-248-streaming-jank` | bug/perf | 无 |
| P1 | #247 | `08-09-issue-247-bind-mount-browser` | feat | 无 |
| P1 | #258 | `08-09-issue-258-trusted-write-roots` | feat | 无 |
| P1 | #259+#242 | `08-09-issue-259-remove-mode-injection` | feat | #242 被 #259 覆盖（Reference→Custom 迁移，非 tombstone） |
| P2 | #237 | `08-09-issue-237-status-feedback` | feat/UI | 无 |
| P2 | #233 | `08-09-issue-233-typography` | feat/UI | 无 |

## 权威关系

- 各 issue 正文为功能/验收权威来源。
- **#259 最终定调覆盖 #242**：删除独立注入时 Reference 必须迁移为 Custom 快照，不采用 #242 的 tombstone 方案；#242 随 #259 一并关闭。
- 实现顺序建议：#245 → #248 热路径修复 → #247/#258 → #259（含 #242）→ #237/#233。

## 跨子任务验收

- [ ] 8 个 open issue 均有对应实现与验证证据
- [ ] #242 与 #259 合并关闭，无双路径残留
- [ ] 每条关闭评论含中英交付 + 验收勾选清单
- [ ] 生产代码变更最终装到设备（有 adb 时 installDebug）

## 非目标

- 不治理历史 lint 基线
- 不默认跑 connectedDebugAndroidTest
- 不顺手处理已关闭 issue
