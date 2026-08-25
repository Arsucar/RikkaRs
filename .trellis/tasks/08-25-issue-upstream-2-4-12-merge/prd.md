# Merge upstream 2.4.6–2.4.12 into release/rikka-arsucar

## Goal

将上游 `rikkahub/rikkahub` `master` tip **`fa0305ba`**（2.4.6–2.4.12 + 2 笔 IME 修复）同步到 `release/rikka-arsucar`，吸收 bugfix 与有价值能力，并固定保留 fork 身份与 CI。

## Background

| 项 | 值 |
|----|-----|
| 基底 | `38148bb6`；`versionName=2.3.53` / `versionCode=215`；`applicationId=me.arsucar.rikka` |
| origin | 与 HEAD 同步；工作树另有未提交 `.trellis/spec/guides/ui-modification-thinking-guide.md` |
| 上次整并 | #197 → `778c9113` ← `8349ef25`（2.4.2–2.4.5+） |
| merge-base | `8349ef25` |
| D1 tip | `fa0305ba`（2026-08-25 fetch） |
| 正式 tag | `2.4.12` = `e8293d35` |
| 上游声明 version | `2.4.12` / `179` / `me.rerere.rikkahub` |
| behind / ahead | **66** / **3029** |
| 窗口 | 291 files，+12932 / −18487 |

无对应 GitHub issue（open 仅 #304/#302/#301）。本任务 Trellis-only。权威工程约束：`docs/RIKKA_ARSUCAR_FORK_AND_CI.md`。前次任务：`.trellis/tasks/07-31-issue-197-upstream-merge/`。

### Fork 不变量

- `applicationId=me.arsucar.rikka`（Debug：`me.arsucar.rikka.debug`）；Kotlin 包名可仍 `me.rerere.rikkahub`
- 无 Firebase / 无 `google-services`
- 正式发版仅 `.github/workflows/release-apk.yml`（可与上游其它 workflow 共存）
- version 走 fork 线，合并后 **≥ 2.3.53 / 215**（禁止上游 179 / 2.4.12）
- 压缩上下文以 **#59** 为准（`CustomNumberSelector` 分段选数）
- LICENSE/README 保持分段双许可 + RikkaRs 叙事
- 不向 `rikkahub/rikkahub` 提 PR
- Clash 429 切节点（#209，`AIRequestInterceptor`）不得被上游网络页替换掉

### 吸收窗口

- **2.4.8**：服务端搜索门控、DeepSeek Responses 搜索、`refactor(ai) #1648`、流式事件归一、R8/keep、搜索选择 UI
- **2.4.9**：思考强度 Max、流式 `tool_calls` null 崩溃、图生 Provider Override、jlatexmath/R8
- **2.4.10**：豆包搜索、正则拖拽排序、DashScope ASR、`.agc`、加密 reasoning 不重放明文
- **2.4.11**：网络设置（UA/代理/连接测试）、备份可选导入+覆盖确认、DeepSeek V4 Flash Vision、JWT 混淆、更新检查节流、MiMo 思考参数
- **2.4.12**：工作区终端后台+多 Tab、ChatInput/通知、fork 会话继承 folder/cwd、SnakeYAML skill、OpenRouter `session_id`、空 tool schema 归一
- **未发版**：ChatInput IME 动画稳定性、模型搜索 IME 自动关闭

### 高风险面

- `ai/` 67 文件：`#1648` 流式重构 + 拆分 `UIMessagePart`。fork `GenerationHandler` 仍消费 `MessageChunk`；`ChatService` 相对 merge-base +3014 行。
- 新模块 `videogen/`：API 骨架（Aliyun/MiniMax/Volcengine）；上游 `settings` + `app` 已依赖；无产品 UI。
- 新 `build-logic/` convention plugin。
- 上游 `OkHttpClient` 改为 `ProxySelector` + 可配置 UA；fork 在同一 client 上挂 Clash interceptor。
- 备份 UI（`BackupPage`/`BackupVM`/`ImportExportTab`）双边均改：上游可选导入+覆盖确认；fork 有 #183–#190 原子恢复/生命周期。
- 工作区终端多 Tab（`WorkspaceTerminalPage` / `WorkspaceTerminalSessionManager`）。

### 会改用户手感的上游改动（相对当前 fork）

日常路径，合入即改肌肉记忆：

| 改动 | 当前 fork | 上游 | 惯性影响 |
|------|-----------|------|----------|
| 键盘弹出时 ChatInput | 工具栏仍在；仅底角变直角、底边距 0 | 收起工具栏，发送键上移到输入行（`e6e0dfd4`） | **D11 ours**：不跟 |
| 英文句首自动大写 | 无 `KeyboardCapitalization.Sentences` | 输入框句首自动大写 | 中文影响小；英文输入会变 |
| 备份首页 Tab | WebDAV → S3 → 导入导出 → 提醒 | **本地** → WebDAV → S3 → 提醒 | 习惯点第一页进 WebDAV 的人会进本地备份 |
| 助手上下文条数 | 0–512 **滑条**（#59 自动压缩在下方） | 数字输入；1–19 被抬到 20 | 设置页手感变；与 #59 同一 Form 区，冲突热区 |
| 搜索选择器 | 现有 bottom sheet | `SearchPicker` 大改（SearchMode 卡片 + 路由）+ provider 门控 | **D12 ours**：选择器 UI/路由不跟；豆包搜索与 ChatService 内外搜索对齐仍吸收 |
| 推理档位 | 滑条 + 底部刻度；XHIGH 经方言映射 `max` | 去掉刻度；枚举新增 MAX(32k) | **D13 ours**：UI 与用户档位梯子不跟 |
| 工作区终端 | 单会话 | 后台运行 + 多 Tab | 终端用户增益；单 Tab 习惯被扩成多会话 |
| 本地备份导入 | 直接导入 | 先选内容 + 覆盖确认 | 多一步确认，防误覆盖 |

不改日常手感、主要是修 bug / 加能力：空 tool schema、IME 搜索框乱关、fork 会话继承 folder/cwd、DashScope ASR、JWT/R8、图生 Override、豆包搜索、正则拖拽排序、网络设置新页（设置里多入口，Clash 仍在）、暂停更新提醒、MCP header 显隐、新模型图标。`#1648` / videogen / build-logic 无独立产品 UI。

## Requirements

- **R1** 合并 D1 tip（或等价完整吸收），目标 behind=0。
- **R2** 合并后全部 Fork 不变量成立。
- **R3** 冲突按 `design.md` 热区合成。
- **R4** 窗口内关键能力在树中：空 tool schema、IME 两笔、fork 会话 folder/cwd、DashScope ASR、JWT/R8、备份覆盖确认、网络页、videogen 模块、终端多 Tab。
- **R5** CHANGELOG 写明同步（2.4.6–2.4.12+ / `fa0305ba`）。
- **R6** 基底为当前 HEAD；merge 前处理脏工作树；不推未审临时文件；push/发版按用户习惯。
- **R7** merge 前若 `upstream/master` ≠ `fa0305ba`，停并询问，禁止静默扩大窗口。
- **R8** `videogen/` 整模块进树；不产品化视频生成 UI。
- **R9** 上游网络页与 Clash #209 共存于同一 `OkHttpClient`。
- **R10** 吸收上游备份可选导入+覆盖确认，同时保留 fork 原子恢复/任务生命周期。
- **R11** ChatInput：键盘弹出时工具栏仍可见，发送键不因 IME 上移到输入行（D11）。可吸收句首大写。
- **R12** 搜索选择器 UI/路由保持 fork；不引入上游 SearchMode 卡片导航。豆包搜索服务与 `ChatService` 内外搜索对齐可合入（D12）。
- **R13** 推理选择器保持 fork 刻度 UI；不把上游 `ReasoningLevel.MAX` 暴露为新档。fork 已有 XHIGH→`max` 方言（D13）。

## Acceptance Criteria

- [ ] AC1：`git rev-list --count HEAD..upstream/master` 对 D1 tip 为 0
- [ ] AC2：`applicationId=me.arsucar.rikka`；无 google-services/Firebase 运行时；`release-apk.yml` 仍为正式发版入口
- [ ] AC3：`versionName`/`versionCode` fork 线且 **≥ 2.3.53 / 215**
- [ ] AC4：#59 压缩对话框分段选数仍在
- [ ] AC5：R4 抽样能力在树中（含 `videogen` include、网络页、Clash interceptor）
- [ ] AC6：CHANGELOG 有同步说明
- [ ] AC7：未向上游开同步 PR
- [ ] AC8：`--no-daemon` 可编译；有设备则 `installDebug`，无设备 `assembleDebug` 并如实记录
- [ ] AC9：LICENSE/README 仍为 fork 叙事（D3）
- [ ] AC10：`AIRequestInterceptor` 仍注册；`NetworkSetting`/`ProxyConfig`/`SettingPreferencesNetworkPage` 在树中
- [ ] AC11：IME 可见时 ChatInput 工具栏仍在（无「仅发送键上移」布局）
- [ ] AC12：`SearchPicker.kt` 无上游 SearchMode 卡片/新路由；豆包搜索服务在树中
- [ ] AC13：`ReasoningPicker` 仍有底部刻度；用户档位无独立 MAX 档（XHIGH 顶档）

## Decisions

- **D1** tip = `upstream/master` @ **`fa0305ba`**。tip 再前进则停问。
- **D2** 优先一次 `git merge` 到 D1；冲突爆炸再按主题分批，仍以同一 tip 为终点。
- **D3** LICENSE/README 冲突 **ours**。
- **D4** 不强制新建 GitHub issue。
- **D5** `videogen/` 整模块合入；不产品化 UI。
- **D6** 网络页 + Clash #209 都留：吸收 `ProxySelector`/UA，保留 `AIRequestInterceptor`。
- **D7** 备份：吸收上游可选导入+覆盖确认；fork #183–#190 原子恢复/生命周期优先。
- **D8** AI：吸收 `#1648` 流式事件模型与 `UIMessagePart` 拆分；`GenerationHandler`/`ChatService`/subagent/图生精细参数按 fork 语义接到新 API，禁止整文件 theirs。
- **D9** 接受 `build-logic/` 与上游 keep/R8 改动；合并后扫 Firebase。
- **D10** 单任务整并，不拆 parent/child。merge commit 不 bump 版本；发版另走既有流程。
- **D11** ChatInput IME 布局 **ours**：不收工具栏、发送键不上移。句首大写可合入。`f86d6e82` 圆角/底边距若与 fork 已有逻辑重复则 ours。
- **D12** `SearchPicker.kt` 及 `8c3f8240` 对 `ChatPage`/`ChatInput` 的选择器路由 **ours**。吸收 `c88822d6` 豆包搜索与 `aac6e963` ChatService 对齐；`fdc0bc9a` 仅改选择器则跳过。
- **D13** `ReasoningPicker.kt` **ours**（保留刻度）。不把 `ReasoningLevel.MAX` 加入用户梯子；DeepSeek `max` 继续走 fork XHIGH 方言。`3c9457b4` 等映射修复可合入。

## Out of Scope

- 向上游 PR；借机大重构 fork 独有模块（除非冲突最小合成）
- 改写为上游纯 AGPL 叙事
- 默认全量 instrumented test / 全量 lint 基线治理
- 把 fork versionName 改成上游 `2.4.12`
- 完成 videogen 产品化
- 用上游网络页替换 Clash，或反过来丢掉网络页
- 上游 ChatInput「键盘收工具栏」布局、SearchPicker SearchMode 路由、ReasoningPicker 去刻度/MAX 档

## Notes

- 设计与执行：`design.md`、`implement.md`；子代理清单：`implement.jsonl` / `check.jsonl`。
- 实现期禁止盲跟上游 version；merge 后必须再扫 Firebase。
