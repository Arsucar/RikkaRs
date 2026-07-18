# 统一助手完整工具能力 #153

## Goal

在现有助手工具页统一展示并解析完整工具能力。

## Requirements

- 覆盖内置、本地、workspace、memory table、skills、MCP、subagent。
- 提供稳定 ID 和无副作用 snapshot，区分 configured/available/effective/reason/approval。
- 页面统计不得依赖固定工具数量，预览不得连接服务或创建目录。

## Acceptance Criteria

- [ ] 动态 MCP/skill 计数和所有来源门控与实际 generation 一致。
- [ ] 不可用项都有机器 reason 与本地化文案。
- [ ] 静态/动态来源、全局门控和 delegate-only 有测试。

## Notes

- 依赖 #151；阻塞 #154-#157。
