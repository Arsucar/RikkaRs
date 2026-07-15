package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/** 单个赋能工具（工具名 + 简述）。 */
private data class ToolInfo(val name: String, val description: String)

/** 分类头行状态控件类型。 */
private enum class ToolControlKind { TOGGLE, ALWAYS_ON, NAVIGATE }

/** 页面渲染用的分类模型。 */
private data class ToolGroupUi(
    val title: String,
    val icon: ImageVector,
    val tools: List<ToolInfo>,
    val enabled: Boolean,
    val controlKind: ToolControlKind,
    val onToggle: ((Boolean) -> Unit)? = null,
    val badge: String? = null,
    val onNavigate: (() -> Unit)? = null,
)

private val WORKSPACE_TOOLS = listOf(
    ToolInfo("workspace_read_file", "读取工作区文件"),
    ToolInfo("workspace_write_file", "写入工作区文件"),
    ToolInfo("workspace_edit_file", "精确编辑文件"),
    ToolInfo("workspace_shell", "执行 shell 命令"),
)
private val MEMORY_TOOLS = listOf(
    ToolInfo("memory_tool", "创建/编辑/删除长期记忆"),
)
private val MEMORY_TABLE_TOOLS = listOf(
    ToolInfo("memory_table_tool", "读写结构化记忆表格与模板"),
)
private val SEARCH_TOOLS = listOf(
    ToolInfo("search_web", "联网搜索"),
    ToolInfo("scrape_web", "抓取网页内容"),
)
private val CONVERSATION_TOOLS = listOf(
    ToolInfo("recent_chats", "引用最近对话"),
    ToolInfo("conversation_search", "搜索历史对话"),
)
private val SKILL_TOOLS = listOf(
    ToolInfo("use_skill", "加载并应用技能"),
)
private val SUBAGENT_TOOLS = listOf(
    ToolInfo("spawn_subagent", "派发子代理执行任务"),
    ToolInfo("ask_btw", "侧问子代理"),
    ToolInfo("manage_subagent_profile", "管理子代理配置"),
)
private val FINISH_TOOLS = listOf(
    ToolInfo("finish_work", "标记任务完成"),
)

private val ALL_TOOL_GROUPS = listOf(
    WORKSPACE_TOOLS, MEMORY_TOOLS, MEMORY_TABLE_TOOLS, SEARCH_TOOLS,
    CONVERSATION_TOOLS, SKILL_TOOLS, SUBAGENT_TOOLS, FINISH_TOOLS,
)

/**
 * 统计助手当前启用的赋能工具数量与总数，供助手配置页入口卡片显示 `已启用/总数`。
 * 搜索与完成工具始终启用。
 */
fun empowermentToolStats(assistant: Assistant): Pair<Int, Int> {
    val total = ALL_TOOL_GROUPS.sumOf { it.size }
    var enabled = FINISH_TOOLS.size
    if (assistant.enableWebSearch) enabled += SEARCH_TOOLS.size
    if (assistant.workspaceId != null) enabled += WORKSPACE_TOOLS.size
    if (assistant.enableMemory) enabled += MEMORY_TOOLS.size
    if (assistant.enableMemoryTable) enabled += MEMORY_TABLE_TOOLS.size
    if (assistant.enableRecentChatsReference) enabled += CONVERSATION_TOOLS.size
    if (assistant.enabledSkills.isNotEmpty()) enabled += SKILL_TOOLS.size
    if (assistant.enableSubagents) enabled += SUBAGENT_TOOLS.size
    return enabled to total
}

@Composable
fun AssistantToolsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    val (enabled, total) = empowermentToolStats(assistant)
                    Text("赋能工具（$enabled/$total）")
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantToolsContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            onNavigateSkills = { navController.navigate(Screen.AssistantInjections(id)) },
        )
    }
}

@Composable
private fun AssistantToolsContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    onNavigateSkills: () -> Unit,
) {
    val groups = listOf(
        ToolGroupUi(
            title = "工作区",
            icon = HugeIcons.Folder01,
            tools = WORKSPACE_TOOLS,
            enabled = assistant.workspaceId != null,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on ->
                if (on) {
                    workspaces.firstOrNull()?.let { ws ->
                        onUpdate(assistant.copy(workspaceId = Uuid.parse(ws.id)))
                    }
                } else {
                    onUpdate(assistant.copy(workspaceId = null))
                }
            },
        ),
        ToolGroupUi(
            title = "记忆",
            icon = HugeIcons.Brain02,
            tools = MEMORY_TOOLS,
            enabled = assistant.enableMemory,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableMemory = on)) },
        ),
        ToolGroupUi(
            title = "记忆表",
            icon = HugeIcons.Database02,
            tools = MEMORY_TABLE_TOOLS,
            enabled = assistant.enableMemoryTable,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableMemoryTable = on)) },
        ),
        ToolGroupUi(
            title = "搜索",
            icon = HugeIcons.Search01,
            tools = SEARCH_TOOLS,
            enabled = assistant.enableWebSearch,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableWebSearch = on)) },
        ),
        ToolGroupUi(
            title = "对话",
            icon = HugeIcons.Message02,
            tools = CONVERSATION_TOOLS,
            enabled = assistant.enableRecentChatsReference,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableRecentChatsReference = on)) },
        ),
        ToolGroupUi(
            title = "技能",
            icon = HugeIcons.Puzzle,
            tools = SKILL_TOOLS,
            enabled = assistant.enabledSkills.isNotEmpty(),
            controlKind = ToolControlKind.NAVIGATE,
            badge = assistant.enabledSkills.size.takeIf { it > 0 }?.toString(),
            onNavigate = onNavigateSkills,
        ),
        ToolGroupUi(
            title = "子代理",
            icon = HugeIcons.Connect,
            tools = SUBAGENT_TOOLS,
            enabled = assistant.enableSubagents,
            controlKind = ToolControlKind.TOGGLE,
            onToggle = { on -> onUpdate(assistant.copy(enableSubagents = on)) },
        ),
        ToolGroupUi(
            title = "完成",
            icon = HugeIcons.Tick02,
            tools = FINISH_TOOLS,
            enabled = true,
            controlKind = ToolControlKind.ALWAYS_ON,
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            ToolGroupCard(group)
        }
    }
}

@Composable
private fun ToolGroupCard(group: ToolGroupUi) {
    CardGroup(
        title = { Text(group.title) },
    ) {
        // 分类头行：图标 + 名称 + 状态控件
        item(
            onClick = group.onNavigate,
            leadingContent = { Icon(group.icon, null) },
            headlineContent = { Text(group.title) },
            supportingContent = { Text("${group.tools.size} 个工具") },
            trailingContent = {
                when (group.controlKind) {
                    ToolControlKind.TOGGLE -> Switch(
                        checked = group.enabled,
                        onCheckedChange = group.onToggle,
                    )

                    ToolControlKind.ALWAYS_ON -> Tag(type = TagType.INFO) {
                        Text("始终启用")
                    }

                    ToolControlKind.NAVIGATE -> {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (group.badge != null) {
                                Tag(type = TagType.INFO) { Text(group.badge) }
                            }
                            Icon(HugeIcons.ArrowRight01, null)
                        }
                    }
                }
            },
        )
        // 工具行：仅在分类启用时展开
        if (group.enabled) {
            group.tools.forEach { tool ->
                item(
                    headlineContent = {
                        Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                    },
                    supportingContent = {
                        Text(tool.description, style = MaterialTheme.typography.bodySmall)
                    },
                )
            }
        }
    }
}
