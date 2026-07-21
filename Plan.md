# 优化计划（按优先级）

## 阶段 0：基线与候选清单
- [x] 阅读 AGENTS.md、Trellis 指南及 Prompt.md / Plan.md / Implement.md / Documentation.md
- [x] 记录分支、commit、JDK、Gradle/AGP、主机环境和初始 `git status`
- [x] 运行 baseline assembleDebug、test 和仓库已有的 detekt/ktlint 任务（detekt/ktlint N/A）
- [x] 按 Prompt.md 测量三次冷构建并记录中位数
- [x] 选定 APK 产物，记录路径、变体、ABI、精确字节数和 SHA-256
- [x] 分析模块依赖图、插件、编译器任务和热点代码
- [x] 创建 `OPTIMIZATION_PLAN.md`，为每个候选记录证据、预期收益、风险和验证方式

验证：baseline 可复现；若 baseline 构建或测试本身失败，先记录为阻塞，不把历史失败归因于优化。

## 阶段 1：P0 构建性能
- [x] 审查根目录与各模块 Gradle 配置、插件应用和任务配置
- [x] 评估 configuration cache、build cache、parallel execution 和 JVM 参数
- [x] 检查 `api` / `implementation` 泄漏与不必要的模块依赖（B3-R：demotion 已落地；check 代理 `compileDebugKotlin`+`test`+`installDebug` 通过）
- [x] 评估 kapt → KSP 或其他插件迁移，记录迁移成本与实测收益（B5：仓库零 kapt，Room 已用 KSP → N/A 迁移）
- [x] 移除确认无用的插件、依赖和生成任务（B6：删除 settings 中 objectbox 死 resolution；catalog 孤儿仅记证不删）
- [x] 每个候选用同一 profile 方法对比，保留改善项并恢复回退项（B1 已 profile；B3-R 冷构建×3 留给阶段 6 / 最终 check；本轮无新增已验收 B* 墙钟数字）

验证：构建成功，相关测试通过，profile 结果可比。

## 阶段 2：P1 运行时性能
- [x] 检查 Compose 稳定性、重组范围、列表 key、状态读取和重复分配（R7：ChatDrawer 对实体 ID 补 key；suggestion 仅有 `List<String>` 且允许重复，保持位置身份；主列表/Paging 已有 key；R2/R3 高风险跳过见 NOTES）
- [x] 检查 Room N+1、缺失索引、过宽查询和主线程数据库访问（R5 强化审查：Conversation/GenMedia 缺索引但无 EXPLAIN 固件 → 不迁移；DAO 已用 light projection/paging；无 allowMainThreadQueries）
- [x] 审查 OkHttp 拦截器链、连接复用、超时和重复客户端（R8：主 client 单例合理；TTS/MCP 独立 client 有意隔离；SearchService.init 复用主 client → 不合并）
- [x] 审查 Coil 请求复用、内存/磁盘策略和不必要的图像转换（R7：singleton ImageLoader + OkHttp 共享；ZoomableAsyncImage remember ImageRequest；AIIcon 已 remember）
- [x] 定位主线程 IO、大对象/JSON 解析、重复 DataStore/Flow 收集（R1 `9e225402` 模板缓存；R4 `fe91967d` LRU JSON；R3 runBlocking highlight 高风险跳过；DataStore 多点 collectAsStateWithLifecycle 属页面级合理）
- [x] 只实施有代码证据或可执行验证的候选，并记录验证边界（本轮仅 R7 的实体 ID key 与 Coil request remember；suggestion 无稳定 ID 不加伪 key；无 frame-time 声明；check 代理 compile/install 已通过）

验证：聚焦测试通过；app 功能改动按仓库规则安装到设备验收。

## 阶段 3：P2 代码质量与模块边界
- [x] 统计并修复仓库已有 detekt/ktlint 任务中的可操作 warning（Q1-R：N/A — 全仓无 detekt/ktlint 插件/配置/任务；不为指标新增工具；Android Lint 另记）
- [x] 搜索并删除确认不可达、未引用或已废弃的代码（Q2-R：Kotlin 死码无安全整删；A4 删除零引用 `drawable/patreon.xml`；`@Deprecated` ToolCall/ToolResult 仍参与序列化迁移 → 保留）
- [x] 合并重复实现，但不为少量重复引入不必要抽象（Q3：MarkdownBlock→MarkdownNew 为门面非重复；TTS 多 client / Markdown 双路径审查后跳过合并）
- [x] 修正会扩大编译或运行成本的模块耦合（Q4：`api(project)` 仍为零；app 星型 `implementation` 图合理；B3-R 已 demote 三方 api；无进一步模块拆分）
- [x] 为被重构的关键逻辑补充聚焦测试（T1-R：本阶段无行为重构需新测；R1/R4/T2 已有测试；R7 仅实体 ID key/`remember` → 可选测跳过）

验证：行为兼容、测试通过；工具未配置时记录 N/A，不为指标新增工具。

## 阶段 4：P3 资源、R8 与启动
- [x] 删除经资源引用与构建验证确认未使用的资源（A4：全量 drawable 引用扫描 13 项，仅 `patreon.xml` 零引用已删；其余 donate/provider 图标有引用；check 代理 build/install 已验证）
- [x] 审查 R8/ProGuard keep 规则，避免过度 keep 与反射破坏（A3-R：`app/proguard-rules.pro` keep Serializable/jlatexmath/jackson/auth0 + dontwarn；release minify+shrink 已开；无 release 同变体证明 → 不改规则）
- [x] 评估大位图压缩/矢量化、资源收缩及打包重复项（A2-R：`useLegacyPackaging=true` + termux pickFirst 保留；`isShrinkResources=true` 仅 release；assets 大头为 jieba dict/banner PNG — 功能资产，不压；legacy packaging 高风险跳过）
- [x] 检查 baseline profile 和启动热路径；仅在可正确构建、安装和验证时修改（A5：`:app:baselineprofile` + 已生成 `baseline-prof.txt`/`startup-prof.txt` ~5.0MB；不重跑 generator/不改热路径 — 需设备 Macrobenchmark）

验证：同一 APK 产物精确字节数增幅不超过 5%，release 专属改动使用可用的同变体验证。

## 阶段 5：P4 测试加固
- [x] 补充能捕获优化回归的 DAO、序列化、转换器或 ViewModel 单元测试（T1-R：B3-R 为 Gradle classpath 无运行时行为测；R7 实体 ID key/Coil request identity 不强制新测；既有 R1/R4/T2 覆盖本轮已接受机制）
- [x] 运行受影响模块测试（T3：check 代理已跑 `:app:compileDebugKotlin`、仓库 `test` 960/0/0/3、`installDebug` 于 `ebc3de22`；触及 common/ai/search/highlight/app）
- [x] 不修改 CI workflow，不默认运行 connectedDebugAndroidTest（T4：`.github/workflows` 本轮零 diff；不默认 AndroidTest）

验证：最终 JVM 测试全部通过。

## 阶段 6：收尾
- [x] 按测量协议重新采集三次冷构建、APK 和静态分析数据（冷 252.749/251.119/250.572 中位 251.119s = baseline−2.19%；APK 82133445 B SHA `37754831…AAF56D`；detekt/ktlint N/A）
- [x] 在一次 Gradle 调用中尽量合并最终资源处理、Kotlin 编译和 JVM 测试（check：`compileDebugKotlin` + 独立 `test` 960/0/0/3；冷构建为独立 clean assemble×3）
- [x] 执行最终 assembleDebug、test 和适用的设备安装验收（assemble via cold; test PASS; installDebug PASS `ebc3de22`）
- [x] 完成 `OPTIMIZATION_NOTES.md`（存在失败、跳过或验证缺口时）
- [x] 生成 `OPTIMIZATION_REPORT.md`（已修订为 Plan resume 版：覆盖矩阵 + 诚实 −20% 未达标）
- [ ] 最终提交：待本轮 work commit 完成后更新（用户已明确要求修复并归档；不 push）

验证：报告明确区分“达到目标”“改善但未达标”“N/A”“未验证”，不隐藏失败或波动。
