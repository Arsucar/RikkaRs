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
- [ ] 检查 `api` / `implementation` 泄漏与不必要的模块依赖
- [ ] 评估 kapt → KSP 或其他插件迁移，记录迁移成本与实测收益
- [ ] 移除确认无用的插件、依赖和生成任务
- [ ] 每个候选用同一 profile 方法对比，保留改善项并恢复回退项

验证：构建成功，相关测试通过，profile 结果可比。

## 阶段 2：P1 运行时性能
- [ ] 检查 Compose 稳定性、重组范围、列表 key、状态读取和重复分配
- [ ] 检查 Room N+1、缺失索引、过宽查询和主线程数据库访问
- [ ] 审查 OkHttp 拦截器链、连接复用、超时和重复客户端
- [ ] 审查 Coil 请求复用、内存/磁盘策略和不必要的图像转换
- [ ] 定位主线程 IO、大对象/JSON 解析、重复 DataStore/Flow 收集
- [ ] 只实施有代码证据或可执行验证的候选，并记录验证边界

验证：聚焦测试通过；app 功能改动按仓库规则安装到设备验收。

## 阶段 3：P2 代码质量与模块边界
- [ ] 统计并修复仓库已有 detekt/ktlint 任务中的可操作 warning
- [ ] 搜索并删除确认不可达、未引用或已废弃的代码
- [ ] 合并重复实现，但不为少量重复引入不必要抽象
- [ ] 修正会扩大编译或运行成本的模块耦合
- [ ] 为被重构的关键逻辑补充聚焦测试

验证：行为兼容、测试通过；工具未配置时记录 N/A，不为指标新增工具。

## 阶段 4：P3 资源、R8 与启动
- [ ] 删除经资源引用与构建验证确认未使用的资源
- [ ] 审查 R8/ProGuard keep 规则，避免过度 keep 与反射破坏
- [ ] 评估大位图压缩/矢量化、资源收缩及打包重复项
- [ ] 检查 baseline profile 和启动热路径；仅在可正确构建、安装和验证时修改

验证：同一 APK 产物精确字节数增幅不超过 5%，release 专属改动使用可用的同变体验证。

## 阶段 5：P4 测试加固
- [ ] 补充能捕获优化回归的 DAO、序列化、转换器或 ViewModel 单元测试
- [ ] 运行受影响模块测试
- [ ] 不修改 CI workflow，不默认运行 connectedDebugAndroidTest

验证：最终 JVM 测试全部通过。

## 阶段 6：收尾
- [ ] 按测量协议重新采集三次冷构建、APK 和静态分析数据
- [ ] 在一次 Gradle 调用中尽量合并最终资源处理、Kotlin 编译和 JVM 测试
- [ ] 执行最终 assembleDebug、test 和适用的设备安装验收
- [ ] 完成 `OPTIMIZATION_NOTES.md`（存在失败、跳过或验证缺口时）
- [ ] 生成 `OPTIMIZATION_REPORT.md`
- [ ] 最终提交：`docs: add overnight optimization report`

验证：报告明确区分“达到目标”“改善但未达标”“N/A”“未验证”，不隐藏失败或波动。
