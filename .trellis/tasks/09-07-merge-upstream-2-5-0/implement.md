# 执行计划：合并上游 2.5.0

## 0. 预检（主线程）
- [ ] `git status --porcelain` 处置 20 个 dirty 路径：向用户确认 / stash（不擅自提交）。
- [ ] 确认当前 HEAD = 984366a5（merge 基点），upstream/master = 12ee935e。

## 1. 合并落盘（主线程）
- [ ] `git merge upstream/master --no-edit`（预期自动失败，进入冲突态）。
- [ ] 不手动逐文件解，记录冲突文件清单后分派子代理。

## 2. 冲突解决（trellis-implement 并行，禁止编译）
按独立域拆分，每个子代理一个 prompt（必须以 `Active task: <path>` 开头）：
- [ ] 组A 构建配置：gradle/libs.versions.toml、app/build.gradle.kts（保留无 Firebase/web preBuild/包名）。
- [ ] 组B ai 模块：GoogleProvider / ResponseAPI / Message.kt / ResponseApiStreamDecoderTest + fork ai 定制回植核查。
- [ ] 组C speech 模块：8 TTS provider + 2 ASR controller（上游重试/双向流式 + fork 定制仲裁）。
- [ ] 组D app 核心：GenerationLoop / ChatService / ConversationSession / MessageNodeDAO / MessageFtsManager / Conversation.kt / PreferencesStore / S3Sync / WebDavSync / AppModule / DataSourceModule / RikkaHubApp / Message.kt 测试。
- [ ] 组E app UI+杂项：ChatPage/ChatInput/TTSAutoPlay/SettingPage/SettingPreferencesUIPage/AssistantMemoryPage/BackupVM/ImportExportTab/WorkspaceDetailPage/WorkspaceTerminalPage/SearchVM/HighlightCodeBlock/PromptInjectionTransformerTest + search/ExaSearchService + web-ui/extension-picker + workspace/ProotShellRunner + README×3。

## 3. 整合自查（主线程，静态）
- [ ] `git diff --check` 无冲突标记；`grep -r "<<<<<<<"` 全仓为零（排除 binary）。
- [ ] Koin 注册无缺漏（上游新增 service/repo 均 has注册）。

## 4. 验收（单一 trellis-check 子代理，唯一可编译）
- [ ] `.\gradlew --no-daemon :app:assembleDebug`
- [ ] 聚焦 JVM 测试：ai + speech + app 冲突相关测试类（一次 Gradle 调用）。
- [ ] 失败 → 回到 2/3 修复循环（仅新 HIGH/CRITICAL 或编译错误的循环允许）。

## 5. 设备安装（主线程按 AGENTS.md 流程）
- [ ] adb devices → 无则 connect 100.99.129.110:5555。
- [ ] `.\gradlew --no-daemon :app:installDebug`（失败重连后重试一次）。
- [ ] 真机启动冒烟（聊天发送一条、设置页打开）。

## 6. 收尾（主线程）
- [ ] CHANGELOG.md 按 docs/CHANGELOG_GUIDE.md 更新（versionName/versionCode 处理按 fork 发版规约，确认上游 2.5.0 与 fork 版本号映射写入说明）。
- [ ] `git status` 核对合并提交内容后询问用户是否 push origin release/rikka-arsucar。

## 验证命令汇总
```bash
git merge upstream/master --no-edit
# 冲突解决后：
.\gradlew --no-daemon :app:assembleDebug
.\gradlew --no-daemon test  # 或聚焦 testDebugUnitTest 过滤冲突测试类
.\gradlew --no-daemon :app:installDebug
```

## 回滚点
- 合并中：`git merge --abort`。
- 合并后未 push：`git reset --hard 984366a5`。
