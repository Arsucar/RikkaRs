# 增加工具连接测试与运行状态 #156

## Goal

展示工具运行状态并提供用户显式触发的安全连接测试。

## Requirements

- 聚合现有 MCP status 与 workspace shellStatus。
- MCP 测试仅连接、认证和 list tools，禁止 callTool；workspace 首版仅展示只读状态。
- 按 server ID 去重并用配置 revision 防止过期结果覆盖新配置。

## Acceptance Criteria

- [ ] 成功、0 工具、待授权、网络和协议错误可区分。
- [ ] 并发、revision、页面生命周期和日志脱敏有测试。
- [ ] 测试不产生业务调用、文件写入或远端副作用。

## Notes

- 依赖 #153/#155；首版不主动探测 workspace shell。
