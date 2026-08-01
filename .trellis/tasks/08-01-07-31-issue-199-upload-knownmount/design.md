# 技术设计：/upload knownMount

## 现状

- knownMounts 两处内联 list：`ChatService.kt:1940-1948`（主）、`:3077-3083`（子代理），内容仅 `/skills` + `/skills_private`。
- `WorkspaceTools.resolveKnownMountFile`（:343-360）已支持任意 target/source，无 hardcode。
- Proot/Manager bind 已有 `RepositoryModule.kt:86-100`：`filesDir/upload` → `/upload`。
- 系统提示 `BuiltinPromptRegistry.kt:42-46` 已声明 `/upload` READ-ONLY。
- 工具描述 `WorkspaceTools.kt:83-87` 未声明 `/upload`。
- fork 拷贝 `ChatService.copyWithForkedFileUrl`（:2899-2913）写回 upload/ 新 UUID 文件。

## 方案

1. 在 `createWorkspaceToolsIfReady` 与 `buildSubagentToolsForChat` 两处 knownMounts 各追加：
   ```kotlin
   WorkspaceKnownMount(
       target = "/upload",
       source = File(context.filesDir, FileFolders.UPLOAD),
   )
   ```
   source 不传 allowedSymlinkRoots（upload 无 symlink 授权）。
2. `WorkspaceTools.createReadFileTool` description 追加 `/upload` 只读说明（与 BuiltinPromptRegistry 口径一致）。
3. 可选：抽 `private fun uploadKnownMount() = WorkspaceKnownMount(...)` 或列表 builder，避免两处复制；若抽，命名为 ChatService 私有函数。

## 边界与取舍

- 不改 `WorkspaceManager` / `RepositoryModule`（已有 bind）。
- 不改终端会话 Proot（`WorkspaceTerminalSession` 仅 bind `/skills`，与 workspace_read_file 主路径分离，属 scope 外）。
- fork 无需改代码；只补测试证明新 UUID 路径可解析。

## 风险

- source 目录未创建：`File(context.filesDir, FileFolders.UPLOAD)` 可能不存在（未上传过）。`resolveKnownMountFile` 对不存在 source 的 canonicalFile 解析行为需验证；若 `canonicalFile` 抛异常需 `.apply { mkdirs() }` 或安全兜底。
- 运行时 WorkspaceManager 实例 bind 表与静态代码若不一致，knownMount 短路仍优先；本设计在 ChatService 显式注册，与 issue 期望一致。
