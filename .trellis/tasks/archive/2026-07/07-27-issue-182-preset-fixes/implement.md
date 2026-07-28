# Implementation Plan（#182 审查修复）

> 依赖顺序：数据模型/注册表标记（前置） → 注入 transformer + 迁移（依赖前置） → UI（依赖前置） → 清理/规范 → 编译+测试。
> 网关稳定时可并行 Block B/C；实现子代理只做代码修改，最后一个检查子代理才跑 Gradle。
>
> 恢复状态（2026-07-27）：Block A-D 与聚焦测试已完成；最终合并 Gradle 验证和冻结构建安装待执行。

## Block A — 模型 + 注册表标记（前置，先冻结契约）

1. `BuiltinPromptDef` 增加可注入性标记（`injectable: Boolean` 或 `kind: BuiltinKind`），默认值向后兼容。
   - 当前 4 个 key 全部 config-only（`injectable=false`）；真实消费继续由各自专用流程负责。
2. `PresetEntry.Custom` 增加 `legacyPriority: Int? = null`（迁移锁定排序用，见 Block B-3）。
3. 决定 `displayNameRes` 去留（配合 UI 修复项 5）；`dynamic` 是否收敛为计算属性。
风险文件：`data/ai/prompts/BuiltinPromptRegistry.kt`、`data/model/PresetEntry.kt`。

## Block B — 注入 transformer + 迁移（依赖 A）

修复审查发现 #1–#6：
1. **config-only 不注入**：`resolvePresetEntry` 对当前 4 个 Builtin 返回 null，不进入通用注入路径。
2. **宏不泄漏**：测试锁定包含 override/动态宏的 Builtin 仍完全跳过；不扩展 `TransformerContext`。
3. **去重**：collectInjections 对 step1 直连 + step1b entries 按 id 去重。
4. **全局 enabled 继承**：`migratedWithEntries` 的 `enabled = (id !in disabledEntryIds) && injection.enabled`。
5. **排序锁定**：迁移写 `legacyPriority`，resolvePresetEntry 迁移来源优先用它，否则 `-order`。
6. **迁移持久化 + 过滤顺序**：`PreferencesStore` 先迁移（未过滤 id 命中内容）再过滤旧字段；参考 `scheduleSubagentBuiltinMigrationPersist` 调度一次性持久化。
风险文件：`PromptInjectionTransformer.kt`、`Assistant.kt`（migratedWithEntries）、`PreferencesStore.kt`。

## Block C — UI（依赖 A，可与 B 并行）

修复审查发现 #7–#10 + 展示：
1. **统计修复**：`Preset` 加统一展示入口，`PresetCard`/`ExtensionContent`/`enabledInPreset` 改用（entries-aware）。
2. **Reference 失效不伪装选中**：`PresetDetailPage.kt:542` 去掉 `?: first()`，失效显示占位/禁用确认。
3. **切换 builtinKey 重置 override**：`:479` 重置 overrideContent/overridePosition/role/position。
4. **moveInGroup 保持跨组顺序**：改为原位重排该组，不挪到 entries 末尾（design.md 方案 b）。
5. config-only Builtin 在编辑/卡片上加「配置型·不注入对话」徽标。
6. Builtin 新增和编辑共用去重规则：新增排除全部已占用 key；编辑保留当前 key并排除兄弟条目占用 key。
7. 使用 `sh.calvin.reorderable` 做同类型组内拖拽；详情页只展示 Builtin + Custom，保留 Reference 数据与旧入口。
8. 删除菜单只设置 pending target，复用 `RikkaConfirmDialog` 显示目标名称，确认后才按 UUID 删除。
风险文件：`ui/pages/extensions/PresetDetailPage.kt`、`PromptPage.kt`、`ui/components/ai/ExtensionContent.kt`。

## Block C2 — Builtin 专用消费回流（用户后续拍板）

1. 新增纯函数 `resolveBuiltinOverride`，只解析当前助手关联 preset 中启用、同 key、非空的覆盖。
2. `reply_draft` / `suggestion` 在 `ChatService` 使用覆盖；suggestion 回退全局设置。
3. workspace/memory transformer 在持有运行时数据的边界替换宏；memory 数据块不得被静态覆盖吞掉。
4. 纯函数测试覆盖禁用、空白、多条目顺序、模板替换与 memory 宏有/无两路。

## Block C3 — 用户复现回归（2026-07-28）

1. `PresetDetailPage` 改为本地乐观 preset + 相对 mutation，覆盖 metadata、add/edit/toggle/delete/reorder。
2. `PromptVM` 串行 preset mutation；`SettingsStore` 新增 latest-persisted、target-only 的事务 writer。
3. 为 reply-draft/suggestion 登记单花括号变量；变量区与 `dynamic` 解耦。
4. 新增 stale fallback + 连续 mutation 持久层测试，以及四模板精确变量/UI helper 测试。

## Block D — 清理 + 规范（低优先）

- 删/接线 `displayNameRes`；`dynamic` 计算属性；`VAR_MEMORY_TABLES` 复用 `MEMORY_TABLE_MACRO`。
- 提取重复 helper（`presetPositionLabel` 等）到共享文件。
- `RouteActivity.kt` 删重复 import + 回退空白 churn。
- `name`/`description` 本地 draft + 防抖持久化；分组计算 `remember`。
- `BuiltinPromptRegistry.kt:84-86` 拆行 ≤120（勿改注入语义）。
- i18n 全量：**仅当用户要求**，用 `locale-tui-localization` skill 补 4 语言。

## Block E — 编译 + 测试（最后一个子代理 / 主代理收尾）

1. 单测：迁移混合来源排序锁定、去重、enabled 继承、4 个 Builtin 不注入且不泄漏宏、
   专用 override 消费、Builtin 新增/编辑去重、拖拽跨组不变、entries-aware 计数、旧 JSON 往返和 DataStore 迁移写回。
2. 一次合并运行资源处理、生产 Kotlin、完整 app JVM 单测和 AndroidTest Kotlin 编译（全部 `--no-daemon`）。
3. `git diff --check`（空白）；有设备 `:app:installDebug` 真机验收。

### 2026-07-27 验证记录

- 聚焦 `PresetEntryUiTest`：通过（13 tests），覆盖新增/编辑 Builtin 去重及既有 UI helper。
- 最终合并 Gradle：`:app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin`，`BUILD SUCCESSFUL`。
- `git diff --check`：通过。
- Lint：本次续跑未重复；上一轮唯一一次 lint 为历史基线失败，改动 Kotlin 文件无新增诊断。
- 安装：设备 `100.99.129.110:5555` 在线；`installDebug` 初次及唯一重试均连续 120 秒无输出后终止。
  设备现有包 `me.arsucar.rikka.debug` 仍为 `2.3.39`，`lastUpdateTime=2026-07-27 17:04:17`，
  因此最终工作树**未通过真机安装/视觉/拖拽验收**。
- 最终 APK：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`；
  SHA-256 `1D4CEF12683688378BEFFCAA0A6342B065FBCB0BB3E67C5B2FFC97FEEADFC958`。

### 2026-07-28 增量验证记录

- 聚焦测试通过：`BuiltinPromptRegistryTest` 12、`PresetEntriesMigrationPersistenceTest` 6、
  `PresetEntryUiTest` 14，共 32 项，0 failures / 0 errors。
- Kotlin daemon 的 Windows 增量备份出现一次 `NoSuchFileException`；Gradle 自动降级为无 daemon 编译后
  `BUILD SUCCESSFUL`，不属于代码失败。
- 生产代码已再次变化；2026-07-27 的完整 Gradle、APK 哈希和安装结果不再代表最终冻结构建。
- 最终合并 Gradle 首次被生成的 `app/build/kspCaches/debug` lookup storage 损坏阻断；定向清理该缓存后，
  `:app:processDebugResources :app:compileDebugKotlin :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin` 全部通过，`BUILD SUCCESSFUL`。
- 设备验收：`adb devices` 无设备；唯一一次 `adb connect 100.99.129.110:5555` 超时（10060），
  复查仍无设备，因此未执行 `installDebug`，真机布局/手势验收未完成。
- `:app:assembleDebug` 通过。最终 APK：`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`，
  SHA-256 `6F68909A01650A53EC477D5C9EA75DAEC308711D1BBBE6F8BEE1DC8D5B75DDE5`，
  GoFile `https://gofile.io/d/08qhJG`（公开页面检查为 HTTP 200）。

## 验收门

- 现有用户迁移后注入行为不回归（顺序/去重/enabled 单测锁定）。
- 4 个 config-only 内置不注入对话且不报错；无双注入、无字面宏泄漏。
- entries-only 预设卡片计数/名称正确；Reference 失效有明确提示；切换 builtinKey 不串内容。

## 最大风险

1. `memory_tables` 双注入 —— 必须先核对 transformer pipeline 注册顺序再定策略。
2. priority→order 排序锁定 —— 混合来源（迁移条目 + 直连 + lorebook）单测是防回归关键。
3. 模型新增字段的序列化向后兼容 —— 默认值 + 旧 JSON 往返测试。
