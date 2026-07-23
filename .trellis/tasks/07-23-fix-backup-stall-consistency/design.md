# 设计：备份生命周期、归档与数据库快照

## Boundaries

数据流调整为：

`Compose action -> BackupVM -> BackupTaskCoordinator -> archive/sync/restore IO -> terminal state -> Compose feedback`

数据库归档调整为：

`Room writable database -> VACUUM INTO unique temp snapshot -> integrity validation -> ZIP rikka_hub.db`

## Task State Contract

- `BackupTaskState.Running` 携带 `BackupTaskStage`，阶段由 VM 和 sync 层在真实边界更新。
- Coordinator 在接受任务前验证 scope 可用，并使用 lazy IO coroutine 消除“先 Running、任务未登记/未启动”的竞态。
- Coroutine body 用 `try/catch/finally` 覆盖取消和所有 Throwable；取消保留 coroutine cancellation 语义，其他错误写入 Failed。
- 任务增加总 watchdog。网络、文件复制和 ZIP 循环在可取消点传播取消；无法中断的底层系统调用返回后仍不得写 Success。
- Coordinator 继续按 `BackupOperation` 去重；同一操作拒绝重复，不同操作允许并行，因此每个临时文件必须唯一。

## Archive Writer

- 抽出 WebDAV/S3 共用的归档生成器，统一 settings、数据库和 files entry 行为。
- 使用 `File.createTempFile`，避免秒级命名冲突。
- 使用 `Deflater.BEST_SPEED` 和显式 64 KiB buffer；循环中检查 coroutine active 状态。
- skills 递归使用 canonical path/root containment 和 visited directory 集合，拒绝越界或循环；不可读目录作为失败处理，避免静默生成不完整备份。
- 归档生成器在任何失败/取消时删除临时 ZIP。

## Database Snapshot

- 注入 `AppDatabase`，通过 `openHelper.writableDatabase.execSQL("VACUUM INTO ?", ...)` 生成预先不存在的临时数据库。
- 快照完成后用现有数据库完整性工具或等价 SQL 校验 `integrity_check` 与 `foreign_key_check`，通过后才写入 ZIP。
- 新 ZIP 不包含 WAL/SHM。恢复旧 ZIP 时仍识别 sidecar；恢复新 ZIP 时主动删除目标旧 sidecar，避免旧 WAL 重放到新 main DB。
- 不提供 checkpoint+copy fallback：它不能在并发写入下保证主文件一致。快照能力失败时明确让备份失败。

## Defensive Migration

- `AppDatabase` version 44 -> 45。
- `Migration_44_45` 查询 `PRAGMA table_info(message_node)`；缺少 `compress_hidden_count` 时添加 nullable INTEGER 列，存在时不执行 DDL。
- 该迁移专门修复旧/不一致备份造成的 schema 身份与实际列不一致；保留正确的 34->35 迁移。

## UI Contract

- 运行状态在现有按钮/卡片 supporting text 中显示，不新增嵌套卡。
- 运行中的主按钮或 trailing action 提供明确的“取消备份/取消恢复/取消导入导出”，带可访问描述。
- Success、Failed、Cancelled 只由一个承载面消费，避免 inline 与 toaster 重复；恢复/导入成功继续使用重启对话框。
- 验证空、正常、长文本、失败、取消、窄屏、横屏、大字体、浅/深色和 TalkBack 描述。

## Compatibility And Rollback

- ZIP entry 名称保持兼容；恢复路径继续接受旧 sidecar。
- 数据库 version 一旦升至 45 不回退；回滚 APK 前需保留新版本备份。
- 如果快照实现验证失败，回滚归档调用到旧实现但保留 coordinator 状态修复；不得回滚 44->45 防御迁移。
