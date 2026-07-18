# 支持助手工具级权限策略 #154

## Goal

支持按助手、按稳定工具键配置四态工具权限，并在运行时强制生效。

## Requirements

- 四态固定为 INHERIT/ALLOW/ASK/DENY；来源默认继续由 workspace/MCP 所有。
- DENY 在 provider request 前移除，ASK 强制审批，ALLOW 不得突破硬安全边界。
- 子代理采用父助手、来源门控和 profile 的最小权限交集。
- 孤儿策略保留、标记并允许手动清理；当前 generation 使用冻结快照。

## Acceptance Criteria

- [ ] 旧 JSON/备份默认 INHERIT，既有行为无破坏迁移。
- [ ] 同名 MCP、孤儿策略、策略优先级、DENY 不注入和子代理不可提权有测试。
- [ ] 保存失败回滚，修改只影响后续 generation。

## Notes

- 依赖 #153；阻塞 #157。
