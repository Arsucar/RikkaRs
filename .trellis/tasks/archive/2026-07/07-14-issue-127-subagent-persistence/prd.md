# 完成 Issue 127 子代理上下文持久化

## Goal

在保留内存热缓存的同时持久化子代理上下文，使进程重启或 API 中断后可回顾和续跑。

## Requirements

1. Room 保存可完整重建 `SubagentContext` 的 scope、消息、usage、状态、错误与时间字段，并提供显式 migration。
2. 运行进度采用节流/串行化异步快照；终态可靠刷新；存储失败只记录并降级 memory-only。
3. 启动和 acquire miss 从 Room 恢复，RUNNING 降级 INTERRUPTED，继续执行既有 scope 授权校验。
4. TTL 60 分钟、容量 16 与 LRU 语义在持久层和恢复后保持。
5. 损坏/旧版本单条记录被隔离，不导致整个恢复失败。

## Acceptance Criteria

- [ ] 模拟清空内存/冷启动后消息完整恢复并可续跑。
- [ ] API 失败后的 FAILED/INTERRUPTED 上下文可 acquire。
- [ ] RUNNING 降级、scope mismatch、TTL、LRU、并发 flush 和写失败均有自动化测试。
- [ ] migration 测试证明旧数据库升级不丢现有应用数据。

## Notes

- 不新增 UI；复用现有 spawn_subagent 状态渲染。
