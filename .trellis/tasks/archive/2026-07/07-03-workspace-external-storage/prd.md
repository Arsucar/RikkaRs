# PRD: Workspace Project Files External Storage

**Source**: GitHub issue #30 (feat: 工作区项目文件支持应用外部存储目录)
**Parent task**: `07-03-workspace-external-storage` (this)
**Child task**: `07-03-workspace-backup-eval` (backup/WebDAV coverage, out of MVP scope)

## 1. Background & Problem

当前工作区的「项目文件」目录（proot 内挂载为 `/workspace`）硬编码在应用私有目录：

```
/data/user/0/<pkg>/files/workspaces/<root>/files/
```

该路径对用户不可见（无 root 无法用文件管理器/USB/PC 直接访问），导致用户无法在外部编辑 `docs/AGENT.md`、技能说明等文本，每次修改只能走「导出 → 改 → 再导入」。

## 2. Goal

让用户能通过一个**全局开关**，把所有工作区的项目文件 `files/` 目录切换到**应用专属外部存储**（`getExternalFilesDir`），使其可通过文件管理器/USB/PC 直接编辑；同时保持 proot 工作流（`/workspace` bind、AI shell 工具、交互式终端）读到的仍是同一份文件。

## 3. Scope

### In Scope (MVP)

- 全局 app 设置：项目文件存储位置 = `PRIVATE`（现状，默认）或 `EXTERNAL`（`getExternalFilesDir`）。
- 切换存储位置时，对**已存在的所有工作区**执行一次性迁移（复制 + 校验 + 删源），成功后只使用新路径（单一真相源）。
- `WorkspaceTerminalSession` 去除硬编码，统一从 `WorkspaceManager` 取 files 根路径。
- 工作区「基本信息」UI 展示当前项目文件在设备上的绝对路径。
- 外部存储不可用时给出明确错误（不静默回退到私有）。

### Out of Scope (本次不做)

- 公共 `Download/rikkahub`（需 SAF / 宽存储权限）—— 后续扩展。
- per-workspace 独立存储选择 —— 后续扩展。
- proot rootfs（`linux/`）迁移 —— 仍留私有目录。
- `extraBindMounts`（`/skills`、`/tool_outputs`、`/upload`）—— 仍绑 `context.filesDir`，不动。
- 备份 / WebDAV 对外部目录的覆盖评估 —— 拆到子任务 `07-03-workspace-backup-eval`。
- `WorkspaceShellPolicy` 对 `/storage/`、`/sdcard/` 启发式拦截的调整 —— 外部根路径为 `Android/data/...`，不命中这些字符串，本轮不动策略，仅在文档提示。

## 4. Functional Requirements

### FR-1: 全局存储位置设置

- 新增 app 级设置项 `workspace_files_storage`，枚举值 `PRIVATE` | `EXTERNAL`，默认 `PRIVATE`。
- 设置入口位于「设置 → 工作区」或「工作区列表页」的设置项中（实现时定）。
- 切换值时触发迁移流程（FR-3）。

### FR-2: 路径解析

- `PRIVATE`: `context.filesDir/workspaces/<root>/files`（现状，不变）。
- `EXTERNAL`: `context.getExternalFilesDir("workspaces")/<root>/files`，即 `Android/data/<pkg>/files/workspaces/<root>/files`。
- `WorkspaceManager.filesDir(root)` 必须根据当前全局设置解析路径。
- `linux/`（rootfs）、`tmp/` 仍基于私有 `baseDir`，不随存储设置变化。

### FR-3: 迁移流程（切换存储位置时）

1. 校验目标根可写（外部模式需 `getExternalFilesDir` 非 null 且可写）。
2. 全局加锁，禁用所有 workspace 操作（AI shell、终端、文件浏览），直到迁移完成或失败。
3. 对每个已存在的 workspace `<root>`：
   a. 复制 `files/` 树到目标根（保留权限/可执行位）。
   b. 校验：文件数 + 每个文件的 size 一致（MVP 粒度，不做 byte 级 hash 以控制耗时；大文件场景若需要可后续加 hash）。
   c. 校验通过 → 删除源 `files/`。
   d. 校验失败 → 回滚（删除已复制的目标，保留源），中止迁移，设置值不变，向用户报错。
4. 全部成功 → 更新全局设置值，解锁。

### FR-4: 终端路径对齐

- `WorkspaceTerminalSession` 中 3 处硬编码 `filesDir/workspaces/<root>/files` 改为调用 `WorkspaceManager` 的路径解析方法（或共享的路径解析器），确保终端 bind 的 `/workspace` 与 AI shell 指向同一物理目录。

### FR-5: UI 展示路径

- 工作区「基本信息」页（`WorkspaceDetailPage`）展示当前项目文件在设备上的绝对路径，方便用户用文件管理器定位。
- 外部模式下展示外部路径；私有模式下展示私有路径。

### FR-6: 外部存储不可用处理

- 切换到 EXTERNAL 时若 `getExternalFilesDir` 返回 null 或不可写，直接报错，不切换、不迁移。
- 运行时（已处于 EXTERNAL 模式）若外部存储被卸载/不可用，workspace 操作应给出明确错误提示，不静默回退到私有（避免「以为在外部其实在私有」的混淆）。

## 5. Non-Functional Requirements

- **数据安全**: 迁移必须原子（per-workspace 复制+校验+删源，失败可回滚）；绝不出现「迁移中断导致两边都不完整」。
- **性能**: 迁移在后台线程执行，UI 显示进度；大 workspace 不阻塞主线程。
- **兼容性**: 已有用户升级后默认仍为 PRIVATE，无感知；不强制迁移。
- **proot 稳定性**: 外部路径仍为真实 `File.absolutePath`，proot bind 行为不变。

## 6. Acceptance Criteria

- [ ] AC-1: 全局设置可在 PRIVATE / EXTERNAL 间切换；默认 PRIVATE。
- [ ] AC-2: 切换到 EXTERNAL 后，新建工作区的 `files/` 创建在外部目录；已有工作区的 `files/` 已迁移到外部。
- [ ] AC-3: 迁移完成后，私有与外部**不同时**保留 `files/`（单一真相源）。
- [ ] AC-4: AI shell（`workspace_shell` 工具）与交互式终端访问 `/workspace` 指向同一物理目录（EXTERNAL 模式下均指向外部）。
- [ ] AC-5: 工作区基本信息页展示当前项目文件绝对路径。
- [ ] AC-6: 外部存储不可用时，切换操作报错且不改变现状；运行中外部失效时 workspace 操作报明确错误。
- [ ] AC-7: 迁移失败时回滚，设置值与文件位置均不变，用户可重试。
- [ ] AC-8: `linux/`（rootfs）始终在私有目录，不受存储设置影响。
- [ ] AC-9: 单元测试覆盖路径解析（PRIVATE/EXTERNAL）与迁移流程（成功/失败回滚）。

## 7. Open Questions (实现阶段确认)

- 迁移进度 UI 的形式（对话框 / 通知 / 页面内进度条）？MVP 倾向页面内进度 + 禁用交互。
- 是否需要记录迁移日志供用户排查？MVP 倾向最少日志 + 失败时展示错误。
