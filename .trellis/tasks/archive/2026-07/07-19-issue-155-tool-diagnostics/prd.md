# 增加助手工具可用性诊断 #155

## Goal

提供只读、无副作用的助手工具可用性诊断。

## Requirements

- 展示首要原因、完整判定链、修复导航和脱敏复制摘要。
- 至少覆盖 workspace、memory table、MCP、skills、delegate-only 和 assistant policy。
- 单个来源失败不得阻断其他来源；诊断不得连接、执行或改变配置。

## Acceptance Criteria

- [ ] 每个稳定 reason code 有单测与中英文文案。
- [ ] 摘要不含 token/header/cookie/完整私有 URL/workspace 内容。
- [ ] 部分来源失败仍可展示其余诊断。

## Notes

- 依赖 #153/#154；与 #156 共用 reason/status 展示模型。
