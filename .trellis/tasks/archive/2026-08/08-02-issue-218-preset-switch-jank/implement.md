# issue-218 implement

## 有序 Checklist

1. [x] 读 `PreferencesStore.updatePreset` / `updateAssistantWebSearch` / `updateAssistantConfig` 作模板。
2. [x] 新增 `toggleAssistantPreset`（或 `updateAssistantPresetIds`）partial 写。
3. [x] `ChatVM` 暴露对应方法；删除/ bypass ChatPage 全量 `updateSettings` 预设路径（约 L698/711/1001）。主路径改在 ExtensionSelector 直接调 store。
4. [x] 确认 `ExtensionContent` 仍由 `selectedIds` 派生即可，或加本地 optimistic（若 flow 更新仍慢）。
5. [x] 次路径：`AssistantExtensionsPage` + `AssistantDetailVM` 同步 partial + 乐观。
6. [x] （可选）ModeInjection/Lorebook 同模式。→ 未做，留 follow-up。
7. [x] 验证 + 装设备。→ 单测绿；设备 offline，assembleDebug + GoFile 备链。

## 验证命令

```powershell
# 若有 store 单测
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*PreferencesStore*"

# 最终
adb devices
# 无 device 则 adb connect 100.99.129.110:5555
.\gradlew --no-daemon :app:installDebug
```

手动：聊天页扩展选择器连点 Presets Switch；助手扩展页同测；杀进程后状态持久。

## Review 门

- [x] 无预设路径再走 `writeFullSettings` 全量
- [x] mutex 保护
- [x] 失败回滚
- [~] 真机无可见延迟（设备 offline；代码乐观 flow；APK 已打）

## 回滚点

- 还原 PreferencesStore 新方法 + ChatVM/ChatPage/Assistant* 调用。

## 工作量

**M**（1 天）：主路径半日，次路径 + 回归半日。

## 风险

- settingsFlow 订阅面仍大导致重组掉帧：记录为 follow-up，不阻塞 AC1 若翻转已即时。
