# issue-218: 预设切换开关掉帧修复

## Goal

修复聊天页扩展选择器 / 助手扩展页切换预设（Presets Switch）时的延迟与掉帧：根因是无乐观状态 + 读快照后全量 `writeFullSettings` 落盘，开关翻转等磁盘往返。对齐 #202 已落地的 transform 部分写模式。

对应 GitHub issue: #218。

## Requirements

### R1 即时反馈
- Switch 点击后 UI **立即**翻转（本地乐观状态，或 VM 立即更新 StateFlow），不等磁盘往返。

### R2 原子部分写
- 禁止「读 setting 快照 → `vm.updateSettings(全量)` → `writeFullSettings`」路径做预设开关。
- 新增/复用 PreferencesStore transform 方法（仿 `updatePreset` / `updateAssistantWebSearch`）：仅改目标 assistant 的 `presetIds`（或对应扩展 id 集合），DataStore 原子写。
- 快速连点不丢更新（transform 串行 + mutex，对齐 #202）。

### R3 覆盖路径
- **主路径**：聊天页扩展选择器（`ExtensionContent` Presets Switch → `ChatPage` → `ChatVM`）。
- **次路径**：助手扩展页（`AssistantExtensionsPage` → `AssistantDetailVM`）：若仍无乐观状态，一并修；已部分写则补乐观状态即可。
- **排除**：`PresetDetailPage`（#202 已合规）。

### R4 重组热点（可选评估）
- `ChatPage` 顶层 collect 导致全屏重组：若部分写后延迟已可接受，可不改；若仍掉帧，再评估细粒度 collect（非本 issue 必须）。

## Constraints

- 遵守 #202：禁止读快照-全量覆盖写。
- 不改 Settings schema；不改预设数据模型。
- 乐观状态失败回滚：transform 失败时 UI 回退到 store 真值并提示（Toast/Snackbar 可选，至少 log）。

## Acceptance Criteria

- [x] AC1：配置较多（多预设/世界书/快捷消息）时，Presets Switch 点击即时翻转，无可见「等磁盘」延迟。（代码：edit 前乐观 settingsFlow；真机点按待设备）
- [x] AC2：落盘走 PreferencesStore 部分写；不经过 `writeFullSettings` 全量路径。
- [x] AC3：快速连点最终状态与最后一次操作一致，无后写覆盖先写丢更新。（updateMutex 串行）
- [x] AC4：助手扩展页同类开关同样即时（若本 issue 纳入次路径）。
- [x] AC5：写失败时 UI 不永久卡在错误乐观态（回读 store 或显式回滚）。
- [~] AC6：聚焦验证：`AssistantConfigPersistenceTest` 通过；assembleDebug 成功；设备 `100.99.129.110:5555` offline，未能 installDebug，已提供 APK 下载链。

## Out of Scope

- 不重构整个 Settings 写入体系。
- 不强制拆 ChatPage 顶层 collect（除非 AC1 仍不达标）。
- 不改 ModeInjections / Lorebooks 开关（可同模式，但非本 issue 必须；若同文件顺手且风险低可一并，否则单独 issue）。

## Notes

- 现状核实：
  - `ExtensionContent.kt:81-84` Switch：`checked = selectedIds.contains`，`onCheckedChange → onToggle`，无本地 optimistic。
  - `PreferencesStore.update` / `updateUnlocked` 仍 `writeFullSettings`；但已有 `updatePreset`(L696)、`updateAssistantConfig`(L682)、`updateAssistantWebSearch`(L776) 等部分写可作模板。
  - `ChatVM.updateSettings`(L371) 仍是全量入口；ChatPage L698/711/1001 调用之。
