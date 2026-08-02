# batch-issues-plan design（父任务交付矩阵）

## 本轮范围

仅规划产物；不写生产代码、不 start 子任务实现、不关 issue、不装设备。

## 子任务交付状态

| 序 | 子任务 | issue | prd | design | implement | 工作量 | 依赖 | 新权限/服务/迁移 | 主要风险 |
|----|--------|-------|-----|--------|-----------|--------|------|------------------|----------|
| 1 | `08-02-issue-214-reasoning-dialect` | #214 bug | ✓ | ✓ | ✓ | **S** 审 PR（已有 **PR #221**） | 无 | 无 | PR 作者未本地跑测；合入前跑 `:ai` 单测；o-series 对 `"none"` |
| 2 | `08-02-issue-218-preset-switch-jank` | #218 bug | ✓ | ✓ | ✓ | **M** ~1d | #202 模式 | 无 | 全屏重组仍掉帧；并发 mutex |
| 3 | `08-02-issue-220-checkpoint-cache` | #220 feat | ✓ | ✓ | ✓ | **L** 2–3d | 无硬依赖 #219 | Settings 字段；Conversation 元数据 | FTS 成本；杀进程矩阵 |
| 4 | `08-02-issue-219-keepalive-fgs` | #219 feat | ✓ | ✓ | ✓ | **M–L** 1.5–2d | 无硬依赖 #220 | FGS service + 渠道 | OEM 拒 specialUse；计数泄漏 |
| 5 | `08-02-issue-217-216-conversation-variables` | #217+#216 feat | ✓ | ✓ | ✓ | **L+** 3–5d | #215 过渡字段 | Room 字段；分支 snapshot | 分支语义；ST 边界；管线顺序 |

## 推荐实现顺序（会话级）

1. **#214 / PR #221** — **优先审阅并合入**（代码已在 `origin/fix/reasoning-dialect-214`，MERGEABLE）。本地跑 `:ai` 单测 → merge → 关 #214。**不要重写实现**。
2. **#218** — 下一实现项（性能 bug，#202 模式）。
3. **#219** 与 **#220** — 可**不同会话并行**（文件面几乎不重叠）。若单人串行：先 #219 再 #220，或相反均可。
4. **#217+#216** — 最大；建议 #215 有结论或接受过渡字段后再开；可再拆宏/MVU/UI/分支 child。

## 并行度

| 组合 | 可并行？ | 说明 |
|------|----------|------|
| #214 ∥ #218 | 是 | 模块几乎无交集 |
| #219 ∥ #220 | 是 | 注意 ChatService 钩子 thrash，合并前互审 |
| #217 ∥ 任一 | 慎 | 动 transformers/Conversation/Assistant，易冲突 |
| 全 5 并行 | 否 | 内存/评审/合并成本过高 |

## 跨任务不变量

- 一切 Settings/Assistant 写遵守 **#202 transform 部分写**。
- 实验功能默认 **关**、关闭零副作用。
- 正式实现会话结束：聚焦测 → 合并最终 compile/test → **installDebug**（app 改动）；#214 可仅 ai 单测。
- 关 GitHub issue：中英双评论（解决点/验证/定位/边界），#217+#216 互链。

## 父任务完成定义

- [x] 5 子任务均有 prd/design/implement
- [x] 本交付矩阵
- [ ] 用户审阅规划；逐个 `task.py start` 开实现会话（**不在本轮**）

## 回滚

规划文档均可删；无生产影响。
