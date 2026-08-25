# Implement: merge upstream `fa0305ba` (2.4.6–2.4.12+)

## Pre-merge

1. 记录 `PRE_MERGE=$(git rev-parse HEAD)`（预期 `38148bb6` 或其后的同分支提交）。
2. `git fetch origin && git fetch upstream`。若 `upstream/master` ≠ `fa0305ba`，**停**并问用户（R7）。
3. 工作树干净。当前脏文件 `.trellis/spec/guides/ui-modification-thinking-guide.md`：stash 或先提交/还原，禁止带进 merge。
4. 确认 `git merge-base HEAD upstream/master` 仍为 `8349ef25`。

## Merge

5. `git merge fa0305ba -m "merge(upstream): sync master fa0305ba (2.4.6–2.4.12+) into release/rikka-arsucar"`
6. 按 `design.md` 热区解冲突。禁止接受上游 `applicationId` / 低 versionCode / Firebase / 纯 AGPL LICENSE / 丢掉 Clash interceptor / 用上游精简 `ChatService` 覆盖 fork / D11–D13 的 UI theirs。

## Post-merge gate（必须）

7. **身份**：`applicationId=me.arsucar.rikka`；version ≥ `2.3.53` / `215`。
8. **Firebase 扫除**：catalog、root/app plugins、代码/DI 无 `google-services` / `Firebase*`。
9. **LICENSE/README**：fork 叙事（D3）。
10. **#59**：压缩对话框分段选数仍在。
11. **workflows**：`release-apk.yml` 仍在。
12. **videogen**（D5）：`settings.gradle.kts` include、`app` implementation、模块源码在树中。
13. **网络 + Clash**（D6）：`NetworkSetting`/`ProxyConfig`/`SettingPreferencesNetworkPage` 在；`AIRequestInterceptor` 仍加入 `OkHttpClient`。
14. **备份**（D7）：覆盖确认+可选导入在；fork 原子恢复路径未删。
15. **AI**（D8）：`UIMessagePart.kt` / stream handler 在；`GenerationHandler` 消费新事件而非旧 `MessageChunk` 裸类型（若上游已改名）。
16. **build-logic**：`includeBuild("build-logic")` 可解析。
16b. **D11**：IME 可见时 ChatInput 工具栏仍在；无发送键因 IME 上移。
16c. **D12**：`SearchPicker.kt` 无 SearchMode 卡片路由；`DoubaoSearchService` 在。
16d. **D13**：`ReasoningPicker` 仍有刻度；`ReasoningLevel` 用户档无 MAX（或 MAX 未接入选择器）。

## Validate

17. 聚焦：`.\gradlew --no-daemon :ai:testDebugUnitTest :videogen:testDebugUnitTest :app:compileDebugKotlin`（可与资源处理合并一次调用）。
18. 若 OkHttp/网络有单测：`ProxyConfigTest` + 既有 Clash 单测。
19. **仅最后一个检查代理允许跑 Gradle**。
20. 有设备：`adb devices` → 必要时 `adb connect 100.99.129.110:5555` → `.\gradlew --no-daemon :app:installDebug`。
21. 无设备：`assembleDebug` + 如实记录。

## Docs

22. `CHANGELOG.md` 增加上游同步条目（2.4.6–2.4.12+ / tip `fa0305ba`）。
23. 不强制开/关 GitHub issue（D4）。

## Risky files

- `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `LICENSE`, `README*.md`
- `di/DataSourceModule.kt`, `PreferencesStore.kt`, `AIRequestInterceptor.kt`
- `GenerationHandler.kt`, `ChatService.kt`, `ChatVM.kt`
- `ai/**`（尤其 `Message.kt` / 新 `UIMessagePart.kt` / stream decoders）
- `BackupPage.kt`, `BackupVM.kt`, `ImportExportTab.kt`
- `WorkspaceTerminalPage.kt`, `WorkspaceTerminalSessionManager.kt`
- `videogen/**`, `build-logic/**`, `.github/workflows/release-apk.yml`
- `ChatInput.kt`, `SearchPicker.kt`, `ReasoningPicker.kt`

## Rollback points

- 冲突无法收敛：`git merge --abort`
- 编译大面积红：reset 到 `PRE_MERGE`，改分批或缩小 tip（需用户同意）

## Do not

- 向 `rikkahub/rikkahub` 开 PR
- 默认全量 `:app:lintDebug` / `connectedDebugAndroidTest`
- 盲跟上游 version 179 / 2.4.12
- 用上游 `OkHttpClient` 块整段覆盖导致 Clash 丢失
- 产品化 videogen UI
- 接受上游 ChatInput 收工具栏、SearchPicker SearchMode 路由、ReasoningPicker 去刻度/MAX 档
