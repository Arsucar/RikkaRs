# 全项目全流程全链路深度审查与优化

## Goal

对 fork `release/rikka-arsucar`（RikkaHub Android）**所有模块、所有核心业务链路**进行一次极其细致、不设成本上限的审查，定位正确性缺陷、性能瓶颈、安全隐患与代码质量问题，并逐项深度优化到可验证通过。基线与当前 HEAD `10b33419`（v2.3.42，上游 2.4.2–2.4.5+ 已合并）一致。

> 用户明确授权：不记成本、并行全量审查、深度优化。API 余额次日到期，须在本轮完成尽量多的有效产出。

## Background

- 946 个 Kotlin 文件，10 个模块：`app`(721)、`ai`(50)、`highlight`(51)、`speech`(39)、`workspace`(20)、`search`(21)、`common`(22)、`document`(6)、`material3`(3)、`web`(3)。
- fork 不变量：`applicationId=me.arsucar.rikka`、无 Firebase、正式发版仅 `release-apk.yml`、#59 自动压缩 + 上游阶梯截断共存。
- 最近刚完成上游 #197 合并（2.4.2–2.4.5+），存在合并回归、重复实现、接口不一致的残余风险，是本次审查的重点诱因。

### 审查域矩阵（覆盖全部模块与链路）

| 域 | 模块/范围 | 关注链路 |
|----|-----------|----------|
| D1 Chat 核心链路 | app/data, app/domain, app/ui/chat, app/ui/message | 发送→transformer→上下文构建→provider→流式→输出 transformer→UI→持久化；MessageNode 分支、regenerate、删会话跳转 |
| D2 会话/助手管理 | app/data/model, repository, assistant | 会话/助手 CRUD、切换、固定、标题、tabs |
| D3 AI Provider 层 | ai/ 全部 | OpenAI/Google/Anthropic/Vertex/Moonshot/Grok；消息转换、流式、错误处理、重试、token 计数、阶梯截断、#59 压缩 |
| D4 工具/MCP/搜索 | app/tools, app/mcp, app/subagent, search/ | 工具注册、MCP 客户端、搜索 provider（Exa/Tavily/Zhipu/Bing/Brave/SearXNG…）、工具执行与结果注入 |
| D5 Workspace | workspace/ + app/workspace | 沙箱文件系统、shell 执行、bind mount、SAF、权限边界 |
| D6 文档解析 | document/ | PDF/DOCX/PPTX/EPUB 解析 |
| D7 语音 | speech/ | TTS/ASR |
| D8 Web 服务器 | web/ + app/web | Ktor server、静态前端托管、安全性 |
| D9 数据持久化 | app/data/db, dao, migration(46), datastore | Room schema、迁移正确性、仓库层、事务、缓存 |
| D10 备份/恢复/同步 | app/backup, webdav, s3, sync, export | 备份导出导入、WebDAV/S3、FTS、会话同步 |
| D11 UI/性能 | app/ui 全部 | 导航、状态管理、列表虚拟化、重组合、冷启动、内存 |
| D12 安全 | 全局 | API key 存储、网络、路径穿越、提示注入、沙箱逃逸、日志泄露 |
| D13 并发/资源 | 全局 | 协程、Flow、共享状态、线程安全、资源释放 |
| D14 编译/构建/CI | gradle, .github/workflows | 构建健康、依赖、无用代码、可维护性 |

## Requirements

- **R1** D1–D14 全部审查域均有结论，发现的问题按严重度分级归档（HIGH/CRITICAL 必修复，MEDIUM 有复现且可控则修复，LOW 记录）。
- **R2** 修复不改动 fork 不变量；不改生产代码之外的验收契约。
- **R3** 每个修复均为最小改动、符合 `.editorconfig`（Kotlin 4 空格/行长 120）、遵循既有模式。
- **R4** 聚焦单元测试覆盖新增逻辑；修复后 `--no-daemon` 编译通过、相关 JVM 单测通过。
- **R5** 生产代码冻结前做最终整合验证；有设备则 `installDebug` 真机验收。
- **R6** 输出一份审查报告（问题清单 + 已修复 + 已记录待办）落盘 `.trellis/tasks/<task>/review-report.md`。

## Acceptance Criteria

- [ ] AC1：D1–D14 每域一份审计结论；HIGH/CRITICAL 项修复率 ≥ 90%，未修复项有理由记录。
- [ ] AC2：`.\gradlew --no-daemon :app:compileDebugKotlin` 通过（合并资源+单测一次调用）。
- [ ] AC3：新增/修改逻辑配套聚焦 JVM 单测通过。
- [ ] AC4：fork 不变量复核通过（applicationId、无 Firebase、release-apk.yml、#59 压缩保留）。
- [ ] AC5：有设备则 `installDebug` 成功（必要时 `adb connect 100.99.129.110:5555` 重连一次）。
- [ ] AC6：`review-report.md` 归档，含问题清单、修复提交、验证证据、已知边界。

## Decisions

- **D1** 审查方式：按 D1–D14 拆域派发并行子代理审计（general 子代理，读+静态分析），主代理汇总定级。
- **D2** 优化优先级：CRITICAL/HIGH（正确性、数据丢失、安全、回归）→ MEDIUM（可复现的明确问题）→ LOW 仅记录。
- **D3** 编译纪律：仅最后一个检查/验证子代理允许跑 Gradle；全部命令带 `--no-daemon`；聚焦测试优先，全量 lint 不默认跑。
- **D4** 完成 app 模块功能改动后按 AGENTS.md 执行安装验收流程。

## Out of Scope

- 默认全量 `:app:lintDebug` / `connectedDebugAndroidTest`。
- 大规模 UI 重写、导航架构重构（除非有明确正确性/性能证据支撑的局部修复）。
- 向 `rikkahub/rikkahub` 提 PR。
- 改动 fork 身份、CI 入口、LICENSE 叙事。

## Notes

- 基线 commit：`10b33419`（v2.3.42 / 上游 2.4.2–2.4.5+ 合并后）。
- 子代理派发要求每域至少满足：减少主线程宽重读取 / 与其他域并行 / 独立核验之一。
- 空结果或只复述任务的子代理最多重派一次。
