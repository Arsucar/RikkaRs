# 修复 #187 预设审查回归

关联 GitHub issue #187，承接 #182 与 Trellis 任务
`07-27-issue-182-preset-fixes` 的最终契约。

## Goal

修复 #182 当前实现审查确认的三个回归：全局注入经 Reference 与助手直连时重复注入、Workspace
编辑器默认模板与运行时默认不一致、拖拽排序无法由 TalkBack/键盘操作且语义错误。

## Confirmed Facts

- 直连注入以全局 `ModeInjection.id` 记录去重身份；Reference 解析结果当前改用 `PresetEntry.id`，
  因而同一全局目标可进入结果两次。
- Workspace 注册表展示精简默认，而无覆盖运行时使用完整 `buildWorkspacePrompt`。用户基于展示默认做任意编辑后，
  保存的 override 会替代完整运行时默认并丢失工具、skills、权限和 `/upload` 只读约束。
- 拖拽手柄当前是空 `onClick` 的 `IconButton`，却固定朗读为“上移”；没有可访问的向下动作。
- 当前交互只允许同一 `PresetEntry` 子类型内排序；该边界不得改变。

## Requirements

1. Reference 解析必须携带其目标全局注入的稳定源身份。直连、旧预设和 Reference 指向同一全局注入时，
   内容至多注入一次；两个不同 Custom 条目不得因内容相同而合并。
2. Workspace 完整默认提示必须只有一个权威生成入口。注册表的可编辑默认与运行时无覆盖默认必须保持
   相同的工具、skills、权限和上传文件安全约束；宏仍在 Workspace transformer 边界解析。
3. 保留长按触摸拖拽，并提供可由 TalkBack 和键盘到达的同组“上移/下移”命令。首项禁用上移、末项禁用下移，
   不允许跨子类型移动。
4. 拖拽手柄使用准确、已本地化的描述，不得暴露无效果的按钮动作。
5. 将“拖拽排序必须有非手势等价操作”的复用规则写入 UI Modification Thinking Guide。
6. 动态宏只解析原始模板占位符一次；workspace 名称、cwd 等运行时值中的宏形文本必须保持字面内容。

## Acceptance Criteria

- [x] AC1：助手直连 X 且预设含 `Reference(modeInjectionId=X)` 时，X 的内容只出现一次；回归测试锁定。
- [x] AC2：不同 Custom 条目仍按各自 entry id 独立注入；Reference 目标缺失/禁用仍安全跳过。
- [x] AC3：Workspace 注册表默认解析后与运行时完整默认等价，包含工具逐项说明、skills/权限与 `/upload`
  只读约束；基于默认文本编辑不会隐式降级。
- [x] AC4：TalkBack/键盘可对同组非边界条目执行上移和下移；边界动作禁用；跨组数据不变。
- [x] AC5：触摸长按拖拽继续可用，手柄描述准确，英文和简体中文资源齐全。
- [x] AC6：聚焦单测、Debug 资源处理、生产 Kotlin、完整 app JVM 单测与 AndroidTest Kotlin 编译通过。
- [x] AC7：有设备时完成 `installDebug` 与拖拽/TalkBack 人工验收；无设备时按仓库流程提供最终 APK 证据。
- [x] AC8：运行时变量值中的 `{{...}}` 不被二次解析；未知宏保留、已提供空值清空的既有契约不变。

## Verification Record (2026-07-28)

- Focused tests: PromptInjectionTransformerTest, BuiltinPromptRegistryTest, PresetEntryUiTest passed.
- Final combined Gradle: Debug resources, production Kotlin, 881 app JVM tests, and AndroidTest Kotlin compilation
  passed; 0 failures, 0 errors, 0 skipped.
- `git diff --check` passed. Full lint was not run under the repository's ordinary app-change policy.
- Device: `adb devices` was empty; the single allowed connect to `100.99.129.110:5555` timed out with 10060.
  No installation, visual, gesture, or live TalkBack claim is made.
- APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, SHA-256
  `985D817434AF92F153AF92B366886DA6A740926BAB8AAB0F3580CA4C8BB39B09`, GoFile `https://gofile.io/d/dUFhTD`.

## UI Verification Matrix

- Content：1 个、多个、长标题、重复名称；英文与简体中文。
- State：启用/禁用条目，Builtin/Custom 的首项/中间项/末项。
- Form factor：窄屏、横屏、滚动列表；移动命令不得挤压主行，放入现有溢出菜单。
- Accessibility：TalkBack 朗读准确；拖拽手柄无空点击；上移/下移可聚焦、边界禁用；大字体可用。
- Interaction：触摸长按拖拽、菜单上移/下移、编辑、删除确认、返回/取消。

## Out Of Scope

- 改变 PresetEntry 数据模型、迁移版本或多预设冲突策略。
- 跨类型拖拽、全量六语言本地化、重构其他内置提示词。
- 改变 `07-27` 已定调的 Reference 详情页隐藏边界；Reference 数据与旧入口继续保留。
