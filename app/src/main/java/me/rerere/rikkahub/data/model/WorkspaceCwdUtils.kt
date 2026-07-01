package me.rerere.rikkahub.data.model

/**
 * 工作区 CWD（当前工作目录）的解析与规范化工具。
 *
 * 存储值（`Conversation.workspaceCwd` / `Assistant.defaultWorkspaceCwd`）可为 null，
 * 运行时统一通过 [resolveEffectiveWorkspaceCwd] 解析为始终非 null 的有效路径，
 * 默认回落到 [WORKSPACE_ROOT]。
 */

const val WORKSPACE_ROOT = "/workspace"

/**
 * 规范化工作区路径：
 * 1. 反斜杠 → 正斜杠、trim、合并连续 `/`
 * 2. 逐段解析 `.` 与 `..` 穿越
 * 3. 约束结果必须落在 [WORKSPACE_ROOT] 之内（FILES 区域）；越界则安全回落到 [WORKSPACE_ROOT]
 */
fun normalizeWorkspaceCwd(path: String?): String {
    if (path.isNullOrBlank()) return WORKSPACE_ROOT
    val unified = path.replace('\\', '/').trim()
    if (unified.isEmpty()) return WORKSPACE_ROOT

    val stack = ArrayDeque<String>()
    for (segment in unified.split('/')) {
        when (segment) {
            "", "." -> {} // 跳过空段与当前目录
            ".." -> if (stack.isNotEmpty()) stack.removeLast()
            else -> stack.addLast(segment)
        }
    }

    val resolved = "/" + stack.joinToString("/")
    return if (resolved == WORKSPACE_ROOT || resolved.startsWith("$WORKSPACE_ROOT/")) {
        resolved
    } else {
        // 越界（含 `..` 穿越到 /workspace 之外）→ 安全回落
        WORKSPACE_ROOT
    }
}

/**
 * 解析会话的有效 CWD。
 *
 * 优先级：`conversation.workspaceCwd` > `assistant.defaultWorkspaceCwd` > [WORKSPACE_ROOT]，
 * 结果经 [normalizeWorkspaceCwd] 规范化，始终非 null。
 */
fun resolveEffectiveWorkspaceCwd(
    conversation: Conversation,
    assistant: Assistant,
): String {
    val stored = conversation.workspaceCwd ?: assistant.defaultWorkspaceCwd
    return normalizeWorkspaceCwd(stored)
}
