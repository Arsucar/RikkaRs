# Issue #143 备份任务离页续跑

## Goal

将备份/导入导出任务从单个 Compose 页面生命周期中解耦，使用户返回或切换备份 Tab 后任务继续运行，并正确报告完成、取消和失败。

## Confirmed Facts

- WebDAV、S3、本地导入导出均由 `rememberCoroutineScope` 启动；Tab dispose 会取消任务，返回页面还会销毁 NavEntry 级 `BackupVM`。
- 三条路径的进度状态是 Composable 本地 `remember`，且 `runCatching`/`catch(Exception)` 会把取消包装成业务错误。
- 本地导出当前在 SAF copy 完成前就记录 `lastBackupTime`；WebDAV/S3/本地临时文件清理也不完整。

## Requirements

- 引入进程内、生命周期独立的备份任务协调层（或等价 singleton AppScope 服务），持有任务 Job、状态和取消入口；BackupVM/UI 只负责触发与观察。
- 覆盖 WebDAV、S3、本地导出及现有恢复入口，至少保证离页不静默失败；避免同类任务重复并发。
- 状态至少区分 Running、Success、Failed、Cancelled，并在页面重进后仍可观察终态；完成通知不能依赖一次性 Toast。
- 所有层保留 `CancellationException` 语义；主动取消文案与普通错误分离，离页取消不得显示失败文案。
- 只有完整成功后才更新 `lastBackupTime`；所有临时文件在成功、失败、取消路径均清理；空输出流视为失败。

## Acceptance Criteria

- [ ] 备份中途切换 Tab 或返回上一页，WebDAV/S3/本地导出仍完成或处于可见的明确状态，不出现 composition left 文案。
- [ ] 主动取消（如保留取消入口）显示已取消，不触发失败 Toast、不更新成功时间。
- [ ] 网络/凭证/文件错误仍显示业务失败，状态可在重新进入页面后读取。
- [ ] 本地导出仅在 SAF 输出流成功关闭后记录 `lastBackupTime`；`openOutputStream == null` 失败。
- [ ] 临时 zip/导入文件在所有终态清理；重复点击不会启动同类并发任务。
- [ ] 有 coordinator/task-manager 单测覆盖离页等价场景、取消分类、失败、成功和状态持久观察。

## Out of Scope

- 进程被杀后的 WorkManager/前台服务续跑、系统级通知和跨设备任务同步。
