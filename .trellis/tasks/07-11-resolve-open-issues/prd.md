# 处理全部开放 GitHub Issues

## Goal

完成并关闭仓库当前全部开放 GitHub issue，同时保留可追溯的实现、验证和发布证据。

## Confirmed Facts

- 2026-07-11 盘点到 3 个开放 issue：#68、#102、#104。
- 当前开发分支为 `release/rikka-arsucar`，开始时工作树干净。
- 三个 issue 可独立规划、实现和验收，因此使用一个父任务和三个子任务。

## Requirements

- #68：审计上游同步和双 CI workflow，补齐缺失的端到端验证后关闭。
- #102：修复 Markdown 表格水平滚动时误触右滑抽屉的问题，并安装到设备验收。
- #104：实现子代理上下文缓存、失败/中断保留和基于 context id 的后续复用，并覆盖 TTL/LRU/并发等边界。
- 每个 issue 只有在其验收条件有直接证据时才能关闭。
- app 模块改动完成后执行 `adb devices` 和 `./gradlew --no-daemon :app:installDebug`；无可用设备时至少完成编译并说明。

## Acceptance Criteria

- [ ] 子任务 #68、#102、#104 均完成并归档。
- [ ] 所有相关自动化检查通过，app 改动完成安装验收或记录无设备证据。
- [ ] GitHub 上不再存在本轮范围内的开放 issue。
- [ ] 代码、Trellis 记录、GitHub issue 评论和关闭状态相互一致。

## Progress

- [x] #68 已完成 Daily Build/nightly 验收并关闭、归档。
- [x] #104 已完成 91 个 Subagent 测试、编译、安装并关闭、归档。
- [ ] #102 已实现、测试、编译、安装并推送；待设备解锁后完成表格/抽屉真机交互验收与关闭。

## Out of Scope

- 处理执行期间新创建、且与本轮三个 issue 无关的 GitHub issue。
- 默认运行 `connectedDebugAndroidTest`。
