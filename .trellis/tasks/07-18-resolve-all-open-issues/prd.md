# 处理全部 GitHub open issues

## Goal

完成 `Arsucar/RikkaRs` 当前全部 8 条 open issue（#150-#157），使已交付事项有可追溯的关闭证据，
未交付事项按依赖顺序完成实现、测试、安装验收、发布记录和双语关闭评论，最终将 open issue 清零。

## Confirmed Facts

- 当前 open issue 仅有 #150-#157；全部无 assignee、无 milestone、无评论。
- #150 已由提交 `0f078ae1` 实现，并随 v2.3.33 发布；CHANGELOG 已记录，但 issue 尚未关闭。
- #151 是唯一 bug：工作区工具开关在多个工作区时静默选择首项，且 UI 可用数与运行时 READY 门控不一致。
- #152 是贯穿 #151、#153-#157 的跨层测试总纲，不应作为末尾一次性补测。
- #153 提供统一稳定工具目录和无副作用 capability snapshot，是 #154-#157 的基础。
- #154 提供助手级工具权限策略；#155/#156 分别提供只读诊断与显式连接测试；#157 最后提供模板和跨助手复制。
- GitHub token 缺少 `read:project`，暂时无法读取 Project 2 的真实状态和自定义优先级；规划优先级以风险与依赖推定。
- 工作分支为 `release/rikka-arsucar`；现有未提交的 `AGENTS.md` 修改不属于本任务，必须保留且不得纳入提交。

## Requirements

- 建立父任务与可独立验收的 issue 子任务；父任务只负责来源需求、依赖图、跨任务验收和最终整合。
- 批次 0：核验 #150 的实现、测试、发布事实，按仓库规范发布中文和英文交付评论后关闭，不重复开发。
- 批次 1：修复 #151，并同步完成 #152 的 workspace 0/1/N、取消、READY 状态与持久化测试。
- 批次 2：实现 #153 的稳定工具目录、configured/available/effective 规则及无副作用 snapshot，并同步补齐 #152 对应测试。
- 批次 3：实现 #154 的工具级权限策略、provider request 前过滤、审批覆盖、子代理最小权限交集及兼容持久化测试。
- 批次 4：基于统一 reason/status 模型实现 #155 诊断与 #156 MCP/工作区状态和显式连接测试；测试不得调用业务工具或泄露凭据。
- 批次 5：在前置模型稳定后实现 #157 preset CRUD、diff、跨助手复制和批量策略；默认不复制私有资源绑定或 secret。
- 每个功能子任务都必须包含对应的 #152 测试切片；最后补齐 UI/目录有效工具 ID 与 `ChatService` 最终 tool name 一致性的跨层断言。
- 所有 app 功能改动按仓库流程执行聚焦测试、合并质量检查、Debug 安装验收；不得默认运行 Android 仪器测试。
- 每条 issue 关闭前发布事实对应的独立中文评论与独立英文评论，包含目标分支、修复提交、正式版本（若已发布）、实际验证、定位和已知边界，并回读确认。
- 完成代码后更新 CHANGELOG、提交并推送到 `origin/release/rikka-arsucar`；正式发版遵循唯一 workflow `Release APK (arm64)`。

## Dependency And Delivery Order

`#151 -> #153 -> #154 -> #157`

`#153 -> #155 -> #156`

`#152` 贯穿所有实现批次；#150 作为已发布台账清理可先独立完成。

## Acceptance Criteria

- [ ] #150 的现有交付证据完成核验，双语评论已回读，issue 已关闭。
- [ ] #151 的 0/1/N workspace、取消、无效 ID、非 READY、保存失败行为满足 issue 验收条件。
- [ ] #153 的能力目录覆盖内置、本地、workspace、memory table、skills、MCP 和 subagent，且预览无 I/O 副作用。
- [ ] #154 的 DENY 不进入 provider request，ASK 进入审批，旧配置默认 INHERIT，子代理不能提权。
- [ ] #155 的 reason code/判定链/修复导航完整且复制摘要脱敏；单一来源失败不影响其他来源。
- [ ] #156 只执行连接、认证、工具发现，不调用 `callTool`；状态按 server ID/revision 隔离且生命周期无泄漏。
- [ ] #157 支持 preset CRUD、内置模板、diff、逐目标结果和批量四态策略；未知工具与私有资源安全处理。
- [ ] #152 的 table-driven JVM 测试矩阵覆盖持久化、能力解析、UI 状态、ChatService 装配和子代理权限，无 sleep 或外部服务依赖。
- [ ] 相关 Kotlin 编译、资源处理、JVM 测试和 AndroidTest 源码编译通过；有设备时 `:app:installDebug` 成功，否则按仓库规范提供 APK 交付证据。
- [ ] 每条 issue 的关闭评论与真实提交、版本和验证一致，所有 8 条 issue 最终均为 CLOSED。
- [ ] 最终提交不包含用户现有 `AGENTS.md` 修改或其他无关工作区文件。

## Out Of Scope

- 不创建新的“助手工具”顶级入口；在现有 `AssistantToolsPage` 演进。
- 不发布 universal、x86_64、AAB 或上游遗留 workflow 产物。
- #156 首版不主动执行 workspace shell health check，除非产品决策扩大范围并完成独立安全评审。
- 不把 workspace 文件、OAuth、header、token、cookie 或其他 secret 写入模板、日志、诊断摘要或 issue 评论。

## Open Product Decisions

- 已定稿：#154 采用 `INHERIT/ALLOW/ASK/DENY` 四态。
- 已定稿：动态工具消失后保留孤儿策略并在 UI 标记，允许手动清理；导入导出不得丢失。
- 已定稿：#157 MVP 完全禁止复制 workspace/MCP/skill 资源绑定及 secret，只复制稳定工具键对应的策略。
- 已定稿：内置模板为“只读研究”“代码工作区需审批”“最小权限”；首版不提供无法可靠保证的一次撤销。

## Notes

- 这是复杂父任务；开始实现前必须补齐 `design.md`、`implement.md`，并为独立 issue 建立子任务。
- Project 2 字段在获得 `read:project` scope 前不可核验，不能把推定优先级描述成项目已设置事实。
