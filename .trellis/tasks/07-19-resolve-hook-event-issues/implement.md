# 执行计划

- [x] 定义 HookEvent/HookEventType、稳定 ID、schema 校验和旧 trigger 兼容。
- [x] 将 HookDispatcher/Repository 接入标准事件并保持 exactly-once/lease/timeout。
- [x] 编辑器单选四种事件，关键词 trigger 显示简单输入。
- [x] 实现关键词纯预筛、规范化、限长和零请求门控。
- [x] 从主/子代理工具终态映射最终失败事件，抑制重试/自愈/取消。
- [x] 从 SubagentResult/contextId 生成终态事件与上下文完整性。
- [x] 将错误事件接入现有 SYNC_MEMORY_TABLE prepare/execute 和 sourceEventId 幂等。
- [x] 补序列化、路由、关键词、失败状态、子代理终态、记忆事务和 UI 测试。
- [x] 合并运行资源、Kotlin、JVM、AndroidTest 源码检查并安装 Debug。
- [ ] 提交推送、双语评论回读、关闭 #158-#162、更新规格并归档。
