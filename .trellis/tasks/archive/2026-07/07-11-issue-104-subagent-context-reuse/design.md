# Design

## Data Model

`SubagentContext` 保存：context id、完整 `List<UIMessage>`、owner/scope（conversation、assistant、workspace/cwd、depth、profile）、created/lastAccess/expires 时间、status、usage 和最后错误摘要。工具记录不单独存储，避免与 `UIMessagePart.Tool` 漂移。

`SubagentResult` 扩展 `contextId` 与 `contextStatus`，工具返回的 slim payload 和 metadata 同步暴露这些字段。

## Cache Ownership and Lease

新增进程内 `SubagentContextCache`，由 app singleton `SubagentHost` 持有或注入。内部使用 `Mutex` 保护 access-order `LinkedHashMap`，只暴露原子操作：

- `createAndAcquire`
- `acquireForReuse`
- `updateProgress`
- `finish` / `interrupt` / `fail`

`acquireForReuse` 在同一临界区完成存在性、TTL、owner/scope、profile、状态检查、追加新 user task 并切换为 RUNNING，避免两个调用同时取得同一历史或取消发生在追加前留下半租约。

缓存入口、内部存储和返回值都对 `UIMessage`、parts、可变 metadata holder 与嵌套 `Tool.output` 做防御性深快照，避免流式列表或调用方后续修改污染历史。

## Runtime Flow

### Fresh Spawn

1. 解析/校验 profile 和权限边界。
2. 创建 context 并立即返回/写入可观察 metadata。
3. `runToCompletion` 每收到 `GenerationChunk.Messages`，先更新缓存，再调用 UI progress callback。
4. 正常结束标记 COMPLETED；provider/transport/stream 中断标记 INTERRUPTED，本地校验/编程失败标记 FAILED；取消在 `NonCancellable` 中保存 INTERRUPTED 后重新抛出，保留最后快照且不吞取消。

### Reuse

1. 通过 `reuse_context_id` 原子 acquire。
2. 校验调用方仍属于原 owner/scope，`profile_name` 与原 profile 一致。
3. 在缓存 messages 尾部追加 `UIMessage.user(task)`。
4. 以完整历史继续 `runToCompletion`；已有带 output 的 Tool part 仅作为历史，不重放。

## TTL and LRU

- expire-after-access，默认 1 小时；成功 acquire 和进度更新刷新访问时间。
- 默认容量 16，可由 cache config 覆盖。
- 淘汰最老的非 RUNNING 项；RUNNING 不过期、不淘汰。
- 若全部为 RUNNING，允许暂时超限，任一转终态后再 prune。

## Security and Compatibility

- opaque context id 不构成权限；每次复用必须校验 conversation、assistant、workspace/cwd、depth 和 profile。
- 配置变化时工具可按当前配置重建，但权限上限不得超过原 context 快照。
- `reuse_context_id` 是可选新字段，旧调用保持新建语义。
- 日志只记录 id、status、容量和事件，不记录消息正文、工具参数或输出。

## Context Length Errors

仓库暂无统一 tokenizer/context-window 元数据，不做伪精确预检。复用调用遇到提供商明确的上下文超限异常时，规范化为稳定错误，保留上下文并允许主代理回退为新建子代理。

## Rollback

缓存、schema/result 扩展和 Host wiring 以同一子任务提交交付。回退后旧 fresh spawn 行为和旧序列化仍可恢复，不涉及数据库迁移。
