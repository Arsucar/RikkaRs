# Implementation Plan

1. 定义 context/status/config/cache API 与稳定错误类型，使用 fake clock 支持确定性 TTL 测试。
2. 实现 Mutex + access-order LRU、滑动 TTL、RUNNING 保护和原子 lease。
3. 扩展 `SubagentResult`、`spawn_subagent` schema、slim payload/metadata，保持旧 JSON/调用兼容。
4. 重构 `SubagentHost` fresh/reuse 流程：创建 context、scope 校验、追加 user task、chunk 增量快照和终态分类。
5. 更新根/嵌套 `ChatService` wiring 与 streaming metadata，使中断前主代理也能获得 context id。
6. 添加 cache 纯单测、Host 正常/中断复用测试、并发 lease、TTL/LRU、scope/profile、schema/序列化和上下文超限错误测试。
7. 运行聚焦 Subagent 测试和静态审查；最终检查代理统一运行 app 编译、必要 lint/test 与设备安装。
8. 更新 `subagent-runtime.md` 的新契约，评论并关闭 #104，完成 Trellis 归档。

## Risky Areas

- `SubagentHost.runToCompletion()` 的异常/取消路径和已有 budget summary 逻辑。
- 根/嵌套 spawn 的 owner/depth/workspace 参数传递。
- UI progress metadata 与最终工具结果的 context id 一致性。
- 大型多媒体/base64 消息的内存压力；本轮以 entry 上限控制，byte budget 留作后续。

