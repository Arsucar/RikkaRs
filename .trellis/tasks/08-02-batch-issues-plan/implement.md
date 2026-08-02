# batch-issues-plan implement（规划收尾，非生产实现）

## 本轮 Checklist

1. [x] 建父任务 + 5 子任务树
2. [x] 父 prd + design（矩阵）
3. [x] 子任务 #214/#218/#220/#219/#217-216 各 prd/design/implement
4. [ ] 用户审阅；确认实现启动顺序
5. [ ] **不要**对本父任务 `task.py start` 做生产实现；实现时对**子任务**分别 start

## 后续实现会话模板

```text
#214 优先：Active task .trellis/tasks/08-02-issue-214-reasoning-dialect
路径=审阅/验证/合入 PR #221（https://github.com/Arsucar/RikkaRs/pull/221），禁止从零重写。

下一实现：Active task .trellis/tasks/08-02-issue-218-preset-switch-jank
按该目录 prd/design/implement；AGENTS.md（--no-daemon、子代理、装设备）
```

## 验证（规划质量）

- 每子任务三文件存在
- 验收条件可测试
- 依赖与 #202/#215 已声明

## 工作量（规划本身）

已完成（本会话）。
