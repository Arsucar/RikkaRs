# Issue 104 Architecture Research

- `UIMessage`/`UIMessagePart.Tool` 已完整保存工具 input/output，应直接缓存消息历史。
- `SubagentTranscriptStep` 有截断，只适合 UI/审计投影。
- `SubagentHost.runToCompletion()` 每个流式 chunk 可见最新 messages，但异常会在最终结果赋值前逃逸，造成中断成果丢失。
- `SubagentHost` 是 app singleton，适合进程内 cache。
- 关键边界：`SubagentHost.kt` 生命周期、`SubagentTools.kt` schema/结果、`SubagentProfile.kt` result、`ChatService.kt` 根/嵌套 wiring、`GenerationHandler.kt` 工具循环和异常序列化。
- 推荐使用原子 lease API、滑动 TTL、access-order LRU，并绑定 conversation/assistant/workspace/depth/profile 防止跨范围复用。

