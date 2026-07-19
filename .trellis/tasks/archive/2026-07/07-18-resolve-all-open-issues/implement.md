# 全部 Open Issues 执行计划

## Ordered Delivery

- [ ] 子任务 #150：核验提交 `0f078ae1`、相关测试和 v2.3.33，发布并回读双语评论后关闭。
- [ ] 子任务 #151：修复 workspace 0/1/N 绑定与 READY 统计，同步落 #152 第一组测试，安装验收。
- [ ] 子任务 #153：实现 capability key/catalog/snapshot，改造现有工具页并落静态/动态来源测试。
- [ ] 子任务 #154：实现四态持久化、运行时过滤、审批覆盖和子代理最小权限交集。
- [ ] 子任务 #155：实现 reason chain、修复导航和脱敏摘要。
- [ ] 子任务 #156：实现聚合状态与显式 MCP 连接测试，覆盖 revision/并发/生命周期。
- [ ] 子任务 #157：实现 preset schema/CRUD/diff/复制/批量编辑与安全输入限制。
- [ ] 子任务 #152：补齐最终跨层矩阵，证明目录 effective IDs 与 ChatService names 一致。
- [ ] 父任务整合：更新 CHANGELOG，合并执行资源/Kotlin/JVM/AndroidTest 编译检查，安装 Debug 包。
- [ ] 每条 issue 按实际交付提交/版本发布独立中英文评论，回读确认后关闭。
- [ ] 提交、推送 `origin/release/rikka-arsucar`，按需要运行 `Release APK (arm64)` 并核验 Release。

## Validation Gates

- 每个子任务：聚焦 JVM 测试 + `git diff --check`。
- UI/资源批次：`:app:processDebugResources` 与 `:app:compileDebugKotlin`，均带 `--no-daemon`。
- 最终批次合并：资源处理、Kotlin 编译、全部 JVM 测试、AndroidTest 源码编译尽量一次 Gradle 调用。
- 不默认运行 `connectedDebugAndroidTest`；不默认运行全量 lint，除非最终改动触发仓库定义的高风险条件。
- app 改动最终必须执行 `adb devices` 和 `:app:installDebug`；无设备时按仓库 APK 上传流程交付。

## Risk And Rollback Points

- `Assistant.kt` / `PreferencesStore.kt`：任何序列化失败立即停止后续子任务，先恢复旧备份兼容。
- `ChatService.kt`：权限过滤必须有 provider request 前的直接断言；否则不得进入诊断/模板批次。
- `SubagentPermissionBuilder.kt`：任何父级 DENY 可被恢复的情况均视为安全阻断。
- `McpManager.kt`：连接测试若可能调用业务工具、泄密或取消共享客户端，不得发布。
- `AssistantToolsPage.kt`：多个子任务依次修改，不并行编辑同一页面；优先下沉领域模型减少冲突。
