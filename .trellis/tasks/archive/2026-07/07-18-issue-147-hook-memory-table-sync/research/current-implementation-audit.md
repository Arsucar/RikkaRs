# 当前实现审计摘要

- Hook Registry/Dispatcher 已按 Action handler 映射，具有 final-success、logical-turn exactly-once、claim/lease、30 秒 timeout 与 history；无需新 scheduler。
- 标签语义仍硬编码在 `HookActionContext.allowedTagIds`、Frozen request/prompt/parser/result、ChatService cast、execution columns 和 UI。
- MemoryTable Repository 写入会 snapshot/revision+1，但没有 expected revision CAS；tool apply_ops 在内存组装后单次 upsert，不能防并发覆盖。
- Handler 写表后 Dispatcher 再单独完成 execution，会存在“数据已写、history 失败”窗口；#147 必须建立同事务 commit contract。
- `memoryTableAutoSyncEnabled` 已持久化且默认 false，UI 仍是 disabled placeholder；V1 用作所有 memory-sync Hook 的全局 auto gate。
- #146 soft delete 是 target 生命周期依赖；Sync target selector 和 CAS 均必须排除/reject deleted document。
