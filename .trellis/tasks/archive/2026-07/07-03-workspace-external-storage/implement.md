# Implement: Workspace Project Files External Storage

**Task**: `07-03-workspace-external-storage`
**依赖**: PRD + design 已评审通过

## Execution Checklist (ordered)

### Phase A: 基础设施 (workspace 模块)

- [ ] A1. 确认现有 app 设置持久化方式（DataStore / Room AppSettings entity）。读取相关文件确定 `workspaceFilesStorage` 字段加在哪、是否需要 Room migration。
- [ ] A2. 新增 `WorkspaceFilesStorage` 枚举（workspace 模块或 app model 层，看现有设置类位置）。
- [ ] A3. 新增 `WorkspacePathResolver`（workspace 模块）:
  - `filesBaseDir` / `rootfsBaseDir` / `workspaceDir(root)` / `filesDir(root)` / `linuxDir(root)` / `tempDir(root)`。
  - `peekFilesBaseDir(storage)` / `peekFilesDir(root, storage)` 供迁移器使用（不依赖当前设置）。
- [ ] A4. 新增 `WorkspaceGlobalLock`（workspace 模块）。
- [ ] A5. 改造 `WorkspaceManager`:
  - 构造改为接收 `WorkspacePathResolver`（保留 `baseDir` 用于 rootfs，或全部委托 resolver）。
  - `filesDir` / `linuxDir` / `tempDir` 委托 resolver。
  - `executeCommand` 入口检查 `isLocked`，锁定中拒绝并返回明确错误。
  - `ensureWorkspace` / `listFiles` 等路径相关方法对齐 resolver。
- [ ] A6. 单测 `WorkspacePathResolverTest`: PRIVATE/EXTERNAL 路径正确性 + 外部 null 行为。
- [ ] A7. 编译 workspace 模块（`.\gradlew :workspace:compileDebugKotlin --no-daemon`）。

### Phase B: app 层 DI 与设置

- [ ] B1. app 设置类加 `workspaceFilesStorage` 字段（默认 PRIVATE）；若 Room entity 需写 migration（bump version + AutoMigration 或 Manual）。
- [ ] B2. `RepositoryModule` 改造: 构造 `WorkspacePathResolver`（storageProvider 读设置）、`WorkspaceGlobalLock`，注入 `WorkspaceManager`。
- [ ] B3. 新增 `WorkspaceStorageMigrator`（app 层）:
  - `migrate(target)`: 校验目标 → 全局锁 → 逐 workspace 复制+校验+删源 → 更新设置 → 解锁。
  - 失败回滚逻辑（单 workspace 内清理 + 已成功的反向迁移）。
- [ ] B4. 单测 `WorkspaceStorageMigratorTest`: 成功 / 校验失败回滚 / 目标已存在清理 / 空 workspace 跳过 / 外部不可用。
- [ ] B5. 编译 app 模块（`.\gradlew :app:compileDebugKotlin --no-daemon`）。

### Phase C: 终端路径对齐

- [ ] C1. 改 `WorkspaceTerminalSession.kt` 3 处硬编码（L28-32, L90-92, L102）→ 用 `WorkspacePathResolver`。
- [ ] C2. `prepareWorkspaceTerminalSession` / `workspaceRootfsReady` 对齐。
- [ ] C3. 终端启动前检查 `WorkspaceGlobalLock`，锁定中拒绝。
- [ ] C4. 编译验证。

### Phase D: UI

- [ ] D1. 设置页加「工作区存储位置」入口（当前值显示 + 切换）；切换走确认对话框 → 迁移 → 进度提示。
- [ ] D2. `WorkspaceDetailPage` 基本信息区展示项目文件绝对路径（长按复制）。
- [ ] D3. 迁移期间相关操作禁用（观察 `WorkspaceGlobalLock.isLocked`）。
- [ ] D4. 外部存储不可用时的错误提示 UI。
- [ ] D5. 编译验证。

### Phase E: 集成验证

- [ ] E1. 全量编译 `.\gradlew :app:assembleDebug --no-daemon`。
- [ ] E2. 安装到设备 `.\gradlew :app:installDebug --no-daemon`（需 `adb devices` 有设备）。
- [ ] E3. 真机验证清单:
  - 默认 PRIVATE，工作区正常。
  - 切换到 EXTERNAL，迁移成功，文件管理器可见 `Android/data/<pkg>/files/workspaces/<root>/files/`。
  - 外部修改文件 → proot `/workspace` 与终端读到更新。
  - AI shell 工具与终端指向同一物理目录。
  - 切换回 PRIVATE，反向迁移成功。
  - 外部存储不可用时切换报错。
  - 基本信息 UI 展示正确路径。
- [ ] E4. 运行单元测试 `.\gradlew test --no-daemon`。

## Validation Commands

```bash
# 模块编译
.\gradlew :workspace:compileDebugKotlin --no-daemon
.\gradlew :app:compileDebugKotlin --no-daemon

# 单测
.\gradlew test --no-daemon

# 完整构建+安装
.\gradlew :app:installDebug --no-daemon

# 设备检查
adb devices
```

## Review Gates

- A 阶段完成后: workspace 模块编译通过 + resolver 单测通过 → 继续 B。
- B 阶段完成后: migrator 单测通过（成功/回滚/空/不可用） → 继续 C。
- C 阶段完成后: 终端与 manager 路径一致（代码审查） → 继续 D。
- D 阶段完成后: 全量编译 + 基础 UI 可用 → 进入 E 真机验证。

## Rollback Points

- A/B/C/D 各阶段独立，失败可回退到上一阶段。
- 代码层面: 所有改动在分支上，未 commit 前可整体回退。

## Notes

- 实现时如发现 design 中的路径解析细节（如 `getExternalFilesDir("workspaces")` 的实际返回）与预期不符，回到 design 更新后再继续。
- `extraBindMounts`（skills/tool_outputs/upload）**不动**，确认改动未波及。
- 子任务 `07-03-workspace-backup-eval` 独立，本任务不涉及。
