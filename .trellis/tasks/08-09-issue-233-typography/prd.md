# PRD: feat(#233) Typography 品牌字体层级

## Goal

为全局 `MaterialTheme.typography` 建立清晰 display/title/body/label 层级（含 Emphasized 变体），改善标题/正文对比与品牌感；**默认采用方案 B（系统字体 + 字重/行高/字距）**，除非仓库已捆绑 Google Sans Flex 资源。

## Background

`app/.../ui/theme/Type.kt` 中 `val Typography = Typography()` 近乎空定制；Google Sans Flex 段被注释。全应用依赖 M3 默认，层级弱。

权威：issue #233。

**方案选择（本任务定调）**：

| 方案 | 说明 | 本任务 |
|---|---|---|
| A | 启用注释中的 Google Sans Flex | **仅当字体资源已捆绑** |
| **B** | **系统字体 + weight / lineHeight / letterSpacing** | **默认采用**（零 APK 字体体积） |
| C | 新品牌字体 | 否决（体积高） |

实现前用仓库检索确认是否已有 Google Sans Flex / 相关 font family 资源；**未捆绑则禁止为 A 新增大体量 font asset**。

本任务 **lightweight：仅 PRD**。

## Requirements

1. 在 `Type.kt` 定义完整 `Typography`：display / headline / title / body / label 各档 + 必要 Emphasized 变体（可用 M3 扩展或项目既有 emphasized API）
2. 经 `RikkahubTheme` 注入，页面继续消费 `MaterialTheme.typography.*`
3. 与 7 预设主题 + Material You 色彩兼容；Dark / AMOLED 对比度可接受
4. 与 `ChatFont.kt` 聊天字体切换共存：聊天正文仍可走 ChatFont，不强制全局覆盖聊天用户选字
5. `fontScale` 大字体无障碍下层级仍可区分
6. 不新增散落裸 `fontSize` 作为主题替代；主题层集中定义

## Non-goals

- 未捆绑时引入 Google Sans Flex 字体文件
- 重做全部页面逐 Text 视觉精修（本任务主题层）
- 修改 ChatFont 产品逻辑（仅保证不冲突）

## Acceptance Criteria

- [ ] AC1: `Type.kt` 不再是空 `Typography()`；各主要 type scale 有显式 weight/lineHeight（及可选 letterSpacing）
- [ ] AC2: 未捆绑 Google Sans Flex 时采用系统字体路径（方案 B）；已捆绑才允许 A
- [ ] AC3: 全局主题注入后标题/正文层级对比可感知（截图前后）
- [ ] AC4: 大 `fontScale` 下无严重截断/重叠（抽检设置页 + 聊天）
- [ ] AC5: ChatFont 切换仍生效，与主题 typography 无回归
- [ ] AC6: 抽检无「为赶层级而新增长期裸 fontSize 绕过 typography」的主题层反模式

## 实现提示

| 项 | 路径 |
|---|---|
| Typography 定义 | `app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt` |
| 主题注入 | `RikkahubTheme`（同 theme 包） |
| 聊天字体 | `ChatFont.kt`（只读共存） |
| 字体资源探测 | `res/font/**`、gradle 依赖、Type.kt 注释块 |

## Notes

- PRD-only；实现时先 `Glob`/`Grep` 字体资源再选 A/B
- 优先改 Type.kt 一处，避免大面积 UI 文件改动
