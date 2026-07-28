# 设计：#184 WebDAV/S3 备份恢复原子化与 dummy 设置污染修复

## 背景与四个缺陷

`WebDavSync.restoreFromBackupFile`（`WebDavSync.kt:151`）与 `S3Sync.restoreFromBackupFile`
（`S3Sync.kt:129`）逻辑几乎完全重复，均存在四个叠加缺陷：

1. **非原子覆写**：db/wal/shm 直接流式写入在用库 `context.getDatabasePath("rikka_hub")`
   （`WebDavSync.kt:180-217` / `S3Sync.kt:158-195`）。写一半失败即损坏正式库。
2. **外键校验后置**：`validateRestoredDatabaseForeignKeys` 在覆写之后才执行
   （`WebDavSync.kt:287` / `S3Sync.kt:265`），坏包已污染正式库、无法回滚。
3. **settings 先生效 + 缺开关校验**：`settings.json` 分支遍历到即 `settingsStore.update`
   （`WebDavSync.kt:170` / `S3Sync.kt:148`），此时数据库仍是旧的/写一半，形成
   「新设置 + 旧数据库」撕裂状态；且未校验 `config.items` 是否包含 DATABASE 开关。
4. **dummy 设置污染**：`BackupArchive.create` 取 `settingsStore.settingsFlow.value`
   （`BackupArchive.kt:58`）。若此时 flow 仍为 `Settings.dummy()`（`init=true`），会把占位
   设置写进备份。

## 关键机制（已确认）

- 恢复完成后不自动重启，由 `BackupDialog` 的 `exitProcess(0)` 提示用户手动重启
  （`components/BackupDialog.kt:20`）。因此恢复只需保证「进程存活期间落盘的库 = 校验通过的完整库」，
  重启后 Room 以新文件重新打开即可。
- `SettingsStore.update` 已有 `init` 守卫（`PreferencesStore.kt:557`）：dummy 设置不会被写入。
- Room 以 WAL 模式打开（`DataSourceModule.kt:73`），`AppDatabase` 未暴露 `close()`，恢复流程
  不主动 close Room；改为把恢复后的库写到目标路径，重启后由新进程打开（与既有行为一致）。
- `openRequeryDatabase` / `validateRestoredDatabaseForeignKeys` / `validateDatabaseIntegrity`
  已存在于 `DatabaseIntegrity.kt`，可直接复用做落盘前校验。

## 方案

### 抽取共享恢复器 `BackupRestorer`（新文件 `data/sync/BackupRestorer.kt`）

WebDav 与 S3 的 restore 正文完全同构，差异仅在 `config.items` 的枚举类型。抽取一个不依赖具体
config 的共享恢复器，入参用布尔开关而非各自的 enum：

```
class BackupRestorer(
    private val context: Context,
    private val json: Json,
    private val settingsStore: SettingsStore,
) {
    suspend fun restore(
        backupFile: File,
        includeDatabase: Boolean,
        includeFiles: Boolean,
    )
}
```

两个 Sync 类各自把 `config.items.contains(DATABASE/FILES)` 归一为布尔后调用它，消除重复。

### 原子恢复流程（restore 正文）

1. **解包到临时目录**：在 `context.cacheDir` 下建唯一临时目录 `restore_<ts>/`，把 zip 内所有条目
   解包到临时目录（db/wal/shm、settings.json、upload/skills/fonts）。全程 `ensureActive()` 支持取消，
   路径沿用现有 `resolveContainedFile` / `SkillPaths` 防穿越。
2. **落盘前校验**（缺陷 2）：若 `includeDatabase` 且临时目录内存在 `rikka_hub.db`：
   - 先删除临时目录内孤立的 wal/shm（若备份未含），保证校验对象一致；
   - 用 `validateDatabaseIntegrity(tempDb, context)` + `validateRestoredDatabaseForeignKeys(tempDb, context)`
     在**临时库**上校验；任一失败抛异常，**此时正式库尚未被触碰**，天然回滚。
3. **settings 校验但不立即生效**（缺陷 3）：先解析 + 迁移 settings.json 得到 `Settings` 对象，
   仅在内存中持有，**不** `settingsStore.update`。
4. **原子替换数据库**（缺陷 1）：校验通过后，
   - 先把当前在用库三件套备份为 `.bak`（用于替换失败回退）；
   - 将临时库 `rename` 到目标路径（同一 filesystem，`File.renameTo` 原子）；wal/shm 同理，
     备份未含的一律删除目标端；
   - 任一步失败则从 `.bak` 恢复原库并抛出，正式库保持恢复前状态。
5. **settings 最后应用**（缺陷 3）：数据库替换成功后才 `settingsStore.update(settings)`，
   保证「新设置 + 新数据库」一致落盘。若 `includeDatabase=false`，settings 仍在文件恢复后应用。
6. 成功后清理临时目录与 `.bak`；`finally` 保证临时目录始终清理。

### 备份侧 dummy 守卫（缺陷 4）

`BackupArchive.create`：读取 `settingsStore.settingsFlow.value` 后，若 `settings.init == true`
（即 `Settings.dummy()`），抛 `IllegalStateException("Cannot back up before settings are loaded")`，
拒绝生成含占位设置的备份。调用方（backup/prepareBackupFile）向用户回显该错误。

## 影响文件

- 新增：`data/sync/BackupRestorer.kt`
- 修改：`data/sync/webdav/WebDavSync.kt`（restoreFromBackupFile 委托 BackupRestorer）
- 修改：`data/sync/S3Sync.kt`（同上）
- 修改：`data/sync/BackupArchive.kt`（create 加 dummy 守卫）
- DI：`di/DataSourceModule.kt`（注册 BackupRestorer 并注入两个 Sync）

## 验收标准

- [ ] 恢复中途失败（坏包/校验不过/写入异常），正式数据库与 settings 保持恢复前状态。
- [ ] 外键/完整性校验在**替换正式库之前**于临时库上完成，失败不触碰正式库。
- [ ] settings 仅在数据库替换成功后应用，不再出现「新设置 + 旧库」撕裂。
- [ ] `config.items` 不含 DATABASE 时不替换数据库；不含 FILES 时不恢复文件。
- [ ] `BackupArchive.create` 在 settings 为 dummy 时拒绝备份，不写占位设置。
- [ ] WebDav 与 S3 共用同一恢复实现，无重复逻辑。

## 测试

- `BackupRestorer` 单元/仪器测试：正常恢复、坏库校验失败保留原库、settings 后置、
  仅 DATABASE / 仅 FILES / 都不含开关组合。
- `BackupArchive` dummy 守卫测试。
- 复用现有 `BackupArchiveIntegrationTest` / `BackupArchiveZipTest` 验证不回归。
