# Hook 事件与错误经验技术设计

## Architecture

`source final state -> HookEvent mapper -> frozen event context -> existing HookRepository exactly-once -> existing HookDispatcher -> Action Registry`

HookEvent 是非执行 DTO；Repository 仍是 run/lease/history 唯一所有者。旧 final-success gate 转换为 FINAL_ASSISTANT_RESPONSE_SUCCESS；其他来源只在各自终态边界生成标准事件。

## Identity And Compatibility

eventId 由 event type、conversation/logical turn、source/context identity 和规范化 evidence hash 稳定生成。schemaVersion 首版为 1；未知版本安全跳过并审计。ConversationHook 保持单一 trigger，旧 AFTER_ASSISTANT_RESPONSE_SUCCESS 解码为新成功事件。

## Source Boundaries

- Keyword：只扫描最终候选文本，纯函数返回规范化 evidence；未命中不调用 dispatcher。
- Tool failure：聚合终态记录，只有 final failed 且非 cancelled/recovered 才映射。
- Subagent：SubagentResult 终态持久化、pending tool 完成后冻结 contextId 快照。
- Memory：使用 source event frozen context 进入已有 Sync handler，不新增写路径。

## Security

payload 仅包含 allowlist 字段，文本/错误限长并去除 token/header/cookie/URL/工作区私有内容。事件、history、memory operation 均不存原始配置或完整工具输出。

## Rollback

旧 trigger 默认和旧 dispatcher 入口保留兼容映射。若新来源回归，可移除 source mapper 而不迁移或破坏既有 Hook/历史/记忆表数据。
