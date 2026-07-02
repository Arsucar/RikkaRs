# Implement Plan: updateCurrentMessages 修复

## Checklist

- [ ] 1. 读取 `Conversation.kt` L62-94 确认当前实现
- [ ] 2. 重写 `updateCurrentMessages` 为按 `UIMessage.id` 匹配
- [ ] 3. 新增/更新单元测试 `ConversationTest.kt`：
  - 测试 A：无 hidden 节点的正常更新（回归）
  - 测试 B：leading hidden 节点 + summary 场景，`updateCurrentMessages` 正确更新可见节点
  - 测试 C：新消息追加到末尾
- [ ] 4. 编译验证：`.\gradlew :app:compileDebugKotlin --no-daemon`
- [ ] 5. 运行单元测试（如测试存在）：`.\gradlew :app:testDebugUnitTest --tests "*Conversation*" --no-daemon`
- [ ] 6. 安装到设备：`.\gradlew :app:installDebug --no-daemon`

## Validation Commands

```bash
.\gradlew :app:compileDebugKotlin --no-daemon
.\gradlew :app:testDebugUnitTest --tests "*Conversation*" --no-daemon
.\gradlew :app:installDebug --no-daemon
```

## Rollback Point

只需恢复 `Conversation.kt` 中 `updateCurrentMessages` 的原始实现。
