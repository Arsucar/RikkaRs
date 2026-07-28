# 修复 #184 WebDAV/S3 备份恢复非原子性与 dummy 设置污染

## Goal

WebDAV/S3 备份恢复存在四个叠加缺陷：(1) 直接覆写在用数据库、非原子写入、未先 close/checkpoint，中途失败损坏 db；(2) 外键校验后置于覆写之后，坏包已污染正式库无法回滚；(3) settings 先生效导致新设置+旧数据库状态撕裂，且缺 config.items 开关校验；(4) BackupArchive.create 取 settingsFlow.value 可能打入 Settings.dummy() 占位设置。目标：恢复原子化（临时目录解包→校验→close→原子 rename→settings 最后应用→重启），失败保留原库回退，备份内容始终为真实设置。

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
