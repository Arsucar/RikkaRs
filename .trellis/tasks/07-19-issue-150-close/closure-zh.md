已在目标分支 `release/rikka-arsucar` 完成交付并核验。

## 解决点

- Hook 顶级动作收敛为“标签管理 / 同步记忆表”，标签管理只保留一份 allowlist，策略由评估提示词表达。
- 动作类型改为 Select，提示词默认折叠；编辑页不再展示旧 Add/Transition 模式或伪多选条件墙。
- 旧 `ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` 配置统一迁移到标签管理；Sync 配置与 Preview/Run/Retry 路径保留。
- 运行时支持多 operation 标签变更，严格校验顶层字段、operation 数量和 allowlist；完整预校验通过后才进入事务提交，并移除旧 Issue 证据硬门控。

## 验证

- 修复提交：`0f078ae1c4c4810ac3b9d891e384ceb4e0500004`
- 发布提交：`2e3c841a861c79b62724b781ebffe180606a416a`
- 正式版本：`v2.3.33`，已确认 tag 包含修复提交。
- GitHub Release：https://github.com/Arsucar/RikkaRs/releases/tag/v2.3.33
- arm64 APK：`rikka-arsucar-v2.3.33-arm64.apk`
- APK SHA-256：`46c7640c47bf1a49aca8a9178708b2c1cba8d2a95688bb3c654607377108dbc1`
- 实际执行：`./gradlew --no-daemon :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.model.ConversationHookTest" --tests "me.rerere.rikkahub.service.hooks.HookOutputParserTest" --tests "me.rerere.rikkahub.ui.pages.assistant.detail.AssistantHooksPageTest"`
- 结果：`BUILD SUCCESSFUL`；3 个测试类共 37 项，0 failure / 0 error / 0 skipped；同一任务图中的 `:app:compileDebugKotlin` 通过。

## 定位

- 配置迁移与统一动作：`ConversationHook.kt`
- 编辑器动作选择、allowlist、折叠提示词：`AssistantHooksPage.kt`
- 严格 multi-op 解析：`HookOutputParser.kt`
- allowlist/标签存在性预校验：`ManageConversationTagsHookAction.kt`
- Room 原子提交：`ConversationTagHookCommitter.kt`
- 发布记录：`CHANGELOG.md` 的 `v2.3.33` 条目。

## 已知边界

- 本轮 `adb devices` 显示固定设备 `100.99.129.110:5555` 为 `offline`，未执行 `installDebug`，因此不声称已完成真机 UI 密度、Preview/Run/Retry 手工验收。
- fail-closed 零写入由 action/committer 代码路径静态核验；现有自动化测试直接覆盖迁移、编辑器边界和 parser，尚无 action/committer 的独立事务回归测试。该测试缺口由 #152 的跨层测试任务继续追踪。
