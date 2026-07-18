# #151 执行计划

- [ ] 提取 workspace shellStatus 规范化、tool-name 常量和 capability resolver。
- [ ] 让 ChatService READY 门控消费共享 resolver，保持既有日志和 tool factory。
- [ ] 提取 0/1/N enable decision 与安全 UUID 解析。
- [ ] 为 AssistantDetailVM 增加可观察的 workspace binding 保存 API。
- [ ] 改造 AssistantToolsPage：configured/available 分离、选择 sheet、创建/修复入口、保存反馈。
- [ ] 改善 WorkspaceSelectSheet 的选择语义，避免 TalkBack 无法识别 selected。
- [ ] 使用 locale-tui workflow 增加并核验全部配置 locale 的文案。
- [ ] 新增 table-driven JVM tests 和持久化回归测试。
- [ ] 静态审查后由唯一 final check agent 执行聚焦测试、资源处理和 Kotlin 编译。
- [ ] 按设备流程执行 installDebug 或记录 APK 交付边界。

## Risk Gates

- 不得让 N workspace 自动选择第一项。
- 统计和工具行不得继续以 `workspaceId != null` 作为有效条件。
- 保存失败不得把 attempted ID 当作已提交配置。
- OFF 不得调用 workspace 删除/rootfs 清理。
- ChatService 非 READY/unknown 必须继续 fail closed。
