# Design: Merge upstream tip `fa0305ba` into `release/rikka-arsucar`

## Architecture / Boundaries

- **单任务整并**（D10）：交付物是一次受控 merge + fork 身份修复 + 可编译树。
- **基底**：本地 `HEAD` `38148bb6`（2.3.53 / 215）。
- **源**：`upstream/master` @ **`fa0305ba`**（D1）。
- **策略**（D2）：优先一次 `git merge fa0305ba`（或 `upstream/master` 若仍等于该 SHA）；冲突爆炸再按主题分批（AI → OkHttp/网络 → Backup/Chat → workspace/terminal → build-logic/videogen），仍以同一 tip 为终点。
- **权威不变量**：`docs/RIKKA_ARSUCAR_FORK_AND_CI.md` + PRD D1–D10。

## Data flow / Merge contract

```
upstream/master (fa0305ba)
        │
        ▼
  three-way merge  ←──  local HEAD (fork features + 2.3.53/215)
        │
        ▼
  post-merge gate:
    · applicationId / version 仍 fork
    · 剥离 Firebase / google-services
    · LICENSE/README 叙事 ours (D3)
    · #59 压缩上下文 ours
    · videogen include + app dep (D5)
    · OkHttp: ProxySelector/UA + Clash interceptor (D6)
    · Backup: 上游导入确认 + fork 原子恢复 (D7)
    · AI: #1648 新流式 API + fork GenerationHandler/ChatService 语义 (D8)
    · ChatInput IME 布局 ours (D11)
    · SearchPicker UI/路由 ours (D12)
    · ReasoningPicker 刻度 ours；无用户 MAX 档 (D13)
        │
        ▼
  compile → focused tests → installDebug（有设备）
```

### 冲突热区与合成规则

| 区域 | 规则 |
|------|------|
| `app/build.gradle.kts` | **ours 身份**：`applicationId=me.arsucar.rikka`；version ≥ 215 / 2.3.53，**禁止**上游 179/2.4.12；去掉 firebase；吸收 `implementation(project(":videogen"))` 与无关 build 改动 |
| `settings.gradle.kts` | 吸收 `includeBuild("build-logic")` 与 `include(":videogen")` |
| `gradle/libs.versions.toml` | 可吸收 AGP/Kotlin/deps；**拒绝** `google-services` / `firebase-*` |
| root / app plugins | 不 apply `google-services` / `firebase.crashlytics` |
| `.github/workflows/` | **保留** fork `release-apk.yml`、`pr-apk.yml` 等；上游 workflow 可共存；**勿删** `release-apk.yml` |
| `LICENSE` + 根 README* | **ours**（D3） |
| `di/DataSourceModule.kt` `OkHttpClient` | **合成（D6）**：吸收 `SettingsProxySelector` / `SettingsProxyAuthenticator` / `SettingsSocks5Authenticator` 与可配置 UA；**保留** `AIRequestInterceptor`（Clash #209）。顺序：UA/proxy 设置 → Clash interceptor → logging |
| `PreferencesStore` `NetworkSetting` | 吸收上游字段；保留 fork `clashConfig` |
| `SettingPage` / 路由 | 网络页与 Clash 页并存，不互相替换 |
| `BackupPage` / `BackupVM` / `ImportExportTab` | **合成（D7）**：上游可选导入+覆盖确认；fork #183–#190 原子恢复、任务不随页面销毁、settingsFlow 失败升级 |
| `ai/` `#1648` | **theirs 流式模型**（`StreamChunk`/`StreamChunkHandler`/`UIMessagePart.kt` 拆分、provider decoder）；fork 独有 `ErrorParser`/`KeyRoulette`/`ImageOptions`/DeepSeekMax 方言接到新类型，禁止整文件 theirs 冲掉 |
| `GenerationHandler.kt` / `ChatService.kt` / `ChatVM.kt` | **fork 语义优先**：subagent、keepalive、checkpoint、并发；把上游 `MessageChunk`→新 stream 事件的消费迁移过来。禁止用上游精简版覆盖 fork 的 +3k 行 ChatService |
| `ChatInput.kt` | **D11 ours 布局**：拒绝 `e6e0dfd4` 收工具栏/发送键上移。可吸收 `82758c36` 句首大写。`f86d6e82` 圆角/底边距与 fork 已有 IME 形状重复则 ours。模型搜索 IME 乱关（`fa0305ba`，`ModelList.kt`）仍吸收 |
| `SearchPicker.kt` + `8c3f8240` 对 ChatPage/ChatInput | **D12 ours**：不引入 SearchMode 卡片/新路由。豆包搜索（`search/DoubaoSearchService*`、设置详情）吸收。`aac6e963` ChatService 内外搜索对齐吸收。`fdc0bc9a` 仅改选择器则跳过 |
| `ReasoningPicker.kt` | **D13 ours**：保留底部刻度。不把 `ReasoningLevel.MAX` 加入用户梯子；DeepSeek `max` 继续 XHIGH 方言。`3c9457b4` 映射修复可合入 `Reasoning.kt` 但不得因此露出 MAX 档 |
| `WorkspaceTerminal*` | 吸收后台运行+多 Tab；保留 fork workspace 权限/只读 LINUX 契约 |
| `videogen/` | **theirs 整模块**（D5）；不接产品 UI |
| `build-logic/` + keep/R8 | 接受上游 convention plugin 与 keep 简化；合并后确认 JWT/jlatexmath/serializable keep 仍在 |
| skills / `.claude` 大体量文档 | 可随 tip 进入；不单独产品验收 |
| `CompressContextDialog.kt` | 若冲突，**ours**（#59 分段选数） |

## Compatibility

- **versionCode**：上游 179 << fork 215 → 合并后仍 ≥ 215；本设计不在 merge commit bump。
- **OkHttp**：上游 `ProxySelector` 与 Clash 429 replay 同时存在。Clash 仍对 429 `chain.proceed` 重放；系统/设置代理由 `ProxySelector` 决定下一跳。两者职责不同：一个是 HTTP 代理配置，一个是 Clash 节点轮换。
- **AI API**：`GenerationHandler.handleStreamChunk(MessageChunk)` 必须改接到 `#1648` 的 provider-independent 事件；漏接会导致流式/工具调用回归。
- **videogen**：模块编译进 APK，无 UI 入口可接受。
- **AGP/Kotlin/build-logic**：接受上游版本线；编译失败在 merge 分支内修，不回退 tip。

## Trade-offs

| 选择 | 理由 |
|------|------|
| 一次 merge 到 tip | behind=0；比 66 次 cherry-pick 少漏依赖 |
| videogen 全收 | D5；拆掉 include 会使 behind≠0 且 app 依赖断裂 |
| 网络页 + Clash | D6；职责正交，丢任一边都是产品回退 |
| AI 不整文件 theirs | fork Chat/subagent 远大于上游窗口 diff |
| ChatInput/Search/Reasoning UI ours | 用户明确拒绝这三处手感回退；behind 仍以 git merge 计 0，产品 UI 用 ours 覆盖 |
| 不拆 child | 单次集成验收；分批只是执行战术 |

## Rollback

- merge 未 push：`git merge --abort` 或 `git reset --hard` 到 merge 前 HEAD。
- 已局部提交未 push：`git reset --hard <pre-merge>`。
- 已 push：revert merge commit（需 `-m 1`）或新 commit 修复；**禁止** force-push 除非用户明示。
- 记录 pre-merge SHA 于执行日志。

## Out of design scope

- 向上游 PR、改 keystore/Secrets、全量 lint 基线、默认 instrumented tests、videogen 产品 UI。
