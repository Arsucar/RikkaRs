# Implement: Hook 编辑器单一标签管理

## Preconditions

- PRD / design 已评审或用户明确允许实现
- `task.py start` 后 status = `in_progress`
- 子代理实现前读 `implement.jsonl`；检查前读 `check.jsonl`

## Ordered checklist

### Phase A — Domain / config

1. [ ] `ConversationHook.kt`：新增 `ManageConversationTags`；`HookActionType.MANAGE_CONVERSATION_TAGS`
2. [ ] 旧 `add_conversation_tag` / `transition_conversation_tags` 解码 → normalize 为 Manage（自定义 serializer 或加载边界函数）
3. [ ] `configurationHash` 分支覆盖 Manage；Sync 不变
4. [ ] `actionType` 扩展与历史 `parseStoredActionType` 别名（ADD/TRANSITION 只读映射）
5. [ ] 单测：`ConversationHookTest` 旧 golden JSON、新 round-trip、hash 排序不变性

### Phase B — Runtime

6. [ ] `HookRuntimeRules`：`MAX_TAG_MANAGE_OPS`（建议 8）及响应长度常量
7. [ ] `ManageConversationTagsHookOutputParser`：strict keys `decision|operations|reason`
8. [ ] `FrozenHookModelRequest.ManageConversationTags` + `buildManageTagsEvaluationPrompt`
9. [ ] `ManageConversationTagsHookAction`：prepare / parse / execute（无证据门控；C1 整单 fail-closed）
10. [ ] `ConversationTagHookCommitter.commitManageTags`：单事务 multi-op
11. [ ] 注册表 / DI：注册 Manage；移除 Add/Transition 注册
12. [ ] 清理或内联废弃 handler；全库搜 exhaustive `when`
13. [ ] 单测：parser、allowlist 越权、整单拒绝、0 变更 SKIPPED、commit 顺序

### Phase C — UI

14. [ ] `AssistantHookEditorPage`：Select 仅 Manage | Sync；删三 chip 与 Transition 面板
15. [ ] allowlist 真多选；校验走 `validateHookEditor`
16. [ ] 评估提示词默认折叠/压缩
17. [ ] 列表摘要：allowlist 名称；删 transition 副行
18. [ ] History：新类型展示 + 旧枚举不崩溃；Sync Preview/Run/Retry 条件不变
19. [ ] strings：`values` + 简体中文；更新 `hookActionLabelRes`

### Phase D — Spec / quality

20. [ ] 更新 `.trellis/spec/app/conversation-tags-and-hooks.md`（UI + multi-op；evidence scenario 标 superseded）
21. [ ] `AssistantHooksPageTest` / 相关 dispatcher 测试适配
22. [ ] 聚焦 JVM 测试 + `compileDebugKotlin --no-daemon`
23. [ ] 有设备：`adb` + `installDebug --no-daemon` 验收编辑页

## Validation commands

```powershell
# 聚焦单测（实现过程中优先）
.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.model.ConversationHookTest"
.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.service.hooks.*"
.\gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantHooksPageTest"

# 收尾（仅最后一个检查子代理 / 主代理收尾）
.\gradlew --no-daemon :app:compileDebugKotlin
adb devices
# 无 device 时: adb connect 100.99.129.110:5555
.\gradlew --no-daemon :app:installDebug
```

不要默认全量 lint / connectedAndroidTest。

## Risky files

| File | Risk |
|------|------|
| `ConversationHook.kt` | JSON 兼容；Assistant 整包解码 |
| `HookRepository.kt` | `valueOf(actionType)` |
| `HookActionRegistry.kt` / DI | 注册遗漏 |
| `AssistantHooksPage.kt` | 大文件 UI 回归 |
| History drawer / ChatVM | Sync 按钮误伤 |
| Spec evidence scenario | 文档与代码不一致 |

## Rollback points

- A 完成后：配置层可独立回滚（无 runtime 注册新类型写盘前）
- B 完成后：若 UI 未合，旧 UI 可能无法编辑新类型 — 避免半发布
- 全量完成后：git revert；注意已保存的 `manage_conversation_tags` 在旧版本不可解码

## Review gates

- [ ] PRD 验收 1–8 可映射到测试/手工步骤
- [ ] 无 Issue 硬门控调用路径
- [ ] 旧 Add/Transition JSON 仍可加载并保存为 Manage
- [ ] Sync Preview/Run/Retry 仍可用
- [ ] 子代理仅最后检查步编译

## Dispatch notes

- 实现：`trellis-implement`，prompt 首行 `Active task: .trellis/tasks/07-18-hook-editor-tag-management`
- 检查：`trellis-check`（唯一允许 Gradle 编译的子代理）
- 主代理含源码改动收尾时必须装设备流程
