# 实施计划

## 1. Task Lifecycle

- [ ] 扩展 `BackupTaskState.Running` 的阶段模型和 Coordinator 更新 API。
- [ ] 使用可验证 scope + lazy IO job 修复启动竞态，并覆盖 Throwable、取消和 watchdog。
- [ ] 在 `BackupVM` 暴露取消操作并在各调用边界上报阶段。
- [ ] 扩展 `BackupTaskCoordinatorTest`：已取消 scope、阶段、超时、Throwable、取消不转 Success。

## 2. Consistent Archive

- [ ] 抽取共享归档生成器和可取消 64 KiB copy helper。
- [ ] 使用唯一临时文件、`BEST_SPEED`、安全 skills 遍历和失败清理。
- [ ] 用 `VACUUM INTO` 生成并校验单文件数据库快照。
- [ ] WebDAV/S3/local export 接入共享归档；保留旧 ZIP entry 合同。
- [ ] 恢复新 ZIP 时清理旧 WAL/SHM，旧格式 sidecar 继续兼容。
- [ ] 添加归档 entry、唯一文件、取消清理和数据库快照测试。

## 3. Database Repair

- [ ] 增加并注册 `Migration_44_45`，将数据库版本升至 45。
- [ ] 测试缺列与已有列两种 version 44 数据库升级路径。

## 4. Compose Feedback

- [ ] WebDAV/S3 按钮显示阶段并支持取消。
- [ ] 本地导入导出卡片显示阶段并提供独立取消 action。
- [ ] 恢复/导入成功只触发一次重启提示；失败和取消具有单一明确反馈。
- [ ] 补充基础字符串并检查窄屏、大字体及 TalkBack 文案。

## 5. Verification

- [ ] 运行聚焦 JVM/Android 测试。
- [ ] 合并运行资源处理、Kotlin 编译、完整 JVM 测试和 AndroidTest 源码编译。
- [ ] 运行 `git diff --check` 和 Trellis check。
- [ ] `adb devices` 后执行 `./gradlew --no-daemon :app:installDebug`。
- [ ] 真机核验空/正常数据备份、取消、失败、切页续跑和数据库修复启动。

## Risk / Rollback Points

- SQLite SNAPSHOT 依赖为浮动版本；仪器测试必须实际执行 `VACUUM INTO`。
- 归档格式兼容以 entry 名和恢复测试为门；新包不得写 WAL/SHM。
- 所有 Gradle 命令使用 `--no-daemon`，多项检查合并执行，避免重复占用内存。
