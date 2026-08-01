# 父任务执行计划：处理开放 issues #199/#200/#201

## 集成顺序

1. 并行派发 3 个 trellis-implement 子代理分别实现 #199 / #200 / #201（只有最后一个检查子代理允许 Gradle 编译）。
2. 主代理审查各 diff，合并冲突（尤其 #199 与 #200 都触及 DocumentAsPromptTransformer 上下文传递时）。
3. 最终一次 Gradle：资源处理 + Kotlin 编译 + 相关 JVM 单测（带 `--no-daemon`）。
4. 安装验收到设备（失败重连 100.99.129.110:5555 一次）。
5. 关闭 issue 前发布中英交付评论。

## 各子任务验收命令

- #199 相关单测：`WorkspaceKnownMountTest`（新增 `/upload` 映射）、`RootfsPathResolutionTest`。
- #200 相关单测：`DocumentAsPromptTransformerTest`（两模式）、偏好序列化测试。
- #201 相关单测：`PromptInjectionTransformerTest`、迁移持久化测试。

## 集成验收清单

- [ ] 三份 diff 均无编译错误、无数据丢失、无行为回归。
- [ ] 一次合并 Gradle 调用内通过编译 + 单测。
- [ ] `installDebug` 成功。
- [ ] 三个 issue 均发布中英交付评论。
