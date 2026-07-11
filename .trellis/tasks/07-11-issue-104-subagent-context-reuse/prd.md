# 实现 Issue 104 子代理上下文缓存复用

## Goal

子代理正常完成、异常失败或提供商中断后保留完整执行上下文；主代理可用 `context_id` 追加指令继续同一上下文，避免重复探索和工具调用。

## Confirmed Facts

- `UIMessage` 已包含完整文本、推理和 `UIMessagePart.Tool` input/output，可作为唯一恢复历史。
- 当前 `SubagentTranscriptStep` 是截断后的 UI/审计投影，不能用于恢复。
- `SubagentHost.runToCompletion()` 能在每个流式 chunk 获得最新 messages，但异常逃逸时外层最终结果可能拿不到这些消息。
- `SubagentHost` 是 app singleton，适合持有进程内缓存；进程重启后缓存丢失符合本 issue 的内存方案。

## Requirements

- 每次新建子代理立即分配 `context_id`，并在流式消息更新时增量保存完整 `List<UIMessage>`。
- 正常完成、失败、提供商/流式中断、用户停止后上下文均保留，并记录 `COMPLETED`、`FAILED` 或 `INTERRUPTED` 状态。
- `spawn_subagent` 新增可选 `reuse_context_id`；复用时追加新的 user task，并以缓存消息作为 generation 初始历史，已完成工具调用不得重放。
- 复用必须绑定同一 root conversation、parent assistant、workspace/工作目录、depth 和 profile，禁止跨边界复用或扩大权限。
- 同一 context 只能由一个运行占用；并发复用立即返回明确 `CONTEXT_IN_USE` 错误。
- 缓存采用滑动 TTL（默认 1 小时）和 access-order LRU（默认最多 16 个），配置可由构造参数覆盖以便测试/部署调整。
- RUNNING 项不因 TTL/LRU 被淘汰；全部为 RUNNING 时可暂时超过上限，终态后再收敛。
- 过期、不存在、仍运行、scope/profile 不匹配、上下文过长均返回明确错误，同时避免在日志中输出消息正文或工具结果。
- `SubagentResult` 和工具 slim payload/metadata 暴露 `context_id` 与状态，异常路径也必须让主代理取得 id。

## Acceptance Criteria

- [x] AC1：正常完成后上下文仍在缓存，结果包含 context_id，可成功复用。
- [x] AC2：在若干流式 chunk 后中断时，最新消息被保留、状态为 INTERRUPTED，随后可复用。
- [x] AC3：复用追加新指令并继续已有历史，旧工具调用不会重新执行。
- [x] AC4：TTL 过期后复用返回稳定明确错误。
- [x] AC5：超过上限按最近最少访问淘汰非 RUNNING 项，绝不淘汰 RUNNING 项。
- [x] 单测覆盖并发 lease、scope/profile 校验、重复中断、LRU/TTL 和结果/schema 序列化。
- [x] 聚焦测试、app 编译和 Debug 安装验收通过。
- [ ] #104 留下实现与测试证据评论后关闭。

## Out of Scope

- 将缓存持久化到数据库/磁盘或跨 app 进程恢复。
- 新增用户界面；TTL/容量通过运行时构造配置，不新增设置页。
- 引入不可靠的字符数/token 估算；优先规范化提供商上下文超限错误。
- 缓存完整推理之外的独立隐式模型状态；恢复边界以应用已持有的 `UIMessage` 为准。
