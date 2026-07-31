# Implement: #197 upstream merge to `8349ef25`

## Pre-merge

1. 记录 SHA：`PRE_MERGE=$(git rev-parse HEAD)`（预期含 2.3.41/203）。
2. `git fetch origin && git fetch upstream`；确认 `upstream/master` 仍为 `8349ef25`（若 tip 再前进，**停**并问用户是否跟新 tip）。
3. 工作树干净；若有未提交 trellis 文档可先保留或 stash 策略按现场。

## Merge

4. `git merge upstream/master -m "merge(upstream): sync master 8349ef25 (2.4.2–2.4.5+) into release/rikka-arsucar (#197)"`
5. 按 `design.md` 热区解冲突；禁止接受上游 `applicationId` / 低 versionCode / Firebase 依赖块 / 纯 AGPL LICENSE 覆盖。

## Post-merge gate（必须）

6. **身份**：`app/build.gradle.kts` → `applicationId=me.arsucar.rikka`；version ≥ `2.3.41` / `203`。
7. **Firebase 扫除**：
   - `gradle/libs.versions.toml`、root/`app` build 无 google-services / firebase 插件与 deps
   - 代码/DI 无 `Firebase` / `FirebaseAnalytics` / `google-services`
8. **LICENSE/README**：仍为 fork 分段双许可 + RikkaRs 叙事（D3）。
9. **#59 + 阶梯截断**：`AssistantBasicPage` / `Assistant` / generation 路径同时保留 fork 自动压缩与上游阶梯/i18n。
10. **workflows**：存在且有效 `release-apk.yml`。
11. **highlight**：上游原生实现在树中且模块可编译（D2）。

## Validate

12. `.\gradlew --no-daemon :app:compileDebugKotlin`（或合并资源+编译+聚焦测试一次调用）。
13. 有关模块单元测试：优先 `Message` 阶梯截断、highlight、Chat 相关已有 JVM test；**仅最后一个检查代理允许跑 Gradle**。
14. 有设备：`adb devices` → 必要时 `adb connect 100.99.129.110:5555` → `.\gradlew --no-daemon :app:installDebug`。
15. 无设备：`assembleDebug` + 如实记录；勿假称真机通过。

## Docs / Issue

16. `CHANGELOG.md` 增加上游同步条目（2.4.2–2.4.5+ / tip `8349ef25`）。
17. 关闭 #197 前中英交付评论（解决点/验证/定位/已知边界）；`Closes #197` 可在 merge 或后续 commit message。

## Risky files

- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `LICENSE`, `README*.md`
- `Assistant.kt`, `AssistantBasicPage.kt`, `GenerationHandler.kt`, `Message.kt`
- `McpManager*` 拆分结果, `ChatService.kt`, `ChatVM.kt`, `BackupVM.kt`
- `highlight/**`, `PresetTheme*`, `.github/workflows/release-apk.yml`

## Rollback points

- 冲突无法收敛：`git merge --abort`
- 编译大面积红：reset 到 `PRE_MERGE`，改分批 merge 或缩小 tip（需用户同意）

## Do not

- 向 `rikkahub/rikkahub` 开 PR
- 默认全量 `:app:lintDebug` / `connectedDebugAndroidTest`
- 盲跟上游 version 172 / 2.4.5
