# 扩展页 Subagent Profiles 层级并入扩展分组

## Goal

把 `ExtensionsPage.kt` 中独立占一级标题的 "Subagent Profiles" 分组并入下方"扩展"CardGroup，作为其中一项（或紧邻的子分组），消除独立顶层标题。

## Background

当前 `ExtensionsPage.kt` L68-109 把 Subagent Profiles 做成 `item { Row { Text(title) + IconButton(add) } + CardGroup { ... } }`，与 L111-141 的"扩展"CardGroup 平级。用户期望 Subagent Profiles 归入"扩展"标题之下，不再独占一级标题。

证据：
- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionsPage.kt:68-109`（Subagent Profiles 独立顶层）
- 同文件 `:111-141`（扩展 CardGroup）
- `strings.xml` 存在未引用的残留键 `extensions_page_subagent_profiles` / `_desc`（Kotlin 中只用 `extensions_page_section_subagent_profiles`）

## Requirements

- R-1：Subagent Profiles 不再使用独立 `titleSmallEmphasized` + `Row` 顶层标题。
- R-2：Subagent Profiles 列表归入"扩展"CardGroup 之下，作为该 CardGroup 的第一组 item（或在 CardGroup 内用一个子标题分隔），保留"+ 添加"入口。
- R-3：保留现有跳转：点击 profile → `Screen.ExtensionSubagentProfile(name, false)`；点击 + → `Screen.ExtensionSubagentProfile(name, true)`。
- R-4：空列表时仍展示 `extensions_page_no_subagent_profiles` 占位项。
- R-5：`CardGroup` 组件 API 若不支持"单 CardGroup 多分组 + 分组标题"，则允许 Subagent Profiles 作为紧邻"扩展"CardGroup 上方的一个无大标题 CardGroup（即仅去掉 L74-78 的 `Text(titleSmallEmphasized)` 那一层，保留 + 按钮在 CardGroup 之上或之内）。

## Acceptance Criteria

- AC-1：进入扩展管理页后，Subagent Profiles 不再以 `titleSmallEmphasized` + primary 色 Title 形式独占一级分组；视觉上归属于"扩展"区域。
- AC-2："+ 添加 subagent"入口仍可用，跳转目标不变。
- AC-3：点击已有 profile 跳转编辑页，行为不变。
- AC-4：空 profile 列表仍显示占位文案。
- AC-5：`.\gradlew :app:compileDebugKotlin` 通过。
- AC-6：清理 `strings.xml` 中未引用的 `extensions_page_subagent_profiles` / `extensions_page_subagent_profiles_desc`（若确认全仓无引用）。

## Out of Scope

- "扩展"CardGroup 内其他项（快捷消息/提示词/Skills/工作区）的顺序调整。
- i18n 文案翻译——本任务仅做层级调整，文案键复用现有。
- 子代理数据模型变更（见 `06-27-builtin-subagent-global`）。

## Technical Notes

- `CardGroup` 的 API：若 `CardGroup(title = { Text(...) })` 支持分组标题，可让 Subagent Profiles 作为该 CardGroup 的一个子段；若不支持，保留两个 CardGroup 但去掉 Subagent Profiles 那个的大标题 Row，使其视觉上从属。
- 实现前需读 `CardGroup.kt` 确认 API 形态。
