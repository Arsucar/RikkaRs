# 全部 Open Issues 技术设计

## Boundaries

本父任务不直接实现业务代码。每条 issue 由独立子任务交付，父任务维护共同领域模型、依赖顺序、
跨任务一致性、发布和关闭证据。

## Single Source Of Truth

新增纯 Kotlin 工具能力领域层，统一拥有：

- 稳定 `ToolCapabilityKey`：内置工具使用命名空间 + tool name；MCP 使用 server UUID + tool name。
- `ToolCapabilityDescriptor`：来源、分组、配置态、可用态、有效态、默认审批和稳定 reason code。
- `ToolCapabilitySnapshot`：一次生成或一次 UI 刷新的冻结目录，不产生 I/O 副作用。
- `ToolPermissionPolicy`：`INHERIT/ALLOW/ASK/DENY`，解析器只允许收窄平台和父级安全边界。
- `ToolCapabilityReason`：机器码与本地化显示分离，供总览、诊断和连接状态复用。

能力发现阶段只读取已经存在的 Settings、Assistant、workspace 状态、MCP 已发现清单和 skill metadata。
显式连接测试是单独的有副作用边界，绝不混入 snapshot 构建。

## Data Flow

`Settings/Assistant/source state -> capability catalog -> frozen snapshot -> assistant policy -> delegate/profile intersection`

同一有效结果分别投影到：

1. `AssistantToolsPage` 的分组、计数、状态、诊断和策略编辑。
2. `ChatService.buildGenerationTools()` 的工具定义过滤与 `needsApproval` 覆盖。
3. `SubagentPermissionBuilder` 的父级上限和 profile 进一步收窄。
4. preset diff/apply planner 的稳定键匹配。

运行时实际构造仍由现有 tool factory 所有；catalog 不复制工具执行逻辑。测试直接比较 snapshot effective IDs
与最终生成工具 names，防止目录和运行时漂移。

## Persistence And Compatibility

- `Assistant` 新增默认空 map 的权限字段；旧 JSON 缺字段时等价于全 `INHERIT`。
- `Settings` 新增默认空 list 的用户 preset 字段和 schema version；旧备份保持可读。
- workspace tool approvals 与 MCP `needsApproval` 保持来源默认，不做破坏性迁移。
- 孤儿策略保留并标记；未知 enum/version 拒绝覆盖并给出稳定错误。
- preset 只存稳定键和策略，不存 schema、resource binding、token/header/cookie 或运行态。

## Issue-Specific Design

- #151：用纯决策函数处理 workspace 0/1/N 与 READY 状态；多个 workspace 打开选择器，取消和保存失败不改绑定。
- #153：先落 catalog/snapshot，再改现有页面；不新增入口。
- #154：provider request 前应用 DENY/ASK；当前 generation 冻结，改动下次生效。
- #155：reason chain 纯函数化；脱敏摘要采用 allowlist 字段，不序列化原始配置。
- #156：MCP manager 暴露单 server、按 revision 的显式探测；仅连接/认证/list tools，禁止 callTool。
- #157：先计算 diff/apply plan，再按 Assistant ID 逐目标原子更新并逐项报告；首版完全跳过资源绑定。

## Compatibility And Rollback

每个 issue 独立提交并在依赖满足后发布。若新 UI 回归，可回退页面投影而保留纯领域模型；若权限路径回归，
回滚 #154 提交即可恢复全 INHERIT 的旧行为；preset 字段为默认空且无资源绑定，回滚不会破坏助手配置。

## Security And Privacy

- DENY 在 provider request 前移除；ALLOW 不能降低硬编码强制审批或父级限制。
- 子代理最终权限为父助手、来源门控和 profile 的最小权限交集。
- 诊断、日志、模板、issue 评论均不得包含凭据、完整私有 URL 或 workspace 内容。
- 连接测试由用户显式触发，不执行业务工具、不写文件、不产生远端业务副作用。
