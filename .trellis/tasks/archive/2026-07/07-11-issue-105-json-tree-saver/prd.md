# 修复日志详情 JsonTree Saver 崩溃

## Goal

修复日志详情打开时 `JsonTreeState.Saver` 返回不可存入 Bundle 的裸 Map 所导致的崩溃。

## Requirements

- Saver 只产出 Compose/Bundle 可保存的类型。
- 保存与恢复必须保留 JsonTree 展开状态。
- 不改变现有展开、折叠及日志详情交互。

## Acceptance Criteria

- [ ] 空状态和非空状态均可通过 Saver round-trip。
- [ ] 打开日志详情不再因 `{}` 状态抛出 `IllegalArgumentException`。
- [ ] 有单元测试覆盖保存/恢复回归。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
