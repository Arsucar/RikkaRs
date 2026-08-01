# Research: issue #199 workspace_read_file /upload knownMount 实现事实

- **Query**: 为修复 issue #199（workspace_read_file 无法读取 /upload）收集精确实现细节
- **Scope**: internal
- **Date**: 2026-08-01
- **Task**: `08-01-07-31-issue-199-upload-knownmount`

## Findings

### 1. ChatService.createWorkspaceToolsIfReady — knownMounts 注册

**文件**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

| 行号 | 内容 |
|---|---|
| 1911–1953 | `createWorkspaceToolsIfReady` 函数体 |
| 1940–1948 | **当前 knownMounts 列表**（仅 `/skills` + private mounts） |
| 1955–1976 | `assistantPrivateSkillMounts` → `/skills_private` |

```1940:1948:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
            knownMounts = listOf(
                WorkspaceKnownMount(
                    target = "/skills",
                    source = skillManager.getSkillsDir(createIfMissing = createSkillDirectories),
                    allowedSymlinkRoots = listOf(
                        skillManager.getSkillSharedDir(createIfMissing = createSkillDirectories),
                    ),
                )
            ) + privateSkillMounts.knownMounts,
```

`privateSkillMounts.knownMounts` 来源：

```1962:1968:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
            knownMounts = listOf(
                WorkspaceKnownMount(
                    target = "/skills_private",
                    source = assistantSkillsDir,
                    allowedSymlinkRoots = listOf(skillSharedDir),
                )
            ),
```

**当前列表内容**: `/skills`、`/skills_private`。**不含** `/upload`。

调用点（生成工具注入）: `ChatService.kt:1828–1835`（`createWorkspaceToolsIfReady(...)`）。

---

### 2. 子代理工具构建处 — 同类 knownMounts list

**主注册位置**: `ChatService.kt:3070–3084`（`buildSubagentToolsForChat` 内预计算 workspace tools）

```3070:3084:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
        val privateSkillMounts = assistantPrivateSkillMounts(assistant.id)
        val precomputedWorkspaceTools = createSubagentWorkspaceTools(
            access = profile.workspaceAccess,
            profile = profile,
            workspaceRepository = workspaceRepository,
            workspaceId = workspaceId,
            workspaceCwd = workspaceCwd,
            knownMounts = listOf(
                WorkspaceKnownMount(
                    target = "/skills",
                    source = skillManager.getSkillsDir(),
                    allowedSymlinkRoots = listOf(skillManager.getSkillSharedDir()),
                )
            ) + privateSkillMounts.knownMounts,
            extraBindMounts = privateSkillMounts.bindMounts,
        )
```

**转发层**: `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt:102–119`

```102:119:app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentPermissionBuilder.kt
suspend fun createSubagentWorkspaceTools(
    access: WorkspaceAccess,
    profile: SubagentProfile,
    workspaceRepository: WorkspaceRepository,
    workspaceId: String,
    workspaceCwd: String? = null,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
    extraBindMounts: List<WorkspaceBindMount> = emptyList(),
): List<Tool> {
    ...
    return filterWorkspaceToolsByAccess(
        createWorkspaceTools(workspaceId, workspaceRepository, workspaceCwd, knownMounts, extraBindMounts),
        access,
    )
```

子代理 knownMounts 与主代理一致：**仅 `/skills` + `/skills_private`，无 `/upload`**。两处 list 是**独立内联构造**（非共享常量）。

---

### 3. WorkspaceTools.resolveKnownMountFile / readTextInRootfs — 路径解析

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt`

#### WorkspaceKnownMount 数据类（:40–44）

```40:44:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
data class WorkspaceKnownMount(
    val target: String,
    val source: File,
    val allowedSymlinkRoots: List<File> = emptyList(),
)
```

#### createWorkspaceTools 传 knownMounts（:46–66）

- `createReadFileTool(..., knownMounts)` — 读
- `createEditFileTool(..., knownMounts, ...)` — 编辑前读原文
- write/shell 用 `extraBindMounts`，**不**用 knownMounts

#### readTextInRootfs（:308–321）

```308:321:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
    knownMounts: List<WorkspaceKnownMount> = emptyList(),
): String {
    resolveKnownMountFile(path, knownMounts)?.let { file ->
        require(file.isFile) { "Path is not a file: $path" }
        require(file.length() <= MAX_READ_FILE_BYTES) { ... }
        return file.readText()
    }
    return readRootfsBuffer(workspaceId, path).toString(Charsets.UTF_8.name())
}
```

#### resolveKnownMountFile（:343–360）

```343:360:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
internal fun resolveKnownMountFile(path: String, knownMounts: List<WorkspaceKnownMount>): File? {
    val normalized = normalizeRootfsAbsolutePath(path) ?: return null
    for (mount in knownMounts) {
        val target = normalizeRootfsAbsolutePath(mount.target)?.trimEnd('/') ?: continue
        val relative = when {
            normalized == target -> ""
            normalized.startsWith("$target/") -> normalized.removePrefix("$target/").trimStart('/')
            else -> continue
        }
        val sourceRoot = mount.source.canonicalFile
        val lexicalFile = sourceRoot.toPath().resolve(relative).normalize().toFile()
        if (!lexicalFile.isSameOrInsidePath(sourceRoot)) continue
        val file = lexicalFile.canonicalFile
        val allowedRoots = listOf(sourceRoot) + mount.allowedSymlinkRoots.map { it.canonicalFile }
        if (allowedRoots.any { root -> file.isSameOrInside(root) }) return file
    }
    return null
}
```

#### knownMount 缺失时的回落路径

1. `resolveKnownMountFile` 返回 `null`（`/upload` 不在 knownMounts 中）
2. 走 `readRootfsBuffer` → `WorkspaceRepository.rootfsFileSize` / `exportRootfsFile`
3. 委托 `WorkspaceManager.resolveRootfsPath`（`workspace/.../WorkspaceManager.kt:135–160`）

`WorkspaceManager` **构造时**已带全局 `bindMounts`（含 `/upload`，见第 4 点）。若 bind 匹配成功，应解析到 `filesDir/upload/...`；若 bind 未匹配，最终回落：

```159:159:workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt
        return RootfsLocation(linuxDir(root), trimmed.trimStart('/'))
```

即 `File(workspaceDir(root), "linux")` + 相对路径 → **`workspaces/<root>/linux/upload/<file>`**（`LINUX_DIR = "linux"`，:375）。该路径在 rootfs 内通常是空挂载点，读文件会得到 **File does not exist**。

**补充事实**: `readImageInRootfs`（:384–405）同样先 `resolveKnownMountFile`，失败再 `readRootfsBuffer`。

**issue 所述失败模式**: 工具路径用 knownMount 短路；未注册时依赖 WorkspaceManager bind 表。历史 #34/#37 对 `/skills` 的修复模式是在 **ChatService knownMounts** 侧显式注册，与 proot bind 表并行存在。

---

### 4. RepositoryModule 的 Proot bind：filesDir/upload → /upload

**文件**: `app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt:70–101`

```86:100:app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt
            // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析, 避免两处漂移
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
```

- Host 源: `context.filesDir` + `FileFolders.UPLOAD`（`"upload"`）
- Rootfs 目标: `"/upload"`
- 用途注释: 同时服务 PRoot `-b` 与 `WorkspaceManager.resolveRootfsPath`

**FileFolders 常量**: `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt:489–496`

```489:496:app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt
object FileFolders {
    const val UPLOAD = "upload"
    const val SKILLS = "skills"
    const val ASSISTANT_SKILLS = "assistant_skills"
    const val SKILL_SHARED = "skill_shared"
    const val FONTS = "fonts"
    const val TOOL_OUTPUTS = "tool_outputs"
}
```

**上传落盘**: `FilesManager.createChatFilesByContents`（:105–145）写到 `context.filesDir.resolve(FileFolders.UPLOAD)`，文件名 `buildUuidFileName` → `${Uuid.random()}.$ext`（`FileUtils.kt:18–28`）。

**path 属性注入**: `DocumentAsPromptTransformer.resolveWorkspacePath`（:52–58）

```52:58:app/src/main/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformer.kt
    // 上传文件保存在 filesDir/upload 下, 该目录通过 proot 挂载到 workspace 的 /upload
    // 返回文件在 workspace 内的绝对路径, 便于 AI 用 workspace 工具直接读取原始文件
    private fun resolveWorkspacePath(document: UIMessagePart.Document): String? {
        val file = runCatching { document.url.toUri().toFile() }.getOrNull() ?: return null
        if (file.parentFile?.name != "upload") return null
        return "/upload/${file.name}"
    }
```

---

### 5. BuiltinPromptRegistry.buildWorkspaceGuidePrompt — /upload 原文

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptRegistry.kt:9–54`

`/upload` 相关原文（:42–46）:

```42:46:app/src/main/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptRegistry.kt
    add(
        "- Files the user uploaded are mounted at `/upload`. Treat `/upload` as READ-ONLY: read uploaded files from " +
            "`/upload/<file-name>`, but never modify, overwrite, or delete anything there. If you need to change an " +
            "uploaded file, copy it into `/workspace` first and edit the copy."
    )
```

同函数还声明 `/workspace`、`/skills`、`/skills_private`（:19–40）。  
注册表默认: `:135` `DEFAULT_WORKSPACE_GUIDE = buildWorkspaceGuidePrompt(VAR_WORKSPACE_NAME, VAR_CWD)`。

**测试断言**: `app/src/test/.../BuiltinPromptRegistryTest.kt:112`  
`assertTrue(resolved.contains("`/upload` as READ-ONLY"))`

---

### 6. forkConversationAtMessage + copyWithForkedFileUrl

**文件**: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

#### forkConversationAtMessage（:2741–2797）

```2753:2766:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }
```

#### copyWithForkedFileUrl（:2899–2913）

```2899:2913:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }
```

**行为事实**:
- 对 `file:` URL 调用 `FilesManager.createChatFilesByContents`
- 在同一 `filesDir/upload` 目录下再写一份 **新 UUID 文件名** 的副本
- 更新 Image/Document/Video/Audio part 的 `url`
- 新文件仍在 host `upload/` 下；若 `/upload` knownMount（或 WorkspaceManager bind）可用，新 UUID 路径应同样可解析

Web 入口: `ConversationRoutes.kt:308` → `chatService.forkConversationAtMessage(...)`。

---

### 7. workspace_read_file 工具描述文本

**文件**: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt:76–113`

```81:87:app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area. Use /skills for global skill files and /skills_private for this assistant's private skill files.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp, svg, heic, heif, avif, ico).
    """.trimIndent().replace("\n", " "),
```

**当前声明**: `/workspace`、`/skills`、`/skills_private`。  
**未声明**: `/upload`。

（UI 文案另有 `R.string.assistant_tools_workspace_read_file_desc` 等，与 Tool definition 字符串分离。）

---

### 8. 相关单测位置与命名约定

| 文件 | 包路径 | 内容要点 |
|---|---|---|
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/WorkspaceKnownMountTest.kt` | `...data.ai.tools` | `resolveKnownMountFile_*`：映射 `/skills`、拒绝 `..`、symlink allowedRoots |
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/WorkspaceToolsTest.kt` | 同上 | 图片扩展名、`workspaceShellResultPart` metadata；**无** read_file/knownMount |
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/WorkspaceShellPresentationTest.kt` | 同上 | shell 展示 |
| `app/src/test/java/me/rerere/rikkahub/service/ChatServiceTest.kt` | `...service` | 仅 background params / capability 名称；**无** knownMounts / fork / createWorkspaceToolsIfReady |
| `app/src/test/java/me/rerere/rikkahub/data/ai/prompts/BuiltinPromptRegistryTest.kt` | prompts | 含 `/upload` READ-ONLY 断言 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/transformers/DocumentAsPromptTransformerTest.kt` | transformers | path=`/upload/a.txt` 注入 |
| `workspace/src/test/java/me/rerere/workspace/RootfsPathResolutionTest.kt` | workspace 模块 | `WorkspaceManager` bind（含 `/upload` 挂载构造）+ `/skills` 读文件 |

**命名约定**:
- 工具层: `app/src/test/.../data/ai/tools/<Subject>Test.kt`
- 服务层: `app/src/test/.../service/ChatServiceTest.kt`（顶层函数/纯逻辑，非 Android instrumented）
- knownMount 解析: 直接测 `internal fun resolveKnownMountFile`（同模块可见）
- JVM 单测，JUnit4 `@Test`，临时目录 `Files.createTempDirectory` / `TemporaryFolder`

**现状空缺**: 无 `/upload` knownMount 单测；无 `createWorkspaceToolsIfReady` knownMounts 列表断言；无 fork 拷贝后 path 可读性测试。

---

### Related Specs / 历史

- `.trellis/spec/app/workspace-tool-capabilities.md` — 工具名清单
- 历史同类: #34 / #37（`/skills` knownMount），归档任务 `07-06-skills-access-scope`
- Issue #199 正文与本调研 8 点定位表一致

## 修复影响面（仅事实，不含方案）

### 会触及的注册点（两处独立 list）

1. `ChatService.createWorkspaceToolsIfReady` — `ChatService.kt:1940–1948`
2. `ChatService.buildSubagentToolsForChat` 预计算 — `ChatService.kt:3077–3083`

两处均通过 `createWorkspaceTools` / `createSubagentWorkspaceTools` 把 `knownMounts` 传入 `WorkspaceTools`。

### 可能同步的描述/提示

| 位置 | 现状 |
|---|---|
| `WorkspaceTools.createReadFileTool` description (:83–87) | 提 `/skills`、`/skills_private`，**无** `/upload` |
| `BuiltinPromptRegistry.buildWorkspaceGuidePrompt` (:42–46) | **已有** `/upload` READ-ONLY 完整说明 |
| `DocumentAsPromptTransformer` path | **已有** `/upload/<name>` |
| `RepositoryModule` bind | **已有** `filesDir/upload` → `/upload` |

### 可复用的已有符号（非新抽象）

| 符号 | 位置 | 用途 |
|---|---|---|
| `FileFolders.UPLOAD` | `FilesManager.kt:490` | host 相对目录名 `"upload"` |
| `WorkspaceKnownMount` | `WorkspaceTools.kt:40` | knownMount 条目类型 |
| `WorkspaceBindMount` | workspace 模块 | proot/extra 绑定（skills_private 模式） |
| `skillManager.getSkillsDir()` 模式 | ChatService 内联 | `/skills` source 获取方式对照 |
| `assistantPrivateSkillMounts` | ChatService:1955 | **仅** private skills；upload 不在此结构内 |
| `resolveKnownMountFile` | WorkspaceTools:343 | 解析逻辑已支持任意 target/source，无 hardcode |

**没有** 名为 `UPLOAD_KNOWN_MOUNT` / 共享 `defaultKnownMounts()` 的现成常量；`/skills` 与 `/skills_private` 均为 **ChatService 内联** `WorkspaceKnownMount(...)`。`/skills` 的 source 来自 `skillManager`；`/upload` 的 host 源在 DI 层是 `File(context.filesDir, FileFolders.UPLOAD)`，与 `FilesManager` 写盘目录一致。

### 解析/fork 链路（事实依赖）

```
upload 落盘: FilesManager → filesDir/upload/<uuid>.ext
path 注入:   DocumentAsPromptTransformer → "/upload/<uuid>.ext"
proot/shell: RepositoryModule bindMounts → /upload
read_file:   knownMounts? → resolveKnownMountFile
             else → WorkspaceManager.resolveRootfsPath(bindMounts 或 linux/)
fork:        copyWithForkedFileUrl → createChatFilesByContents → 新 uuid 仍在 upload/
```

### 测试触点（事实）

- 扩展或平行于 `WorkspaceKnownMountTest` 的 `/upload` 映射用例
- 可选: ChatService 层 list 内容（若抽出可测 helper；当前两处 private/内联）
- `RootfsPathResolutionTest` 已覆盖 Manager 侧 `/upload` bind 构造，与 app 层 knownMounts **分层不同**

## Caveats / Not Found

- **无** 共享的 default knownMounts 工厂；主代理与子代理 list 复制粘贴式平行。
- `ChatService.createWorkspaceToolsIfReady` / knownMounts **无** 直接单测。
- `WorkspaceTerminalSession` 终端 proot 参数目前只 bind `/skills`（:74–78），**不含** `/upload`——与 AI workspace tools / RepositoryModule 的 WorkspaceManager 是**另一条**终端会话路径，与 issue #199 的 `workspace_read_file` 主路径分离。
- issue 文案称缺失 knownMount 时稳定落到 `linux/upload`；代码上 `readTextInRootfs` 回落后会走 `WorkspaceManager.resolveRootfsPath`，而该 Manager **已配置** `/upload` bind。若线上仍 File not exist，需在实现期再核验运行时 Manager 实例 bind 表与路径；本调研只记录静态代码事实。
- 未改任何生产代码；本文件为只读调研产出。
