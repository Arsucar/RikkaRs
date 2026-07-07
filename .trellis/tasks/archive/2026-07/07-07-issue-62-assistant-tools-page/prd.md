# Issue 62 assistant empowerment tools page

## Goal

在助手配置页新增「赋能工具」统一管理入口与页面，以高信息密度展示所有赋能工具（工作区/记忆/记忆表/搜索/对话/技能/子代理/完成）的分类、名称、描述与启用状态，并可在此直接开关分类。

来源：GitHub issue #62。

## Requirements

### 路由与入口
- `RouteActivity.kt`：新增 `@Serializable data class AssistantTools(val id: String) : Screen`，并在 entryProvider 注册 `entry<Screen.AssistantTools>` → `AssistantToolsPage(key.id)`，追加页面 import。
- `AssistantDetailPage.kt`：在「本地工具」卡片项之前，新增「赋能工具」入口项（clickable 导航到 `Screen.AssistantTools(id)`），副标题显示 `已启用/总数`。

### 工具管理页 `AssistantToolsPage.kt`
- 复用 `AssistantDetailVM`（`koinViewModel + parametersOf(id)`），`vm.assistant` / `vm.update` / `vm.workspaces`。
- 分组卡片（`CardGroup`）：每组一个分类头行（图标 + 名称 + 状态控件），启用时展开该组工具行（工具名 + 简述）。
- 8 个分类与其开关字段：
  | 分类 | 控件 | 字段 | 工具 |
  |------|------|------|------|
  | 工作区 | Switch | `workspaceId != null`（ON 绑定首个可用 workspace，OFF 置 null） | workspace_read_file / workspace_write_file / workspace_edit_file / workspace_shell |
  | 记忆 | Switch | `enableMemory` | memory_tool |
  | 记忆表 | Switch | `enableMemoryTable` | memory_table_tool |
  | 搜索 | Tag「始终启用」 | — | search_web / scrape_web |
  | 对话 | Switch | `enableRecentChatsReference` | recent_chats / conversation_search |
  | 技能 | 导航行（角标计数 + 箭头，点击跳转扩展技能页） | `enabledSkills.isNotEmpty()` | use_skill |
  | 子代理 | Switch | `enableSubagents` | spawn_subagent / ask_btw / manage_subagent_profile |
  | 完成 | Tag「始终启用」 | — | finish_work |
- 总数 15；「已启用」= 各启用分类工具数之和 + 始终启用组（搜索2 + 完成1）。

## Constraints

- 不新增数据源；分类状态直接读写 `Assistant` 已有字段。
- 工作区 ON 时若无可用 workspace 则不改动（无法凭空绑定）。
- 未要求本地化，使用中文字面量（与 issue 一致），不新增 strings.xml。

## Acceptance Criteria

- [ ] 助手配置页出现「赋能工具」入口，副标题显示已启用/总数。
- [ ] 工具页展示 8 分类 15 工具，分类可开关，始终启用组显示标签。
- [ ] 分类 OFF 时不展开工具行；ON 时展开。
- [ ] `:app:compileDebugKotlin` 编译通过。

## Notes

- 参考：`AssistantLocalToolPage.kt`（Switch 列表）、`AssistantDetailPage.kt`（入口卡片）、`RouteActivity.kt`（Screen + entry）、`ui/components/ui/Tag.kt`、`ui/components/ui/CardGroup.kt`。
- 工具名已核实：见 `data/ai/tools/*.kt`（search_web/scrape_web、recent_chats/conversation_search、spawn_subagent/ask_btw/manage_subagent_profile、finish_work 等）。
