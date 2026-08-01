# 处理开放 issues #199/#200/#201

## Goal

并行处理仓库 3 个开放 issue，各自独立交付后合并集成验收：

- **#199**（bug）：`workspace_read_file` 无法读取 `/upload`（缺 knownMount），与系统提示及注入 path 矛盾。
- **#200**（feat）：全局偏好控制上传附件注入方式，默认「仅路径 PATH_ONLY」不注入正文，可选「全文 FULL_BODY」。
- **#201**（bug）：关闭预设/世界书/记忆后 system 消息仍残留旧「模式注入」，源于 `assistant.modeInjectionIds` 直连绑定未随 #73/#182 迁移清理，且聊天页无独立注入开关。

父任务持有三个子任务的集成验收与最终设备安装。

## Requirements

- 3 个子任务分别对应 #199 / #200 / #201，规划见各子任务 prd/design/implement。
- 子任务实现并行，但 **#200 默认 PATH_ONLY 依赖 #199 的 `/upload` knownMount 可读**（产品依赖，代码编译不阻塞）。
- 提交分支统一 `release/rikka-arsucar`。
- 生产代码改动完成后执行安装验收（`.\gradlew --no-daemon :app:installDebug`）。

## Acceptance Criteria

- [ ] #199：工作区 READY 时 `workspace_read_file("/upload/<name>")` 可读；工具描述声明 `/upload` 只读挂载；fork 新 UUID 路径可读；`/skills`、`/skills_private` 行为不变。
- [ ] #200：设置→偏好/通用新增「上传附件注入方式」（默认 PATH_ONLY）；仅路径时上下文不含正文含 name/path stub；全文模式与现网一致；仅路径+无工作区回退全文；本地化文案；单测覆盖两模式与升级缺省。
- [ ] #201：关闭预设/世界书/记忆后无残留注入；聊天页扩展面板新增「独立注入」开关且立即生效（含对话级绑定）；迁移清理与已迁移 preset entries 重复的 `assistant.modeInjectionIds` 直连，消除双路径。
- [ ] 三个子任务单测通过；生产代码最终一次 Gradle 编译 + 相关 JVM 单测通过。
- [ ] app 改动完成后安装到设备；安装失败按 AGENTS.md 固定端口重连一次。
- [ ] 各 issue 关闭前发布中英文交付评论（解决点/验证/定位/已知边界）。

## Notes

- 父任务不承担子任务内部实现，仅做需求归一与最终集成验收。
- 上游 #34/#37 是 `/skills` knownMount 同模式修复的历史参考。
