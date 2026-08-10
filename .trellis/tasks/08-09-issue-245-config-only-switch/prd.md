# PRD: bug(#245) 预设内置 config-only 条目假开关

## 问题

预设中 config-only 内置条目（reply_draft / suggestion / memory_table_guide / workspace_guide）在通用注入路径不产生注入（`injectable=false`），但：

1. `displayEntryCount` / `displayEntryNames` 把它们计入「启用条目」，强化「已注入」错觉
2. Switch 旁虽有 config-only Tag，用户仍易理解为「注入开关」

## 研究修正（相对 issue 建议 a）

`enabled` **确实**门控专用功能 override（`resolveBuiltinOverride` / draft-context）。禁用 Switch 会去掉唯一 UI 去关闭 override。

**最终定调：**

| 项 | 决定 |
|----|------|
| 启用计数 / 名称列表 | **排除** config-only Builtin（只统计会进通用注入的条目） |
| Switch | **保留**，继续门控专用功能 override；已有 config-only Tag + 编辑页说明 |
| 注入链路 | 不改（已正确跳过） |

## 验收条件

- [ ] `displayEntryCount` / `displayEntryNames` 不含 config-only Builtin
- [ ] Custom / Reference（及未来 injectable Builtin）计数行为正确
- [ ] config-only 卡片仍显示 config-only Tag；Switch 仍可切换 `enabled`（override）
- [ ] `PresetEntryUiTest` 更新并通过；注入相关既有单测不回归

## 范围

- `PresetEntryUi.kt` + `PresetEntryUiTest.kt`
- 可选：共享 `isConfigOnlyBuiltin()` 供详情页复用
- 不改 `PromptInjectionTransformer` / Registry
