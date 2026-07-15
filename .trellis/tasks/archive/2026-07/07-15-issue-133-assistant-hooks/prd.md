# Issue #133 助手级多 Hook 系统

## Goal

在助手配置中提供可排序、可扩展的多 Hook 系统；本期在最终助手回复成功后独立调用模型，严格解析单一决策并通过 #132 原子 API 添加既有对话标签，同时提供最小化、可审计的运行历史。

## Requirements

- `Assistant` 持久化有序 Hook 列表，旧配置默认空列表；每项包含稳定 ID、模型、prompt、trigger、action config、enabled 和 configVersion。
- Trigger、dispatcher、executor、strict parser、action registry 分层；本期仅实现最终回复成功 trigger 与添加既有标签 action。
- 使用持久 logical turn 和唯一约束判断跨工具审批、ToolContinuation、重生成、取消、失败与空文本的真正最终成功，禁止依赖 `generationDoneFlow`。
- 模型调用复用标题生成的 provider 直调模式，独立上下文，主生成不等待；同 run 串行、失败继续、30 秒超时、无重试。
- 输出必须是字段集合恰为 `decision/tagId/reason` 的 JSON；apply 仅允许冻结 allowedTagIds 中且当前存在的 tagId。
- Room 保存 run/execution 最小审计元数据与状态机；消息快照、完整 prompt/config 和原始输出只存在任务内存，不进入数据库或备份。
- 超时、取消和晚到结果通过 lease/token 条件更新防止标签写入或终态覆盖；进程重启将遗留运行态标为 INTERRUPTED，不恢复调用。
- 助手详情提供 Hook CRUD/启停/排序；右抽屉提供按 run 分组的历史、执行详情和脱敏错误。
- #133 严格依赖 #132 的词表与原子标签 API，不创建、删除、归档或隐式创建标签。

## Acceptance Criteria

- [ ] 旧 Assistant JSON 兼容且 hooks 缺省为空；CRUD、顺序和 configVersion 持久化正确。
- [ ] 普通发送、多步工具、人工审批续接、重生成、取消、失败、空文本和零匹配 Hook 均满足 exactly-once gate。
- [ ] Provider 独立调用、串行执行、失败继续、30 秒超时和主生成不等待均有测试。
- [ ] Strict parser 拒绝 fence、附文、数组、缺失/额外字段、坏类型和非法 tagId；reason 500 code point 截断可审计。
- [ ] 超时先使 token 失效，任何晚到解析/action 结果均不能写标签或覆盖 FAILED 终态。
- [ ] 添加标签只调用 #132 原子 API，标签已存在为合法 no-op，未知/删除标签不创建替代资源。
- [ ] 状态聚合、启动 INTERRUPTED、删除/分支/配置/模型/tag 变化、清理和备份隐私符合 issue 约束。
- [ ] 右抽屉展示多 run/execution、删除引用、脱敏错误和截断提示；Room Flow 驱动更新且不依赖 `generationDoneFlow`。
- [ ] 端到端最终 assistant 文本产生唯一 run，多 Hook 串行 apply/skip，标签和历史同步更新，主生成无回归。
- [ ] 目标测试、Kotlin 编译和可用设备 Debug 安装通过。

## Notes

- Issue：<https://github.com/Arsucar/RikkaRs/issues/133>
- `generationDoneFlow`、现有 message transformers 和主 `GenerationHandler` 均不是 Hook 成功触发器。
