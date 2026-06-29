# PRD: Backport batch 2 — 回流 + UI/数据保存系统优化

## 背景（更新版）

本轮目标从"纯上游回流"升级为"回流 + 合并后系统优化"。调研确认 fork 合并 subagent 全局化（`6dca48a1`）后存在三类系统性问题，加上原本待回流的功能项，统一规划。

### 已确认 fork 已有（不纳入，避免重复）

- ✅ 最近聊天改对话工具、上下文截断警告、ask_user 回退、ElevenLabs/Step/Serper、ScreenTime 工具本体、Subagent 审计字段（`06-29-subagent-audit-port` 已完成）。

## 问题清单（调研已定位根因）

### A. 默认子代理丢失（P0，用户直接感知）
- **现象**：用户侧只剩 explore，coder/reviewer 消失。
- **根因**：`6dca48a1` 全局化后，默认集合 = `Settings.globalSubagentProfiles` 持久化内容；`effectiveGlobalProfiles`（`SubagentRegistry.kt:67`）**只在 global 整表为空时** fallback `BUILTIN_PROFILES`。`deleteGlobalSubagent`（`SettingVM.kt:28`）物理移除且**无内置保护**。一旦 global 里只留 explore，运行时和 UI 都只剩 explore。
- **临时恢复**：扩展 → 子代理 → 溢出菜单「恢复默认子代理」（`restoreDefaultSubagents`）。
- **根治**：(1) `effectiveGlobalProfiles` 改为 union 缺失 BUILTIN（而非仅 empty fallback）；(2) 内置名禁止 `deleteGlobalSubagent` 或改为"重置为内置默认"。

### B. 权限与开关不同步（P1，数据保存脆弱）
- **现象**：ScreenTime 开关可开但权限可无（`AssistantLocalToolPage.kt:84-93` 不 `return` 阻止保存），持久化的 `localTools` 与真实可用性不一致。
- **对照**：upstream 日历开关未授权就 `return` 不保存（行为正确）；ScreenTime 行为割裂。
- **根治**：统一开关行为——未授权时引导授权但不保存开关状态，或授权成功后才写入。

### C. UI 无统一错误/空态（P1，合并后体验问题）
- **现象**：ScreenTime 只处理 `NO_PERMISSION`，`INVALID_TIME/RANGE` 和空数据走默认 JSON Preview；日志/时间等工具也无统一错误卡片。
- **根治**：新建统一 error/permission 卡片组件，local 工具族复用。

### D. 枚举四处重复易漏（P2，维护负担）
- **现象**：`LocalToolOption` / `LocalTools.getTools` / `SubagentTools.toLocalToolOption` / `AssistantSubagentProfilePage`（后者还缺 `AskUser`）四处手写枚举，新增工具极易漏一处。
- **根治**：`LocalToolOption` 作为单一 source of truth，配置页自动派生；顺手补齐子代理页缺的 AskUser。

### E. 子代理配置 UI 风格不统一（P2，学习 sub）
- **sub 做法**：`AssistantSubagentProfilePage` 多 Card 分区（基本信息/Prompt/模型参数/行为/工具/流式记忆），统一 `Card + FormItem + HorizontalDivider`；`AssistantSubagentPage` 顶部设置 Card 内联。
- **fork 现状**：单 Card 堆叠 `SubagentProfileForm`；`AssistantSubagentHubSection` 独立文件。
- **学习范围**：只学 UI 布局范式（多 Card 分区 + 统一 spacing），**保留** fork 的 workspace 权限模型 / global profiles / Extensions 页（sub 砍了这些，我们不砍）。

## 待回流功能项（原有）

- **F. ScreenTime 统计准确性**（upstream `5b46c8de`+`40b613eb`）：事件配对计算 + 排除桌面 launcher。
- **G. 日历查询/创建工具**（upstream `d677707d`）：完全缺失，采用上游 `local/CalendarTool.kt` 结构。
- **H. HttpSearchService 基类重构**（sub 三笔 + 5 配套修复）：19 service 样板抽取 + ENTRIES 注册表统一。

## 任务树（重新组织）

原 3-child → 6-child。子代理相关拆分为两个独立 child（导航+新功能 vs UI 风格统一），避免单 child 过载。

| Child | 承载 | 复杂度 |
|---|---|---|
| `06-29-subagent-default-restore` | A（默认子代理丢失根治） | 轻量 |
| `06-29-screentime-accuracy` | F（统计修复）+ C 的 ScreenTime 部分（UI 错误/空态） | 中 |
| `06-29-calendar-tools` | G（日历新增）+ D 的日历部分（枚举去重落地） | 中 |
| `06-29-subagent-nav-and-features` | 导航重组 + delegate-only + parallel-execution + extraLocalTools | 复杂（数据层+运行时+UI） |
| `06-29-local-tool-ui-consolidation` | B（权限同步）+ C 通用（错误卡片）+ D（枚举去重）+ Profile Card 风格 | 复杂（UI 重构） |
| `06-29-search-httpservice-base` | H（搜索重构） | 复杂 |

## 跨 child 验收标准（parent 层）

- [ ] 默认子代理 explore/coder/reviewer 在任何情况下都可用（删除后自动恢复或禁删）。
- [ ] local 工具新增只需改 `LocalToolOption` + `LocalTools` 两处，配置页自动派生。
- [ ] 所有 local 工具的权限/错误/空态走统一 UI 组件。
- [ ] ScreenTime 统计准确（事件配对 + 排除 launcher）。
- [ ] 日历工具可用（查询 + 创建经确认）。
- [ ] 子代理配置页多 Card 分区，风格统一。
- [ ] HttpSearchService 重构完成，新 provider 只加一行 ENTRIES。
- [ ] 全部完成后 `.\gradlew :app:installDebug --no-daemon` 成功，真机无回归。

## 集成顺序建议

1. **`06-29-subagent-default-restore`**（P0，最小最快，先止血）
2. **`06-29-screentime-accuracy`**（F + 部分 C，独立）
3. **`06-29-calendar-tools`**（G + 部分 D，正交）
4. **`06-29-subagent-nav-and-features`**（导航重组 + 3 新功能；建议在 1 之后，都改 subagent 体系）
5. **`06-29-local-tool-ui-consolidation`**（B + C 通用 + D 完整 + Profile Card；依赖前面工具就位，且与 4 都改 AssistantSubagentPage 建议串行）
6. **`06-29-search-httpservice-base`**（H，最大最后，与 UI 无关）

child 间无硬依赖（除 5 建议在 2/3/4 后），可并行规划。4 和 5 都触及 `AssistantSubagentPage`/`AssistantSubagentProfilePage`，实现期建议串行避免合并冲突。

## Out of Scope

- 不照搬 sub 的数据模型（`SubagentProfile.BUILTIN` / `disabledBuiltinSubagents`）—— 我们保留 `SubagentRegistry` + global profiles 架构。
- 不砍 fork 的 workspace 权限 UI / Extensions 全局页 —— sub 砍了，我们不砍。
- 不引入 sub 的硬编码中文对话框 —— 保持 i18n。
