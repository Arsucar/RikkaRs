# Implement: Hook 编辑器单一标签管理

## Preconditions

- PRD / design 已评审或用户明确允许实现
- `task.py start` 后 status = `in_progress`
- 子代理实现前读 `implement.jsonl`；检查前读 `check.jsonl`

## Ordered checklist

### Phase A — Domain / config

1. [x] `ConversationHook.kt`：新增 `ManageConversationTags`；`HookActionType.MANAGE_CONVERSATION_TAGS`
2. [x] 旧 `add_conversation_tag` / `transition_conversation_tags` 解码 → normalize 为 Manage（`normalize()` + 保存边界）
3. [x] `configurationHash` 分支覆盖 Manage；Sync 不变
4. [x] `actionType` 扩展与历史 `parseStoredHookActionType` 别名（ADD/TRANSITION 枚举常量保留可读）
5. [x] 单测：`ConversationHookTest` 旧 golden JSON、新 round-trip、hash 排序不变性

### Phase B — Runtime

6. [x] `HookRuntimeRules`：`MAX_TAG_MANAGE_OPS`（8）及 `MAX_TAG_MANAGE_RESPONSE_CHARS`
7. [x] `ManageConversationTagsHookOutputParser`：strict keys `decision|operations|reason`
8. [x] `FrozenHookModelRequest.ManageConversationTags` + `buildManageTagsEvaluationPrompt`
9. [x] `ManageConversationTagsHookAction`：prepare / parse / execute（无证据门控；C1 整单 fail-closed；`validateManageTagOperations`）
10. [x] `ConversationTagHookCommitter.commitManageTags`：单事务 multi-op
11. [x] 注册表 / DI：注册 Manage；移除 Add/Transition 注册
12. [x] 废弃 handler 文件仍保留（未注册）；exhaustive `when` 已覆盖 legacy 展示别名
13. [x] 单测：parser apply/skip/非法 shape/上限/multi-op；`ManageConversationTagsHookActionTest` fail-closed gate；manage prompt 无 evidence；hash/编辑器校验

### Phase C — UI

14. [x] `AssistantHookEditorPage`：Select 仅 Manage | Sync；删三 chip 与 Transition 面板
15. [x] allowlist 真多选；校验走 `validateHookEditor`
16. [x] 评估提示词默认折叠/压缩（`promptExpanded` 默认 false）
17. [x] 列表摘要：allowlist 名称；删 transition 副行
18. [x] History：新类型展示 + 旧枚举不崩溃；Sync Preview/Run/Retry 条件不变
19. [x] strings：`values` + 简体中文（`values-zh`）；更新 `hookActionLabelRes`

### Phase D — Spec / quality

20. [x] 更新 `.trellis/spec/app/conversation-tags-and-hooks.md`（UI + multi-op；evidence scenario 标 superseded）
21. [x] `AssistantHooksPageTest` / parser / ConversationHookTest / Manage gate tests 已适配
22. [x] 聚焦 JVM 测试 + `compileDebugKotlin --no-daemon`（2026-07-20 check 通过）
23. [ ] 有设备：`adb` + `installDebug --no-daemon` 验收编辑页（check 有源码改动；装机留给主会话）

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

- [x] PRD 验收 1–8 可映射到代码/测试（#150 已于 v2.3.33 关闭；本 check 补 fail-closed 单测）
- [x] 无 Issue 硬门控调用路径（Manage prepare 不调用 `detectGitHubIssueCompletionEvidence`；旧 Transition handler 未注册）
- [x] 旧 Add/Transition JSON 仍可加载并保存为 Manage
- [x] Sync Preview/Run/Retry 仍可用（history 仅 `SYNC_MEMORY_TABLE`）
- [x] 子代理仅最后检查步编译

## Check verification (2026-07-20)

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest `
  --tests "me.rerere.rikkahub.data.model.ConversationHookTest" `
  --tests "me.rerere.rikkahub.service.hooks.*" `
  --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantHooksPageTest" `
  :app:compileDebugKotlin
# BUILD SUCCESSFUL
# Feature commit: 0f078ae1
# Check deltas: validateManageTagOperations + ManageConversationTagsHookActionTest + parser/prompt coverage
```

## Known residual (non-blocking)

- `commitManageTags` Room 事务路径仍主要靠 androidTest 历史套件（`ConversationTagHookCommitterTest` 仍以旧 actionType 字符串 seed）；JVM 层已覆盖 C1 fail-closed gate。
- 旧 `AddConversationTagHookAction` / `TransitionConversationTagsHookAction` 源文件未删除但未注册。
- History UI 对 Manage 审计 JSON 优先尝试旧 Transition summary 解码，失败则回退 raw operations 展示（可接受）。

## Dispatch notes

- 实现：`trellis-implement`，prompt 首行 `Active task: .trellis/tasks/07-18-hook-editor-tag-management`
- 检查：`trellis-check`（唯一允许 Gradle 编译的子代理）
- 主代理含源码改动收尾时必须装设备流程
