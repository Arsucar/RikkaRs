# Issue #148：执行计划

- [x] 保持 AddTag/Sync 兼容，新增固定 GitHub filter 的 Transition config/type/hash、frozen request、bounded strict parser/result 与错误码。
- [x] 实现纯本地 GitHub Issue completion evidence detector：Markdown code/quote stripping、clause/window 关联、严格 URI/#N、success/negative/PR-build lexicon、host/path/overflow 边界。
- [x] 将当前 logical turn 最终 assistant 文本/evidence 冻结到 Transition prepare；filter SKIPPED 时零 provider 调用。
- [x] 泛化 prepared/terminal audit 写入，定义 filter reject/provider skip/apply 0/1/2 changed 的 bounded summary/diff 与 decision/status/error 矩阵。
- [x] 新增窄 source-node 复查 DAO 与 `ConversationTagHookCommitter`；provider 后 config recheck，事务内 lease/source/tag 校验、remove→add、bounded audit、execution/run terminalization。
- [x] 迁移旧 AddTag handler 使用 committer，以 execution-row golden matrix保持旧 parser/provider/config/hash/status/decision/tag/reason/error/null-audit兼容。
- [x] 扩展 Hook editor/list/history：Transition draft、add/remove 单选、Issue-only、Loading/Error/Empty、摘要与稳定错误展示。
- [x] 使用 locale-tui 增补/翻译所有新增 UI/error/history keys，并做 6 locale XML/coverage/placeholder 审计。
- [x] 补 evidence/parser/hash/provider/handler/UI JVM 测试，以及 Room 原子性、20 标签交换、幂等、缺失 tag 回滚、source/lease/终态失败测试。
- [x] 运行资源、production/JVM/androidTest 编译、全量 JVM、lint changed-file audit 与设备安装流程。
- [ ] 更新 Hook code-spec 与 CHANGELOG，提交推送，发布并复核中英文评论后关闭 #148；最终重新盘点开放 Issues。

## Validation results

- `:app:compileDebugKotlin`, full `:app:testDebugUnitTest`, and `:app:compileDebugAndroidTestKotlin` passed.
- `:app:lintDebug` completed with the repository baseline of 103 errors, 41 warnings, and 1 hint; changed Kotlin filenames and all 28 new resource keys had zero lint-report hits.
- locale-tui wrote all 28 keys across `values`, `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`, and `values-ru`; XML/key/placeholder audit passed.
- `adb connect 100.99.129.110:5555` remained `offline`, so installation and connected instrumentation were not run.
- `git diff --check`, added-line 120-column audit, and changed-UI hardcoded-text audit passed.

## Validation commands

```powershell
.\gradlew --no-daemon :app:processDebugResources :app:compileDebugKotlin `
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
.\gradlew --no-daemon :app:lintDebug
git diff --check
```

Localization workflow:

```powershell
uv run --directory locale-tui src/main.py add <key> "<English>" -m app --skip-translate
uv run --directory locale-tui src/main.py set <key> "<translation>" -l <values-locale> -m app
```

- 对 `values`, `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru` 逐个 XML parse；断言全部目标 key 存在且 `%N$type` placeholder 集合一致。
- lint 失败时解析 text/XML report，分别按改动 Kotlin 文件名与本任务 target resource keys 过滤，历史问题不得描述为通过。

Device flow:

1. `adb devices`；若无 `device` 状态，执行 `adb connect 100.99.129.110:5555` 后再次 `adb devices`。
2. 仍无 `device`：记录 offline/连接错误，只保留编译，不运行安装/instrumentation。
3. 有设备：`.\gradlew --no-daemon :app:installDebug`。
4. 安装失败：重新 `adb connect 100.99.129.110:5555`，再重试 `:app:installDebug` 一次；仍失败如实记录末尾 Gradle/adb 错误。

UI/state verification:

- JVM state/projection tests：Loading 时保存禁用；Error 保留各 Action draft、显示可读错误并允许重试；Empty 显示创建标签入口且保存禁用；Success selector label/summary/同 ID/失效 ID 校验正确。
- Offline 回归：网络状态不参与本地 Hook draft 编辑或 `canSave`；只在实际 provider execution 时暴露既有错误。
- A11y/source audit：两个 selector、固定 filter condition、错误 supporting text 和 summary 均有资源化 label/contentDescription/semantics，状态不只靠颜色。
- Dark Mode/source audit：新增 UI 仅使用 `MaterialTheme.colorScheme`/现有 components，无固定亮色；有设备时在 light/dark 各打开编辑页并核对 selector、error、summary 可读性与触控区域。

## Risk and rollback points

- filter 必须在 provider 前 fail closed；不得扫描旧轮次或 tool raw output。
- 标签转换与 execution/run 终态不得分事务；任何 late failure 必须回滚关系变化。
- 固定 remove→add，避免满标签上限时无法交换；add 失败必须恢复 remove。
- generalized audit JSON 必须有界且不含最终文本、prompt、raw response 或 transcript。
- 不修改旧 AddTag hash material/serial name；新增 Action 必须走 exhaustive sealed `when` 和 UI mapping。
- locale 验证必须解析 6 个 values 文件，检查完整 target-key coverage 与 placeholder 集合一致。
