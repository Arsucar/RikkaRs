# Issues 122–130 执行计划

1. 审计并补全每个子任务的 PRD；复杂子任务补齐 design/implement。
2. 接管 #122 遗留改动，先完成隔离、迁移、授权与测试。
3. 完成 #123、#124、#125，并做静态交叉审查。
4. 在 #122 基础上完成 #126 Must 验收；明确 Could 项是否已有低风险实现路径。
5. 完成 #127 Room 持久化/恢复，再完成 #128 并发语义解耦。
6. 完成 #129/#130 图片内容返回链路和 provider 兼容测试。
7. 最终检查代理执行 diff 审计、focused tests、`compileDebugKotlin`/必要模块测试与 lint 风险检查。
8. 主代理检查设备并执行 `adb devices`、`./gradlew --no-daemon :app:installDebug`；失败按固定地址重连后重试一次。
9. 更新 CHANGELOG/必要 spec，按 issue 边界提交，推送 release 分支并关闭 issues。

## Rollback Points

- Room migration、Assistant 序列化、消息内容模型转换分别保持独立提交。
- 任一全量检查失败先回到对应子任务修复，不以关闭 issue 代替验证。
