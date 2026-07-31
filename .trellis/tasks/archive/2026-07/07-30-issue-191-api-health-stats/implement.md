# Implement plan: #191 API health

## Order

1. Entity + enums + DAO + Migration_46_47 + AppDatabase v47 + DI
2. ApiCallErrorClassifier + ApiCallHealthCompute pure functions + unit tests
3. ApiCallRecorder
4. Wire GenerationHandler.generateInternal
5. Wire ChatService background generateText (title/suggestion) optionally via recorder
6. StatsVM api health state + time range
7. StatsPage UI card + detail sheet
8. strings en + zh
9. Focused unit tests only (no full gradle as implement agent)

## Validation

- Unit: ApiCallErrorClassifierTest, ApiCallHealthComputeTest
- Self-check: compile-ready imports, no message_stats regression
- Main session: compile / install

## Rollback

- Drop Migration_46_47, revert version to 46, remove entity/dao/wiring
