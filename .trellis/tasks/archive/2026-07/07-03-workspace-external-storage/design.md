# Design: Workspace Project Files External Storage

**Task**: `07-03-workspace-external-storage`
**Decisions (from planning)**: 全局开关 / 复制+校验+删源 / 备份拆子任务 / rootfs 不迁 / 外部根用 `getExternalFilesDir("workspaces")`

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  app settings (DataStore)                               │
│    workspace_files_storage: PRIVATE | EXTERNAL          │
└───────────────────┬─────────────────────────────────────┘
                    │ read
                    ▼
┌─────────────────────────────────────────────────────────┐
│  WorkspacePathResolver (new, in workspace module)       │
│    - filesBaseDir(storage): File                        │
│        PRIVATE  → context.filesDir/workspaces           │
│        EXTERNAL → context.getExternalFilesDir("workspaces") │
│    - rootfsBaseDir: File  (always context.filesDir/workspaces,不变) │
│    - resolveFilesDir(root): depends on current storage  │
└───────────────────┬─────────────────────────────────────┘
                    │ used by
        ┌───────────┴────────────┐
        ▼                        ▼
┌──────────────┐        ┌──────────────────┐
│ WorkspaceManager │    │ WorkspaceTerminalSession │
│  (AI shell)   │        │  (交互终端)        │
└──────────────┘        └──────────────────┘
```

**核心思路**：引入一个 `WorkspacePathResolver`，集中管理 files 根路径的解析（私有 vs 外部），由 `WorkspaceManager` 和 `WorkspaceTerminalSession` 共享。全局设置驱动 resolver 的行为。

## 2. Key Components

### 2.1 存储设置

**位置**: 复用现有 app 设置存储（DataStore / `app_settings` 表，与现有设置一致）。需确认现有设置持久化方式后对齐。

**新增字段**:
```kotlin
enum class WorkspaceFilesStorage { PRIVATE, EXTERNAL }
```
持久化为字符串。默认 `PRIVATE`。

### 2.2 WorkspacePathResolver (新增)

放在 `workspace` 模块，避免 app 层硬编码。职责：根据存储设置 + Android Context 解析路径。

```kotlin
class WorkspacePathResolver(
    private val appContext: Context,
    private val storageProvider: () -> WorkspaceFilesStorage,
) {
    // files 根: 受存储设置影响
    val filesBaseDir: File
        get() = when (storageProvider()) {
            PRIVATE -> File(appContext.filesDir, "workspaces")
            EXTERNAL -> File(
                appContext.getExternalFilesDir("workspaces")
                    ?: error("External files dir unavailable"),
                "workspaces"  // 注: getExternalFilesDir("workspaces") 已含 workspaces, 见下方决策
            )
        }

    // rootfs 根: 始终私有, 不受设置影响
    val rootfsBaseDir: File
        get() = File(appContext.filesDir, "workspaces")

    fun workspaceDir(root: String): File = File(filesBaseDir, root)
    fun filesDir(root: String): File = File(workspaceDir(root), "files")
    fun linuxDir(root: String): File = File(File(rootfsBaseDir, root), "linux")
    fun tempDir(root: String): File = File(File(rootfsBaseDir, root), "tmp")
}
```

**路径决策（EXTERNAL 模式）**:
- `getExternalFilesDir(null)` → `Android/data/<pkg>/files/`
- 我们要 `Android/data/<pkg>/files/workspaces/<root>/files`
- 用 `getExternalFilesDir("workspaces")` → `Android/data/<pkg>/files/workspaces/`，然后拼 `<root>/files`。
- **注意**: `getExternalFilesDir` 可能返回 null（外部存储未挂载），resolver 在 EXTERNAL 模式下访问 `filesBaseDir` 时若为 null 抛出明确异常，由调用方捕获并向用户报错。

### 2.3 WorkspaceManager 改造

现状 `WorkspaceManager(baseDir, shellRunner, ...)` 把 files 和 rootfs 都基于同一个 `baseDir`。

**改造**:
- 构造改为接收 `WorkspacePathResolver`（或同时接收 `filesBaseDirProvider` + `rootfsBaseDir`）。
- `filesDir(root)` / `linuxDir(root)` / `tempDir(root)` 改为委托 resolver。
- `ensureWorkspace(root)`: files 目录在 resolver 解析的位置创建；linux/tmp 始终在私有 baseDir 下创建。

**向后兼容**: 现有 `baseDir` 参数语义保留（用于 rootfs），files 路径改走 resolver。

### 2.4 WorkspaceTerminalSession 改造（去硬编码）

现状 3 处硬编码（`WorkspaceTerminalSession.kt` L28-32, L90-92, L102）:
```kotlin
val workspaceDir = File(File(appContext.filesDir, "workspaces"), root)
val filesDir = File(workspaceDir, "files")
val linuxDir = File(workspaceDir, "linux")
val tempDir = File(workspaceDir, "tmp")
```

**改造**: 注入 `WorkspacePathResolver`，改为:
```kotlin
val filesDir = pathResolver.filesDir(root)
val linuxDir = pathResolver.linuxDir(root)
val tempDir = pathResolver.tempDir(root)
```

`prepareWorkspaceTerminalSession` / `workspaceRootfsReady` 同样改。

### 2.5 RepositoryModule 改造

```kotlin
single {
    val context = get()
    val pathResolver = WorkspacePathResolver(
        appContext = context,
        storageProvider = { settingsRepository.get().workspaceFilesStorage }
    )
    WorkspaceManager(
        pathResolver = pathResolver,
        shellRunner = ProotShellRunner(...),  // extraBindMounts 不变
    )
}
```

### 2.6 迁移服务 (新增)

负责切换存储位置时的文件迁移。独立类，便于测试。

```kotlin
class WorkspaceStorageMigrator(
    private val pathResolver: WorkspacePathResolver,
    private val workspaceRepository: WorkspaceRepository,
    private val lockState: WorkspaceGlobalLock,  // 新增全局锁
) {
    suspend fun migrate(target: WorkspaceFilesStorage): MigrationResult {
        val current = pathResolver.currentStorage()
        if (current == target) return MigrationResult.Noop

        // 1. 校验目标可写
        val targetBase = pathResolver.peekFilesBaseDir(target)
            ?: return MigrationResult.Failed("External storage unavailable")
        if (!targetBase.canWrite()) return MigrationResult.Failed("Target not writable")

        // 2. 全局加锁, 阻止 workspace 操作
        lockState.lock()

        try {
            val roots = workspaceRepository.getAllRoots()
            // 3. 逐个迁移
            for (root in roots) {
                migrateOneWorkspace(root, current, target)
            }
            // 4. 全部成功 -> 切换设置
            settingsRepository.update { it.copy(workspaceFilesStorage = target) }
            return MigrationResult.Success(roots.size)
        } catch (e: Exception) {
            // 失败: 单个 workspace 内部已回滚; 已成功的 workspace 需回滚(见 3.3)
            rollbackCompleted(roots, current, target)
            return MigrationResult.Failed(e.message ?: "Unknown error")
        } finally {
            lockState.unlock()
        }
    }

    private fun migrateOneWorkspace(root, current, target) {
        val src = pathResolver.peekFilesDir(root, current)
        val dst = pathResolver.peekFilesDir(root, target)
        // 复制 + 校验 + 删源 (见 3.2)
        // 失败抛异常, 触发上层回滚
    }
}
```

### 2.7 全局锁 (新增)

防止迁移期间 AI shell / 终端访问半移动的文件。

```kotlin
class WorkspaceGlobalLock {
    private val locked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    fun lock() { locked.value = true }
    fun unlock() { locked.value = false }
}
```

`WorkspaceManager.executeCommand` / `WorkspaceTerminalSession` 启动前检查 `isLocked`，锁定中拒绝并提示「正在迁移存储，请稍候」。

### 2.8 UI 改造

**设置入口** (位置: 实现时定, 倾向工作区设置区):
- 当前存储位置显示 + 切换按钮。
- 切换时弹出确认（提示会迁移所有工作区文件），确认后触发迁移，显示进度，期间禁用相关操作。

**WorkspaceDetailPage**:
- 「基本信息」区新增一行：项目文件路径 = `<绝对路径>`（长按可复制）。

## 3. Critical Flows

### 3.1 正常切换 (PRIVATE → EXTERNAL)

```
用户点切换到 EXTERNAL
  → 确认对话框
  → Migrator.migrate(EXTERNAL)
    → 校验外部可写
    → 全局锁 lock
    → for each workspace root:
        src = filesDir(root, PRIVATE)
        dst = filesDir(root, EXTERNAL)
        copyTree(src → dst)         // 保留权限
        verify(src, dst)            // 文件数 + size
        if ok: deleteRecursive(src)
        else: throw + cleanup(dst)
    → 更新设置 workspaceFilesStorage = EXTERNAL
    → 全局锁 unlock
  → UI 提示成功
```

### 3.2 单个 workspace 复制+校验+删源

```
migrateOneWorkspace(root):
  srcFiles = peekFilesDir(root, current)
  dstFiles = peekFilesDir(root, target)
  if !srcFiles.exists(): return  // 空 workspace, 跳过
  if dstFiles.exists(): 
    // 目标已存在(可能是上次失败残留), 清理后重做
    deleteRecursive(dstFiles)
  copyTree(srcFiles → dstFiles)
  if !verifyEqual(srcFiles, dstFiles):
    deleteRecursive(dstFiles)   // 清理目标
    throw VerifyFailed(root)
  deleteRecursive(srcFiles)     // 单一真相源: 删源
```

**verifyEqual**: 递归比较文件数 + 每文件 size。MVP 不做 hash（耗时），大文件场景后续可加。

### 3.3 失败回滚策略

- **单个 workspace 内失败**: 该 workspace 已复制的目标删除，源未删，该 workspace 状态一致（仍在源）。抛异常给上层。
- **上层捕获后**: 对**已成功迁移**的 workspace 执行反向迁移（从新目标迁回源），使全部回到起始状态。
- **设置值不变**: `workspaceFilesStorage` 只在全部成功后才更新，失败时保持原值。
- **极端情况（反向迁移也失败）**: 记录错误日志，提示用户手动处理；MVP 不保证此场景自动恢复。

### 3.4 外部存储不可用

- **切换时**: `getExternalFilesDir` 为 null 或不可写 → `MigrationResult.Failed`，不切换。
- **运行时（已 EXTERNAL）**: workspace 操作（shell/终端/文件浏览）解析 `filesDir` 时若外部 null，抛 `ExternalStorageUnavailableException`，UI 层捕获并提示「外部存储不可用，请在设置中切换回私有存储或重新挂载」。

## 4. Data Model Changes

### 4.1 app_settings 扩展

需确认现有 `app_settings` 持久化结构（DataStore Preferences / Room entity / DataClass）。新增字段:
```kotlin
val workspaceFilesStorage: WorkspaceFilesStorage = WorkspaceFilesStorage.PRIVATE
```
持久化为字符串 "PRIVATE" / "EXTERNAL"。若是 Room `AppSettings` entity，加 `@ColumnInfo` + 默认值，需 migration（bump schema version）。

### 4.2 WorkspaceEntity

**不变**。`root` 仍为目录名。存储位置由全局设置决定，不 per-row 记录。

## 5. Module / Layer Impact

| 文件 | 模块 | 改动 |
|------|------|------|
| `workspace/.../WorkspacePathResolver.kt` | workspace | **新增** |
| `workspace/.../WorkspaceManager.kt` | workspace | 改: filesDir/linuxDir/tempDir 走 resolver |
| `workspace/.../WorkspaceGlobalLock.kt` | workspace | **新增** |
| `app/.../di/RepositoryModule.kt` | app | 改: 注入 resolver + migrator + lock |
| `app/.../workspace/WorkspaceTerminalSession.kt` | app | 改: 去硬编码, 用 resolver |
| `app/.../workspace/WorkspaceStorageMigrator.kt` | app | **新增** |
| `app/.../data/model/AppSettings.kt` (或对应设置类) | app | 改: 加 workspaceFilesStorage 字段 |
| `app/.../workspace/WorkspaceDetailPage.kt` | app | 改: 展示路径 |
| 设置页 UI | app | 改: 加存储位置切换入口 |

## 6. Tradeoffs & Decisions

- **全局开关 vs per-workspace**: 选全局，实现简单；per-workspace 留后续。代价：用户无法混合（部分私有部分外部）。
- **复制+校验+删源 vs 移动**: 选前者，失败可回滚。代价：迁移期间临时双倍磁盘占用。
- **校验粒度 size vs hash**: 选 size，快。代价：理论上有 size 相同但内容不同（极罕见）。
- **外部根 `getExternalFilesDir` vs 公共 Download**: 选前者，无需 SAF 权限，proot bind 稳定。代价：卸载即删（需备份，已拆子任务）。
- **外部不可用静默回退 vs 报错**: 选报错，避免混淆。代价：外部失效时 workspace 不可用直到用户处理。

## 7. Risks

- **迁移中断（进程被杀/断电）**: 单个 workspace 内已删源但未完成 → 该 workspace 损坏。缓解：先完整复制+校验再删源；MVP 接受极端场景需手动恢复，文档提示备份。
- **proot 对外部路径的兼容性**: 外部路径在 `Android/data/` 下，proot bind 理论无差异（均为真实路径），但需真机验证 symlink/权限行为。
- **现有用户升级**: 默认 PRIVATE，无感知；只有主动切换才迁移。无风险。

## 8. Test Strategy

- **单元测试**:
  - `WorkspacePathResolver`: PRIVATE/EXTERNAL 路径解析正确性；外部 null 时行为。
  - `WorkspaceStorageMigrator`: 成功迁移；单 workspace 校验失败回滚；目标已存在清理；空 workspace 跳过。
  - 全局锁: lock 期间 executeCommand 被拒绝。
- **真机验证**:
  - 切换到 EXTERNAL 后，文件管理器可见 `Android/data/<pkg>/files/workspaces/<root>/files/`。
  - 外部修改文件后，proot 内 `/workspace` 与终端读到更新（同会话/下次命令）。
  - AI shell 工具与终端指向同一物理目录。
