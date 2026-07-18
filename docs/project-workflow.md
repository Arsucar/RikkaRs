# GitHub Projects 任务工作流

公开项目：[RikkaHub 开发路线图](https://github.com/users/Arsucar/projects/2)，关联仓库 `Arsucar/RikkaRs`。

本项目是公开 Issue 和 Pull Request 的任务事实来源。不要把密钥、漏洞利用细节、用户数据或未脱敏日志放入公开字段。

## 状态流转

| 状态 | 进入条件 | 退出条件 / 下一状态 |
| --- | --- | --- |
| Backlog | 已收集但尚未达到 Definition of Ready（DoR），或暂未承诺排期 | DoR 满足且决定实施后进入“待办”；不再考虑则进入“已取消” |
| 待办 | DoR 已满足，优先级、类型、模块和验收条件清楚 | 开始实际工作时进入“进行中” |
| 进行中 | 已有人负责并正在实现、调查或编写文档 | 开 PR 且具备可审查状态后进入“审查中”；受阻时在 Issue/PR 中记录原因和解除条件 |
| 审查中 | 有非 Draft PR 等待评审、CI 或修改 | PR 合并后必须进入“待验证”；需要继续开发则回到“进行中” |
| 待验证 | 代码已合并，但尚未按验收条件验证 | 验收条件验证通过后进入“已完成”；失败则回到“进行中” |
| 已完成 | Definition of Done（DoD）全部满足且验证证据可追溯 | 发现回归时重开 Issue，并按新任务重新流转 |
| 已取消 | 明确决定不做、重复或已失效，并记录原因 | 决策改变且重新满足 DoR 时进入“待办”或“Backlog” |

**PR 合并不等于完成。** 合并只进入“待验证”；按验收条件完成与改动相关的检查后，才可进入“已完成”。关闭但未交付、重复或不再实施的任务应进入“已取消”，不能用“已完成”掩盖。

## Definition of Ready

任务进入“待办”前应满足：

- 问题、用户价值和范围明确，非目标已写明；
- 有可验证的验收条件；
- 已设置类型、模块和优先级；
- 依赖、数据迁移、兼容性和安全/隐私影响已识别；
- UI 任务包含入口、状态、导航、可访问性和本地化要求；非 UI 任务明确标注 N/A；
- 负责人可以直接开始工作，阻塞项已在 Issue/PR 中说明解除条件。

## Definition of Done

任务进入“已完成”前应满足：

- 关联 PR 已合并，验收条件逐项通过；
- 适用的单元测试、UI/集成测试、静态检查和构建通过；未执行或无法验证的项目已明确记录；
- 数据库、备份恢复、配置或 API 兼容性已验证，必要迁移可回滚；
- UI 改动已检查深浅色、关键状态、本地化和可访问性；
- 文档、变更日志、已知限制和回滚方案已更新；
- 已检查提交、日志和截图不含敏感信息；
- 实际执行的命令/检查及结果已记录在 Issue/PR 中。

## 字段约定

除 GitHub 内建 `Status` 外，仅使用以下三个自定义字段：

| 字段 | 值 / 用法 |
| --- | --- |
| 优先级 | `P0` 阻断发布或严重数据/安全事故；`P1` 当前版本关键；`P2` 常规计划；`P3` 改进或候选 |
| 类型 | `Feature`、`Bug`、`Refactor`、`Performance`、`Documentation`、`Build-Release`、`Research` |
| 模块 | `UI-Compose`、`Domain`、`Data`、`Provider-Model`、`Conversation`、`Memory`、`Tool-MCP`、`Database`、`Backup-Restore`、`Settings`、`Build-Release`、`Cross-module` |

跨多个模块但有明确主模块时选择主模块；只有无法合理归属时使用 `Cross-module`。

## 自动化与安全边界

- GitHub Projects 内建自动化优先；仓库工作流只作为自动入板基础设施。
- `.github/workflows/project-intake.yml` 对新建、重开或转入的 Issue，以及新建、重开或转为 Ready 的 PR 自动入板。
- 外部 fork 的 PR 使用 `pull_request_target` 获取受信任的基础分支工作流，但该工作流**不 checkout、不构建，也不执行 PR 中的任何代码**。
- Action 使用固定 commit SHA，不解析或执行事件正文。工作流仅授予 `contents: read`；Project 写入只通过仓库 Secret `ADD_TO_PROJECT_PAT`。
- 自动入板后由维护者按本页规则设置字段和状态。为避免错误完成任务，暂不通过 Action 自动修改状态。
- 手动触发仅用于验证 Secret 是否可读取；没有 Issue/PR 事件时不会添加内容。

## 仓库所有者 UI 待办

GitHub API/CLI 当前不能可靠地创建项目视图、编辑内建 `Status` 的选项或配置 Projects 内建工作流，因此以下项目不能宣称已自动完成：

1. 在 Project **Settings → Status** 将状态选项配置为：`Backlog`、`待办`、`进行中`、`审查中`、`待验证`、`已完成`、`已取消`（当前选项仍包含 GitHub 默认的 `Todo / In Progress / Done`）。
2. 在 Project 中创建并保存所需 Table/Board/Roadmap 视图；建议 Board 按 Status 分组，Table 显示三个自定义字段。
3. 在 **Workflows** 配置安全的内建规则：新加入项默认进入 `Backlog`；PR 转为 Ready 时进入“审查中”；PR merged 时进入“待验证”。不要启用“PR merged → 已完成”。
4. 如内建自动入板规则已覆盖本仓库，可停用仓库 `project-intake.yml`，避免重复触发（Projects 本身会去重同一 Issue/PR）。
5. 由仓库所有者创建最小权限、可访问该用户 Project 的 fine-grained PAT，并在 **Repository settings → Secrets and variables → Actions** 保存为 `ADD_TO_PROJECT_PAT`。不要把 Token 写入文件、日志或命令行历史；配置后用 `workflow_dispatch` 验证。

## 日常维护

- 新 Issue 先进入 Backlog；整理时补齐三个自定义字段和 DoR。
- 状态变化或工作受阻时，在 Issue/PR 中记录原因和后续行动。
- Draft PR 仍可入板，但只有 Ready for review 才进入“审查中”。
- 合并 PR 后检查关联 Issue 是否仍开放，并将任务移到“待验证”。验证者而非合并动作负责最终完成。
- 每个 Release 前检查 P0/P1 和“待验证”积压。
