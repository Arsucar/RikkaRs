# Design — 处理所有 open issue 与 PR

## 架构决策

### 分层策略

**第一层：已有 PR 审查合并（6 个）**
- 这些 PR 已有现成实现，审查后直接合并，快速关闭 6 个 issue
- 风险低，ROI 高
- 合并顺序：先低风险（#264 文档 → #300 常量 → #297 Modifier → #298 SharingStarted），后需仔细验证（#299 迁移 → #283 鲁棒性）

**第二层：无 PR 的 bug 类 issue（7 个）**
- 按依赖关系排序实现
- #267 DataStore → #268 状态收集 → #290 DI → #294 静态分析 → #287 弹窗滚动 → #292 i18n → #276 测试

**第三层：无 PR 的 enhancement（2 个）**
- #296 拆分 ChatVM → #295 乐观更新
- 这两个是最大重构，放最后避免与中间层交叉

### PR 审查要点

#### PR #300（Magic Number）
- 13 处 delay/debounce 字面量提取为 `private const val`
- 验证：常量命名语义清晰（如 `WEB_SERVER_START_DELAY_MS`）、放置位置合理（文件顶部或 companion object）
- 风险：`SemanticMemoryBrowserPage.kt` 用 `MEMORY_MESSAGE_CLEAR_DELAY_MS`，`SemanticMemorySettingPage.kt` 用 `SETTING_MESSAGE_CLEAR_DELAY_MS`，issue 建议同名但 PR 用了不同名 — 可接受，因语义不同（5000ms vs 3000ms）

#### PR #299（ConversationEntity 索引）
- DB version 52→53，新增 4 个联合索引
- 关键验证点：
  1. `Migration_52_53.kt` 中索引名必须与 Room 从 `@Entity(indices=...)` 自动生成的名称一致
  2. ConversationEntity 无 `tableName`，Room 使用类名 "ConversationEntity"（混合大小写）作为表名
  3. `AppDatabase.kt` version 53 + 迁移注册
  4. `Migration_52_53_Test.kt` 使用 `runMigrationsAndValidate` 验证 schema
- 风险：如果索引名不匹配，Room schema 验证会失败

#### PR #298（SharingStarted）
- 22 处 `Eagerly` → `WhileSubscribed(5000)`
- 验证：5000ms 超时对所有场景合理；`updateState`（ChatVM:439）使用 `checkUpdate()` 是否适合 WhileSubscribed
- 风险：`updateChecker.checkUpdate()` 在无订阅时取消，可能影响更新检查时机 — 但有订阅时正常，可接受

#### PR #297（Modifier 顺序）
- 5 处 `.clickable().padding()` → `.padding().clickable()`
- 验证：视觉无回归（padding 在 clickable 外，点击区域增大）
- 风险：低

#### PR #283（代码鲁棒性）
- 多文件修改：
  - `MemoryTableTools.kt`：`runCatching{}.getOrNull()` → `.onFailure{Log.w}.getOrNull()`（多处）
  - `SearchTools.kt`：`map["items"]!!.jsonArray` → `map["items"]?.jsonArray ?: emptyList()`
  - `MemoryTableInjectionTransformer.kt`：同 MemoryTableTools 模式
  - `WebDavClient.kt`：`currentHref!!` → 局部变量缓存
  - `HighlightCodeBlock.kt` / `Markdown.kt`：`printStackTrace()` → `Log.w()`
  - `SettingSearchPage.kt`：`primaryConstructor!!.callBy` → null 检查
  - `ProviderConfigure.kt`：`?.bufferedReader()?.readText()` → `.use { it.bufferedReader().readText() }`
  - `ContextUtil.kt`：`file.inputStream().copyTo` → `.use { it.copyTo }`
  - `EmojiUtils.kt`：`json["emojis"]!!.jsonObject` → `?.jsonObject ?: emptyList()`
- 验证：每处降级不掩盖真实错误；Log.w 用于可观察但非致命的解析失败

#### PR #264（README）
- 补充 pnpm 说明 + 两行空行
- 风险：极低

### 无 PR issue 实现设计

#### #296 拆分 ChatVM
- 方案 A：6 VM + 共享 Repository + StateFlow
- 共享 `conversation` StateFlow 通过 `SavedStateHandle` 或共享 Repository 暴露
- 每个 VM 绑定同一 NavBackStackEntry（通过 `koinViewModel` scope）
- 关键函数迁移映射见 issue #296 表格

#### #295 乐观更新
- 方案 A：通用 `OptimisticStateManager`
- `conversation: StateFlow<Conversation>` = `combine(dbFlow, optimisticFlow)`
- 13 个写操作改造，失败回滚 + Toast

#### #294 静态分析
- `build.gradle.kts` 添加 detekt 插件
- `config/detekt.yml` 配置
- `.editorconfig` 添加 ktlint section
- CI workflow 添加检查步骤

#### #292 i18n
- 93 处 `Text("...")` → `Text(stringResource(R.string.xxx))`
- 新增 string keys 到 `values/strings.xml` 和 `values-en/strings.xml`
- 按文件分批，子代理并行

#### #290 DI 统一
- `koinInject` 51 处 → 迁移到 VM 构造函数注入
- `new` 32 处 → 迁移到 Koin module 注册
- `Mutex()` 等可保留在 VM 内（非业务依赖）

#### #287 弹窗滚动
- 72 个 `Column` → `Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))`
- 操作按钮固定在滚动区外

#### #276 测试
- `ChatVM`、`AssistantDetailVM` 集成测试（app/src/test/）
- 核心 Composable UI 测试（app/src/androidTest/）
- 优先覆盖 `updateSettings` 等数据写入路径

#### #268 状态收集
- `ChatPage.kt:135-181` 20 处顶层 `collectAsStateWithLifecycle` 下推
- TopBar 只收集 `conversation.title`（derivedStateOf）
- ChatInput 只收集 `chatSuggestions`/`customSystemPrompt`
- gitStatus/gitDiff/hook 状态下推到对应 Sheet/Drawer

#### #267 DataStore 写入
- 废弃 `updateSettings(Settings)`，改字段级 transform
- 新增 `updateDisplaySetting { it.copy(...) }` 等方法
- Slider 用本地缓冲 + `onValueChangeFinished` 提交

## 依赖关系

```
#296(拆分ChatVM) ──→ #295(乐观更新)
              ──→ #268(状态收集) [交叉，可协调]
#267(DataStore) ──→ #295(乐观更新) [失败回滚交叉]
#294(静态分析) ──→ 独立
#292(i18n)    ──→ 独立
#290(DI)      ──→ 独立
#287(弹窗)    ──→ 独立
#276(测试)    ──→ 依赖其他 issue 代码稳定后补充
```

## 风险与缓解

1. **范围过大导致中途质量下降**：分层推进，每层完成后验证再进入下一层
2. **#296/#295 大重构引入回归**：行为不变前提下拆分，每步编译验证
3. **#294 静态分析可能暴露大量历史问题**：先配置基线，不阻塞现有构建
4. **子代理并行冲突**：不同子代理操作不同文件集，最后由主代理整合编译
