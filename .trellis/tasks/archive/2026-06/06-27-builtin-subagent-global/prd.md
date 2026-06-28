# 全局子代理列表页（二级路由）+ 删除/恢复默认

## Goal

把全局子代理 profile 管理 from 扩展页内嵌 CardGroup 改为独立的二级路由页面，参照 Skills/Workspaces 模式。补全删除功能和恢复默认入口。

## Background

当前 `ExtensionsPage.kt` L63-94 把全局 profile 直接内嵌为 CardGroup 列表，与其他扩展项（QuickMessages/Prompts/Skills/Workspaces 都是二级路由）不一致。用户反馈：子代理数量多时内嵌列表不友好，缺二级路由管理页；且全局 profile 列表**没有删除功能**（兄弟页面 SkillsPage/WorkspacePage/PromptPage 都有 Delete01，唯独子代理没有）。

用户原话："把子代理配置改成二级路由，路由选项放到拓展标题里面-工作区选项的后面，名字叫子代理"；"子代理是不是缺失了删除功能？"；"可以删，但可以恢复默认"。

## Requirements

### 路由与页面
- R-1：新增 `Screen.ExtensionSubagents`（复数，区别于已有的 `ExtensionSubagentProfile` 单条编辑页）。
- R-2：新增 `ExtensionSubagentsPage.kt`（复数），布局参照 `SkillsPage.kt` / `WorkspacePage.kt`：LargeFlexibleTopAppBar + LazyColumn profile 列表。
- R-3：`RouteActivity.kt` 注册新路由 entry。
- R-4：`ExtensionsPage.kt` 删除 L63-94 的 Subagent Profiles CardGroup；在"扩展"CardGroup 的工作区项后面追加"子代理"入口项，onClick 跳 `Screen.ExtensionSubagents`。

### 列表页交互
- R-5：每条 profile 用 CardGroup item 展示（leadingContent=Icon, headlineContent=displayName, supportingContent=description）。
- R-6：每条 profile 的 trailingContent 加两个 IconButton：编辑（ArrowRight01 或 Copy01 风格，点击跳 `Screen.ExtensionSubagentProfile(name, false)`）+ 删除（Delete01，点击弹 AlertDialog 确认）。
- R-7：删除确认后调用 `removeSubagentProfile` 从 `settings.globalSubagentProfiles` 移除并持久化（参照 `AssistantSubagentPage` 的 pendingDelete 模式）。
- R-8：TopBar actions 区加 + IconButton，点击弹创建对话框（OutlinedTextField 输入 name + 标识符合法性校验），确认后跳 `Screen.ExtensionSubagentProfile(name, true)`。
- R-9：TopBar overflow menu 加"恢复默认子代理"选项，点击后重新触发迁移逻辑（把 `BUILTIN_PROFILES` 中 `globalSubagentProfiles` 不存在的插入），弹 toast/确认。

### i18n
- R-10：新增字符串键（6 locale 同步）：
  - `extensions_page_subagents_entry`（扩展页入口项标题，如"子代理"）
  - `extensions_page_subagents_entry_desc`（入口项描述）
  - `extensions_subagents_page_title`（列表页 TopBar 标题，如"子代理配置"）
  - `extensions_subagents_page_empty`（空态文案）
  - `extensions_subagents_page_restore_defaults`（overflow 菜单项文案，如"恢复默认子代理"）
  - `extensions_subagents_page_restore_defaults_done`（恢复后 toast）
  - 复用已有 `extensions_page_add_subagent` / `subagent_delete_profile_title` / `subagent_delete_profile_desc` / `common_delete` / `common_cancel`

## Acceptance Criteria

- AC-1：扩展管理页不再有内嵌 Subagent Profiles CardGroup；"扩展"CardGroup 末尾（工作区后）有"子代理"入口项。
- AC-2：点击"子代理"跳转独立列表页，显示所有 globalSubagentProfiles。
- AC-3：列表页每条 profile 可编辑（跳编辑页）和删除（弹确认）。
- AC-4：TopBar + 按钮可创建新 profile。
- AC-5：TopBar overflow 有"恢复默认子代理"，点击后缺失的 3 个默认 profile 被恢复。
- AC-6：删除 explore 后扩展页列表不再显示 explore；overflow 恢复默认后 explore 回来。
- AC-7：`.\gradlew :app:compileDebugKotlin` 通过。
- AC-8：6 个 locale 的 strings.xml 都含新增键。

## Out of Scope

- 助手级子代理页（`AssistantSubagentPage`）不改——它仍展示 global + local，全局 profile 的增删通过新列表页完成。
- `ExtensionSubagentProfile` 单条编辑页不改。
- 数据迁移逻辑（`migrateSubagentBuiltinsIfNeeded`）不改，只是暴露一个公共方法供 overflow 调用。

## Technical Notes

- 参照 `SkillsPage.kt` 的整体结构（TopBar + LazyColumn + 删除弹窗 + 创建入口）。
- "恢复默认"复用 `PreferencesStore` 里已有的迁移函数（若它是 private，提取为 public 或在 SettingsStore/扩展函数里实现公共逻辑）。
- `Screen.ExtensionSubagents` 是 `data object`（无参路由），区别于 `ExtensionSubagentProfile`（带 profileName + createMode）。
