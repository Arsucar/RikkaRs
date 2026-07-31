# Merge upstream 2.4.2–2.4.5+ (#197)

## Goal

将上游 `rikkahub/rikkahub` 的 `master` tip **`8349ef25`**（2.4.2–2.4.5+）同步到 fork 分支 `release/rikka-arsucar`，吸收 bugfix 与有价值能力，并**固定保留** fork 身份与 CI（`docs/RIKKA_ARSUCAR_FORK_AND_CI.md`）。

Issue #197 正文基线（behind=45 / tip=`16be94e8` / fork 2.3.40）已过时；以 **2026-07-31 fetch** 与本 PRD 为准。

## Background

| 项 | 值 |
|----|-----|
| 合并基底（本地 HEAD） | `5a6b8fd1`；含 #191–#196；**origin 超前 10**；`versionName=2.3.41` / `versionCode=203` |
| origin/release/rikka-arsucar | `6ffb6bf20`（2.3.40 / 202）— 勿用其作 version 下限 |
| Upstream tip（D1） | `8349ef25` — 2.4.5 + i18n「无限制」 |
| Upstream 发版 | tags 2.4.2 / 2.4.3 / 2.4.5；tip 声明 `2.4.5` / `172` / `me.rerere.rikkahub` |
| merge-base | `cf55cbbb5` |
| behind（origin..upstream） | **56**（#197 为 45；+11） |
| 上次整并 | #68 → `42b2101d` Merge upstream v2.4.1 |
| 相对 #197 新增要点 | highlight `#1614` 落地、legacy storage、TTS 默认倍速、Skills key、workspace HEIC/AVIF、Moonshot temperature、deps、bump 2.4.5、上下文 i18n |

**相对 #197 额外处理：** 窗口 45→56 至 2.4.5；highlight 由 wip 暂缓改为 **D2 整模块升级**（fork 已有 `highlight/`）；基底用本地 2.3.41/203；上游仍含 Firebase（Remote Config 删除 ≠ 无 Firebase）；LICENSE 上游纯 AGPL vs fork 分段双许可 → **D3 ours**。

### Fork 不变量

- `applicationId=me.arsucar.rikka`（Kotlin 包名可仍 `me.rerere.rikkahub`）
- 无 Firebase / 无 `google-services`
- 正式发版仅 `release-apk.yml`（可与上游其它 workflow 共存，#68）
- version 走 fork 线，合并后 **≥ 2.3.41 / 203**（禁止上游 172 / 2.4.5）
- 压缩上下文 fork 定制以 **#59** 为准（与上游阶梯截断合成，见 design）
- 不向 `rikkahub/rikkahub` 提 PR

### 吸收主题（56 提交，非穷尽）

助手级搜索、Kimi K3、阶梯截断与占位符、MCP 拆分、Workspace 预览/SAF/bind mount/多图格式、备份与 WebDAV 解耦、主题网格、mermaid 内置、AI 方言/Grok/Moonshot、TTS、highlight 原生高亮、AGP/Kotlin/deps、上下文 i18n。

## Requirements

- **R1** 合并 D1 tip（或等价完整吸收），目标 behind=0；暂缓项须文档化并改 AC1。
- **R2** 合并后全部 Fork 不变量成立。
- **R3** 冲突按 design 热区合成（身份/Firebase/CI/**#59+阶梯**/Chat/MCP/Backup 等 fork 优先；highlight D2 theirs 能力）。
- **R4** 可核对上游关键 fix（助手归属、删会话再跳转、TTS、搜索、Grok、Moonshot temperature、workspace 图格式等）。
- **R5** CHANGELOG 写明上游同步（2.4.2–2.4.5+ / `8349ef25`）。
- **R6** 基底为含 #191–#196 的本地 HEAD；不推未审临时文件；push/发版按用户习惯。

## Acceptance Criteria

- [ ] AC1：相对 `8349ef25` behind=0（或仅文档化且经批准的暂缓）
- [ ] AC2：`applicationId=me.arsucar.rikka`；无强制 google-services/Firebase 运行时；`release-apk.yml` 仍为正式发版入口
- [ ] AC3：versionName/versionCode fork 线且 **≥ 2.3.41 / 203**
- [ ] AC4：#59 自动压缩相关 fork 行为未丢失，并与上游阶梯/i18n 可共存
- [ ] AC5：R4 抽样 fix 在树中
- [ ] AC6：CHANGELOG 有同步说明；CI 符合 #68 共存
- [ ] AC7：未向上游开同步 PR
- [ ] AC8：`--no-daemon` 可编译；有设备则 installDebug，无设备 assemble 并如实记录

## Decisions

- **D1** tip = `upstream/master` @ `8349ef25`（behind 目标 0）
- **D2** highlight 整模块合入（#1614 原生实现）
- **D3** LICENSE/README 冲突 **ours**（分段双许可 + RikkaRs 叙事）

## Out of Scope

- 向上游 PR；借机大重构 fork 独有模块（除非冲突最小合成）
- 改写为上游纯 AGPL 叙事
- 默认全量 instrumented test / 全量 lint 基线治理

## Notes

- 设计与执行：`design.md`、`implement.md`；子代理清单：`implement.jsonl` / `check.jsonl`。
- 实现期禁止盲跟上游 version；merge 后必须再扫 Firebase。
