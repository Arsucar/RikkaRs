# Implement — 处理所有 open issue 与 PR

## 执行计划

### 第一层：已有 PR 审查合并

#### Step 1.1: PR #264 README（低风险文档）
- [ ] 审查 diff（补充 pnpm 说明）
- [ ] 合并 PR #264
- 验证：无编译影响

#### Step 1.2: PR #300 Magic Number（#293）
- [ ] 审查 13 处常量提取
- [ ] 验证命名语义清晰、位置合理
- [ ] 合并 PR #300
- [ ] 关闭 issue #293（中文+英文评论+逐条勾选）
- 验证：`--no-daemon :app:compileDebugKotlin`

#### Step 1.3: PR #297 Modifier 顺序（#289）
- [ ] 审查 5 处 padding/clickable 顺序
- [ ] 验证视觉无回归
- [ ] 合并 PR #297
- [ ] 关闭 issue #289
- 验证：编译

#### Step 1.4: PR #298 SharingStarted（#291）
- [ ] 审查 22 处 Eagerly→WhileSubscribed(5000)
- [ ] 验证 updateState 更新检查场景
- [ ] 合并 PR #298
- [ ] 关闭 issue #291
- 验证：编译

#### Step 1.5: PR #299 ConversationEntity 索引（#288）
- [ ] 审查 Migration_52_53 + Entity + DAO + 测试
- [ ] 验证索引名与 Room 自动生成一致
- [ ] 验证迁移链衔接（51→52→53）
- [ ] 合并 PR #299
- [ ] 关闭 issue #288
- 验证：编译 + androidTest 源码编译

#### Step 1.6: PR #283 代码鲁棒性
- [ ] 审查多文件降级策略
- [ ] 验证每处不掩盖真实错误
- [ ] 合并 PR #283
- 验证：编译

### 第二层：无 PR bug 类 issue 实现

#### Step 2.1: #267 DataStore 写入统一
- [ ] 在 SettingsStore 添加字段级 transform 方法
- [ ] 改造 8 个 VM 的 updateSettings
- [ ] 改造 SearchPicker
- [ ] 改造 Slider（本地缓冲 + onValueChangeFinished）
- 验证：编译 + 聚焦测试

#### Step 2.2: #268 ChatPage 状态收集
- [ ] 下推 20 处 collectAsStateWithLifecycle
- [ ] TopBar 用 derivedStateOf 提取 title
- [ ] Sheet/Drawer 状态内部收集
- 验证：编译

#### Step 2.3: #290 DI 统一
- [ ] 迁移 koinInject 51 处到 VM 构造函数
- [ ] 迁移手动 new 32 处到 Koin module
- 验证：编译

#### Step 2.4: #294 静态分析工具
- [ ] 添加 detekt 插件到 build.gradle.kts
- [ ] 创建 config/detekt.yml
- [ ] 更新 .editorconfig
- [ ] CI workflow 添加检查
- 验证：`--no-daemon detekt`

#### Step 2.5: #287 弹窗滚动支持
- [ ] 批量给 72 个 Column 添加 verticalScroll + heightIn(max)
- [ ] 操作按钮固定滚动区外
- [ ] 子代理并行处理（按文件分组）
- 验证：编译

#### Step 2.6: #292 UI 硬编码字符串 i18n
- [ ] 新增 string keys
- [ ] 93 处 Text("...") → stringResource
- [ ] 子代理并行处理（按文件分组）
- 验证：编译 + 资源处理

#### Step 2.7: #276 测试覆盖
- [ ] ChatVM 集成测试
- [ ] AssistantDetailVM 集成测试
- [ ] 核心 Composable UI 测试
- 验证：`--no-daemon test`

### 第三层：无 PR enhancement 实现

#### Step 3.1: #296 拆分 ChatVM
- [ ] 创建 ChatGitVM、ChatHookVM、ChatMemoryTableVM、ChatDraftVM、ChatContextVM
- [ ] 迁移对应职责函数
- [ ] 共享 conversation StateFlow 机制
- [ ] 更新 ChatPage 引用
- 验证：编译 + 功能验证

#### Step 3.2: #295 乐观更新机制
- [ ] 设计 OptimisticStateManager
- [ ] 改造 13 个写操作
- [ ] 失败回滚 + Toast
- [ ] 单元测试
- 验证：编译 + 测试

### 第四层：最终验证

#### Step 4.1: 全量编译
- [ ] `--no-daemon :app:assembleDebug`
- [ ] 处理编译错误

#### Step 4.2: 安装验证
- [ ] adb devices / connect
- [ ] `--no-daemon :app:installDebug`
- [ ] 核心功能人工验收

## 验证命令

```bash
# 编译
./gradlew --no-daemon :app:compileDebugKotlin

# 完整构建
./gradlew --no-daemon :app:assembleDebug

# 单元测试
./gradlew --no-daemon test

# 安装
./gradlew --no-daemon :app:installDebug

# detekt（#294 完成后）
./gradlew --no-daemon detekt
```

## Review Gates

- 第一层完成后：全量编译验证，确认 6 个 PR 合并无冲突
- 第二层每个 step 后：编译验证
- 第三层每个 step 后：编译 + 测试验证
- 最终：安装到设备

## Rollback Points

- 每个 PR 合并前：当前 commit 是回滚点
- 每个 issue 实现前：当前 commit 是回滚点
- 大重构（#296/#295）前：创建分支/tag 标记回滚点
