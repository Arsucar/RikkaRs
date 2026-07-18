# 补齐工具能力跨层测试 #152

## Goal

建立稳定、无外部依赖的工具能力跨层测试矩阵。

## Requirements

- 覆盖持久化配置、能力解析、UI 状态、ChatService 装配和子代理权限。
- 使用 fake manager/repository 和虚拟时间，不访问真实网络、shell 或文件。
- 测试随 #151/#153-#157 分批落地，本子任务负责最终缺口和一致性断言。

## Acceptance Criteria

- [ ] workspace 0/1/N、MCP fake、权限优先级、子代理不可提权、旧 JSON round-trip 均覆盖。
- [ ] 至少一项测试直接比较 UI/目录有效工具 IDs 与 ChatService 最终 tool names。
- [ ] JVM 测试无 sleep 和外部服务依赖。

## Notes

- 最后执行，但不允许把各功能的安全关键测试拖到本子任务。
