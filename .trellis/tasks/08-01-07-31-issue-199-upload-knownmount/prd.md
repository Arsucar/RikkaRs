# bug(#199): workspace_read_file 补 /upload knownMount 支持

## Goal

工作区 READY 时，`workspace_read_file("/upload/<磁盘文件名>")` 能返回上传文件内容；`workspace_shell` 与 `workspace_read_file` 指向同一 host 文件；fork 后新 UUID 路径同样可读；`/skills`、`/skills_private` 现有行为不变。

## Requirements

- **主代理 knownMounts**：`ChatService.createWorkspaceToolsIfReady`（`ChatService.kt:1940-1948`）加入 `/upload` 条目，source 为 `filesDir/upload`。
- **子代理 knownMounts**：`ChatService.buildSubagentToolsForChat` 预计算（`ChatService.kt:3077-3083`）同样加入 `/upload`。
- **工具描述**：`WorkspaceTools.createReadFileTool` description（`WorkspaceTools.kt:83-87`）补充 `/upload` 只读声明。
- **fork 一致性**：`copyWithForkedFileUrl` 产生的 upload/ 下新 UUID 文件天然可解析（rootfs 内 upload 同目录），无需额外代码，但需测试证明。
- **不回退**：`/skills`、`/skills_private` knownMount 与行为保持不变。

## Acceptance Criteria

- [ ] `WorkspaceKnownMountTest` 增加 `/upload` 映射用例：`/upload/<file>` → `filesDir/upload/<file>`，`..` 越界拒绝。
- [ ] 主/子代理 knownMounts 列表均含 `/upload`（代码位置可断言或由实现抽常量后断言）。
- [ ] `workspace_read_file` 工具描述含 `/upload`。
- [ ] fork 拷贝后新 UUID path 仍可经 `resolveKnownMountFile` 解析（单测覆盖）。
- [ ] `/skills`、`/skills_private` 既有测试不回归。

## Notes

- 历史参考：#34/#37 `/skills` 同类修复模式；`FileFolders.UPLOAD = "upload"`（FilesManager.kt:490）。
- 主/子代理两处 list 现为独立内联构造；若抽共用 `defaultKnownMounts()` helper 需确保两处一致且可单测。
