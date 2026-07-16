# 处理当前新增开放 GitHub Issues

## Goal

处理当前确认的新增开放 GitHub Issues，完成实现、回归验证和交付记录；不重复处理已在本地完成的 #136。

## Requirements

- 本轮范围固定为当前开放的 #140 和 #141。
- #140：将助手记忆页主列表改为以已创建的 `MemoryTableDocument` 为主；加号打开“新增表格”流程，可选择 effective template 或创建私有/全局模板；整卡打开文档；删除仅删除文档；模板必须成功持久化后才能导航。
- #141：优化助手 Hook 列表与编辑页的信息架构；列表只保留启用开关，整卡进入编辑，删除移入低误触入口，摘要统一为“模型 · 动作类型”；编辑页按基础/运行/规则/动作分区，显示明确校验错误，限制长 prompt 占屏，并为未来多 action 类型保留扩展结构。
- 保留 #136 当前本地改动及其边界：只涉及会话归档移除，不影响助手归档；不得覆盖或回滚相关未提交文件。
- 每个 issue 独立完成代码、测试/编译验证和变更记录；若验证通过，再按仓库规范准备提交与 issue 交付评论。

## Acceptance Criteria

- [ ] #140 的记忆表列表、创建流程、文档打开和删除语义符合 issue 描述，且模板持久化失败时不会错误导航。
- [ ] #141 的 Hook 列表和编辑页满足信息架构、校验反馈、prompt 展示限制及 action 扩展要求。
- [ ] 为新增逻辑补充或更新适当的 JVM/UI/源码级测试，并完成必要的 `app` 模块编译与 lint 检查。
- [ ] 验证过程中不回归 #136 的会话归档移除改动，也不移除助手归档能力。
- [ ] 形成每个 issue 的实现、验证、已知边界和后续交付记录。

## Notes

- 已确认的当前开放 issue：#140、#141；#136 已在本地处理，本轮排除。
- 相关代码主要位于 `app` 模块的助手记忆页、Hook 页、ViewModel/Repository 及其测试。
- 待确认：本轮是否以当前清单作为固定范围，后续新建 issue 留到下一轮。

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
