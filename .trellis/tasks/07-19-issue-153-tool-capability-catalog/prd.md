# 统一助手完整工具能力 #153

## Goal

在现有助手工具页统一展示完整工具能力，并让页面统计与运行时有效工具集共享稳定目录。

## Requirements

- 覆盖内置、本地、workspace、memory table、skills、MCP、subagent 及动态工具数量。
- 提供稳定 ID、configured/available/effective、机器 reason、审批策略和无副作用 snapshot。
- 预览不得创建目录、连接服务、访问真实网络/shell、执行工具或改变配置；不新增顶级入口。
- workspace READY/只读规则复用 #151；memory 普通/表能力保持独立。

## Acceptance Criteria

- [ ] 动态 MCP/skill/local 展示按实际有效能力计数，不依赖固定 15 个工具。
- [ ] 每个不可用项都有稳定 reason code 和本地化文案。
- [ ] snapshot 与实际 generation 工具 names 有直接一致性测试。
- [ ] 静态/动态来源、delegate-only、全局门控和无副作用行为均有 JVM 测试。

## Notes

- 依赖 #151；阻塞 #154-#157。
