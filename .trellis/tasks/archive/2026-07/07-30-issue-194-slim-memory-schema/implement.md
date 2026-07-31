# Implement: #194 slimSchemaForInjection

## Checklist

1. [x] 在 `MemoryTableInjectionTransformer.kt` 同文件（或邻近 util）新增 `slimSchemaForInjection`
2. [x] `buildMemoryTablePrompt` L161-162 改用 slim
3. [x] 单测：默认 schema / 空 / 非法 / 多表 / 无 primaryKey；体积 ≥40% 可选断言
4. [x] 现有 `MemoryTableInjectionTransformerTest` 回归

## Validation

```powershell
.\gradlew --no-daemon :app:testDebugUnitTest --tests "*MemoryTableInjection*"
```

Lightweight：无 design.md 亦可。
