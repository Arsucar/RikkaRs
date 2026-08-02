# batch-issues-plan: 6 open issues 规划（5 个实际子任务）

## Goal

为本仓库当前 6 个 open issue（4 enhancement + 2 bug）产出完整的 Trellis 规划产物（PRD / design / implement + 交付矩阵），为本轮之后的逐个实现会话提供事实基础。本轮**仅规划，不写任何生产代码，不做任何装设备动作**。

## 范围与子任务树

6 个 issue 中 #217 与 #216 标题、宏格式、MVU 协议、助手级开关粒度高度重叠（#216 描述里明确点出与 #217 同一变量系统，仅"全局变量可选后续"一行差异），合并为 1 个规划子任务，并在交付矩阵中说明双 issue 评论指向关系。最终产出 **5 个子任务**，按 bug 先于 enhancement、同类型按编号倒序排列：

| 序 | 子任务 | 对应 issue | 类型 | 备注 |
|----|--------|-----------|------|------|
| 1 | `08-02-issue-214-reasoning-dialect` | #214 | bug | **已有 PR #221**（`fix/reasoning-dialect-214`，Closes #214）。本任务=审阅/合入/CI·单测验证，**不从零实现** |
| 2 | `08-02-issue-218-preset-switch-jank` | #218 | bug | 预设切换开关无乐观状态 + 全量覆盖写、翻转等磁盘往返 |
| 3 | `08-02-issue-220-checkpoint-cache` | #220 | enhancement | 生成中每 N 步落盘检查点 + 恢复提示 + 设置开关 |
| 4 | `08-02-issue-219-keepalive-fgs` | #219 | enhancement | 生成期间常驻前台服务保活 + chat_keepalive 渠道 + 设置开关 |
| 5 | `08-02-issue-217-216-conversation-variables` | #217 + #216 | enhancement | SillyTavern 变量宏 + MVU + 助手级实验项开关 + 右抽屉 UI |

## 跨子任务约束

- **#202 规范**：所有写设置项禁止"读快照-全量覆盖写"，必须走 DataStore transform 原子写。#218、#220、#219、#217/#216 涉及 `Settings` 新增字段或 `Conversation` 修改，全部遵守此规则（issue 218/220/219/217 都已显式引用 #202）。
- **#215 依赖**：#217/#216 的助手级实验项开关（`featureId: variable_system`，`Scope.Assistant`）依赖 #215"可热插拔的实验性功能配置页面"。#215 不在本轮规划范围（用户已选仅做这 6 个 issue，#215 暂置），#217/#216 子任务 PRD 需明确：
  - **过渡期方案**：如 #215 先未落地，#217/#216 先为 `Assistant` 增加最小布尔字段承载开关（仿 `toolPermissions` 持久化），消费点读取方式不变；**#215 落地后迁移为实验项，零消费点改动**。
  - #215 与 #217/#216 的对接契约写入 `design.md` 的"依赖与契约"一节。
- **依赖关系**：#220 与 #219 互补（一保活、一兜底恢复），可独立实现无硬依赖；#217/#216 的开关消费点依赖 #215 或过渡最小字段；其余子任务之间无依赖、可独立实现。

## 本轮交付矩阵（Acceptance Criteria）

- [ ] AC1 父任务 `prd.md` 含子任务表、跨任务约束、依赖关系、交付矩阵（本文件）。
- [ ] AC2 每个子任务目录下有：`prd.md`（需求/约束/验收）、`design.md`（技术设计/边界/契约/数据流/回滚形态）、`implement.md`（有序 checklist + 验证命令 + review 门 + 回滚点）。
- [ ] AC3 每个子任务 `implement.jsonl` / `check.jsonl` 至少含指向本任务涉及代码位置与相关 spec 的清单（空也可，但需存在文件或显式说明）。
- [ ] AC4 交付矩阵含每个子任务的：优先级、依赖、工作量预估（粗粒度，如 S/M/L 或半天/1-2 天/3+ 天）、风险点、是否需要新权限/新服务/数据迁移。
- [ ] AC5 父任务 `design.md` 给出推荐实现顺序与并发可并行度（哪些子任务可在不同会话并行实现而不冲突）。
- [ ] AC6 所有产物产出后调用 `task.py current` 确认仍停在父任务 planning，不擅自推进到 in_progress；实现会话由用户逐个启动。

## Out of Scope（本轮不做）

- 不写任何生产代码、不跑 Gradle、不装设备。
- 不动 #215（"可热插拔的实验性功能配置页面"），仅作 #217/#216 的依赖契约声明。
- 不实际修复 issue、不关闭 issue、不发 GitHub 评论。仅产出规划文档与 Trellis 任务结构。

## Notes

- 子任务标题用 issue 原标题前缀（`bug:` / `feat:`）保留可追溯性。
- task.json 标题因 Windows 控制台 GBK 解码显示乱码，不影响 git/文件内容（UTF-8），后续提交时正确。
- 5 个子任务均属于"复杂任务"（跨模块、需设计取舍、有大范围 Compose/数据/服务改动），必须三份产物齐全后才允许 `task.py start`。
