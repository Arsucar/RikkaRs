# 修复备份卡住与数据库一致性

## Goal

让 WebDAV、S3 和本地导入导出在空数据与大数据场景下都能可靠结束，用户可以看见当前阶段并主动取消；同时消除直接复制活动 Room 数据库造成的不一致备份，并修复现存数据库缺少 `compress_hidden_count` 时无法启动的问题。

## Confirmed Facts

- 所有应用内备份入口共用 `BackupTaskCoordinator`，当前会在任务实际启动前写入 `Running`，被取消的 scope 或未执行的 coroutine body 可留下永久转圈状态。
- 备份 UI 仅区分 `Running`，没有准备、传输、写入阶段，也没有接入已有的取消 API。
- WebDAV、S3 与本地导出都先生成 ZIP；当前 ZIP 使用默认压缩级别、8 KiB 默认复制缓冲，并使用秒级文件名，跨操作并发时可能冲突。
- 当前数据库使用 WAL，备份会分别复制活动中的 main/WAL/SHM 文件，没有原子快照边界。
- 项目通过 Requery SQLite 3.50.x 运行 Room，支持 `VACUUM INTO`；应用 `minSdk` 为 26。
- 当前连接设备上的 v2.3.37 数据库因 `message_node.compress_hidden_count` 缺失而无法通过 Room schema 校验；现有 34->35 迁移对正常升级路径是正确的，故需要后续防御迁移修复不一致数据库。

## Requirements

- 任务启动失败、取消、超时、普通异常及非 `Exception` 的失败都必须离开 `Running`，不得永久转圈。
- 运行状态至少区分准备归档、上传/下载、写入目标和恢复数据等用户可理解的阶段。
- WebDAV、S3、本地导入和本地导出均提供可达的取消操作；取消后清理临时文件并显示明确终态。
- ZIP 临时文件必须全局唯一；阻塞复制使用更大的显式缓冲并在分块之间响应取消。
- ZIP 采用偏向速度的压缩设置，不能更改现有 entry 名称或破坏旧备份恢复兼容性。
- 新备份中的数据库必须来自单文件一致快照，不再分别打包活动 main/WAL/SHM。
- 恢复仍接受旧备份中的 main/WAL/SHM；新备份缺少 sidecar 时不得遗留目标设备旧 WAL/SHM。
- 数据库 44->45 迁移必须幂等检查 `compress_hidden_count`，仅在缺失时添加 nullable INTEGER 列，正常数据库不得被破坏。
- 设置、数据库、上传文件、skills 和 fonts 的既有选择语义保持不变；workspace 仍不属于应用内 ZIP。

## Acceptance Criteria

- [ ] 在已取消的任务 scope 上启动操作会被拒绝或立即进入非 Running 终态。
- [ ] 空数据本地导出能生成可打开 ZIP，并在有限时间内退出 Running。
- [ ] WebDAV/S3/本地导入导出均显示当前阶段，运行中的操作可取消。
- [ ] 取消与异常路径会删除对应临时 ZIP/导入文件，且不会重复弹成功提示。
- [ ] 同一秒并发启动不同备份操作时使用不同临时文件，不会互删或共写。
- [ ] 新 ZIP 中数据库 entry 只有 `rikka_hub.db`，快照可独立打开且通过完整性与外键检查。
- [ ] 旧格式 main/WAL/SHM ZIP 仍可进入现有恢复流程；新格式恢复不会复用旧 sidecar。
- [ ] 从 version 44 且缺列的数据库升级到 45 后 Room schema 校验通过；已有列的 44 数据库也可升级。
- [ ] 协调器、归档/快照和迁移具有聚焦回归测试；资源处理、Kotlin 编译、JVM 测试与 AndroidTest 源码编译通过。
- [ ] Debug 包安装到连接设备；手工核验空/正常数据、取消、失败和重进页面状态。

## Out of Scope

- 进程被系统杀死后继续备份；本轮不引入 WorkManager 或前台服务。
- 精确到字节的百分比进度；本轮提供可靠阶段状态。
- 更改备份加密、密钥存储或 workspace 的应用内备份范围。
