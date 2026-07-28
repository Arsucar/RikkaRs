# 修复 #186 WebDAV/S3 Tab 用 rememberCoroutineScope 驱动测试连接/删除远端备份

## Goal

WebDavTab.kt:90/253/354 与 S3Tab.kt:91/271/373 仍用 rememberCoroutineScope().launch 驱动测试连接与删除远端备份文件的网络 IO，挂在 UI 组合作用域，离页/旋屏即取消，无结果反馈（#143 同类残留）。修复：迁入 viewModelScope 或 BackupTaskCoordinator 轻量操作；VM 不再向 UI 暴露 suspend fun，改普通函数+内部 launch；UI 订阅 StateFlow 回显进度与结果。

## Requirements

- `WebDavTab.kt` 与 `S3Tab.kt` 中测试连接（`testWebDav`/`testS3`）与删除远端备份文件（`deleteWebDavBackupFile`/`deleteS3BackupFile`）不再由 `rememberCoroutineScope().launch` 驱动网络 IO。
- 相关操作迁入 `BackupVM` 的 `viewModelScope`（或纳入 BackupTaskCoordinator 轻量操作类别），跨页面导航/旋屏存活。
- `BackupVM` 不再向 UI 暴露这些 `suspend fun`，改为普通函数 + 内部 `launch`；UI 通过 StateFlow/回调订阅进度与结果。
- 结果（成功/失败/进行中）在 UI 有明确回显，替代原先离页即静默中断。
- 遵循 #143 已建立的 Coordinator/`viewModelScope` 边界，不破坏既有主备份/恢复链路。

## Acceptance Criteria

- [ ] 备份设置页无 `rememberCoroutineScope` 驱动网络 IO（grep 验证）。
- [ ] 在途测试连接/删除远端文件时导航离页不被取消，重进页面状态一致。
- [ ] `BackupVM` 对 UI 不再暴露相关 `suspend fun`。
- [ ] `:app:compileDebugKotlin` 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
