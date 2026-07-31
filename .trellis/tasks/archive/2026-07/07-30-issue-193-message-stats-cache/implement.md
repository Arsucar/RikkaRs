# Implement: #193 message_stats + Stats 缓存

## Checklist

1. [x] Entity：`MessageStatsEntity`、`MessageStatsDailyEntity`
2. [x] DAO：`MessageStatsDAO`（upsert、sum、daily group、delete、backfill 辅助）
3. [x] 纯函数：`computeMessageStats(conversationId, nodes)`（可 JVM 单测）
4. [x] `AppDatabase` v45→46 + entities + `messageStatsDao()`
5. [x] `Migration_45_46` CREATE TABLE；`DataSourceModule.addMigrations`
6. [x] `ConversationRepository`：`saveMessageNodes` / `syncMessageNodes` 末尾 upsert 汇总
7. [x] Backfill：`ensureMessageStatsBackfill()`（SQL 或按会话），Stats 加载前调用；手动 refresh 可 force
8. [x] `StatsVM`：并行读汇总、去 delay、refresh/lastUpdated/isRefreshing
9. [x] `StatsPage`：TopBar 刷新 + 上次更新文案
10. [x] strings en + zh
11. [x] DI：DAO single（若需要）
12. [x] 单测：compute +（可选）DAO 聚合语义
13. [x] 验证：`.\gradlew --no-daemon :app:compileDebugKotlin` + 聚焦 test；最后 `installDebug`

## Validation

```powershell
.\gradlew --no-daemon :app:compileDebugKotlin
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*MessageStats*"
# 代码冻结后
.\gradlew --no-daemon :app:installDebug
```

## Review gates

- 写路径与 conversation 事务同事务，失败整单回滚
- 聚合语义与旧 `getTokenStats` / `getMessageCountPerDay` 一致（全 messages，user role 热力图）
- 删除会话后汇总无残留（CASCADE）
- UI 刷新不盖住已有数据

## Rollback

- 回退提交；必要时 DROP 两表并把 version 策略按发布说明处理
