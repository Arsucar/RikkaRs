# Implementation Plan

1. 完成 #68 审计，补跑 Daily Build，核验 nightly prerelease，评论并关闭 issue。
2. 完成 #102 代码定位、手势修复、静态审查和设备交互验收。
3. 完成 #104 运行时设计、实现、聚焦单测和并发/缓存边界审查。
4. 由最后一个检查阶段运行聚焦测试、`test`/`lint`（按风险）和 `:app:installDebug`。
5. 更新必要的 `.trellis/spec/`、CHANGELOG（若用户可见行为需要记录）、提交、归档子任务。
6. 查询 `gh issue list --state open`，确认本轮 issue 全部关闭后归档父任务。

