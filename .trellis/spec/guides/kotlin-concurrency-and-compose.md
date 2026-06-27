# Kotlin 协程与 Compose 常见陷阱

## 1. 协程 `launch` 与取消的竞态

### 问题
在 `finally { scope.cancel() }` 之后，已 `launch` 的协程仍可能执行回调：

```kotlin
// Wrong: 竞态条件
finally {
    scope.cancel()
}
// 已 launch 的协程可能在 cancel 后仍执行 cb()
scope.launch { cb(messages) }
```

### 正确做法
在 launch 前和 launch 块内检查 `isActive`：

```kotlin
// Correct: 双重检查
if (scope.isActive) {
    scope.launch {
        if (!isActive) return@launch
        cb(messages)
    }
}
```

### 检查清单
- [ ] `launch { cb() }` 前检查 `scope.isActive`
- [ ] launch 块内开头检查 `isActive`
- [ ] 协程回调中避免操作已取消的 scope

---

## 2. Compose `remember` 依赖键完整

### 问题
`remember` 的 keys 参数**只列出部分依赖**，但块内读了更多 state，导致 UI 使用陈旧数据：

```kotlin
// Wrong: 漏了 settings.value.providers
remember(settings.value.favoriteModels, providers, modelType) {
    // 块内实际读取了 settings.value.providers
    findModelById(...)
    findProvider(...)
}
```

### 正确做法
`remember` 的 keys 必须包含块内所有读取的 state 变量：

```kotlin
// Correct: 包含所有读取的依赖
remember(
    settings.value.favoriteModels,
    settings.value.providers,  // 加上
    providers,
    modelType
) { ... }
```

### 检查清单
- [ ] `remember` 的 keys 包含块内所有 `.*value.*` 读取
- [ ] 使用 `remember(..., settings.value.providers)` 而非只传 `providers`

---

## 3. ChatService 状态更新：CAS vs 直接赋值

### 问题
主生成路径用 `session.state.value = newState` 直接赋值，子代理进度用 `compareAndSet` CAS 更新，存在写覆盖。

### 正确做法
统一使用 CAS + 重试模式：

```kotlin
// Correct: CAS 原子更新
fun commitConversationState(transform: (ConversationState) -> ConversationState) {
    while (true) {
        val current = session.state.value
        val updated = transform(current)
        if (session.state.compareAndSet(current, updated)) return
        // CAS 失败 → 重试（最多 N 次）
    }
}
```

### 检查清单
- [ ] 同一 MutableStateFlow 的写入路径是否统一（所有写入用 CAS，不用直接 `= `）
- [ ] CAS 是否有最大重试次数保护
