# PRD: feat(#247) workspace 挂载点真实内容浏览

## Goal

Workspace 详情 Rootfs（LINUX）浏览器进入 bind-mount 目标目录时，展示 Android 宿主挂载源真实文件；支持查看/导出/分享；LINUX 只读策略不变。

## Background

Rootfs 下 `skills/`、`upload/`、`tool_outputs/`、`workspace/`、`skills_private/` 在宿主 `linux/` 树中是空占位；真实数据在 `filesDir/...`。工具侧已有 `resolveRootfsPath`，浏览器 `listFiles` 未重定向。

权威来源：GitHub issue #247 正文；实现 inventory：`research/implementation-plan-issue-247.md`。

## Requirements

### 功能

1. Rootfs `/` 可见挂载条目；进入后列出挂载源真实内容。
2. `/skills_private` 按「入口助手」解析私有技能目录：
   - 绑定 workspace 的助手恰好 1 个 → 用其 id
   - 0 个 → 回退当前助手，UI 标注「当前助手」
   - 多个 → 必须提供选择，禁止静默挑一个
3. 挂载路径 `readText` / `exportFile`（及 size）重定向到挂载源。
4. LINUX 区仍禁止编辑/删除/导入。
5. `/dev` `/proc` `/sys` 行为不变。
6. 挂载源缺失/空 → 空目录态，不崩溃。

### 约束

- 无新数据模型字段（`WorkspaceFileEntry` 不变）。
- 不新增写映射。
- 文案 CN+EN（`strings.xml`）。
- 助手绑定变更后重新进入按最新绑定解析（不做过期缓存快照）。

## Non-goals

- 终端 session 挂载表改造
- 把挂载源复制进 FILES 区
- 用 PRoot `ls` 驱动浏览器

## Acceptance Criteria

- [ ] AC1: Rootfs `/` 下可见 `skills/`、`upload/`、`tool_outputs/`、`workspace/`、`skills_private/`；进入显示对应挂载源真实文件
- [ ] AC2: `/skills_private` 按入口助手：1 绑直接显示；0 回退当前助手并标注；多绑提供选择
- [ ] AC3: 挂载点内文本 `readText` 正常
- [ ] AC4: 挂载点内文件 `exportFile`/分享正常
- [ ] AC5: LINUX 只读：编辑保存/删除/导入仍禁用
- [ ] AC6: `/dev`、`/proc`、`/sys` 行为不变
- [ ] AC7: 挂载源空/不存在 → 空目录，不崩溃
- [ ] AC8: 单测：`listFiles` 挂载重定向（`RootfsPathResolutionTest` 风格）+ `skills_private` 助手解析

## 挂载源表（产品）

| Rootfs target | Host source |
|---|---|
| `/skills` | `filesDir/skills` |
| `/tool_outputs` | `filesDir/tool_outputs` |
| `/upload` | `filesDir/upload` |
| `/workspace` | 该 workspace FILES 区 |
| `/skills_private` | `filesDir/assistant_skills/<assistantId>` |
