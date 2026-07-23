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

---

## 4. Compose 指针手势的受限挂起与多指隔离

### 受限挂起作用域

`awaitEachGesture` / `AwaitPointerEventScope` 是受限挂起作用域。不要在其中调用普通 suspend 包装函数；即使包装函数内部最终调用的是 `awaitPointerEvent`，也会编译失败。需要跨事件保持资源时，使用非 suspend acquire，并在手势块内直接 `try/finally` release。

### 多指隔离

父子手势通过共享状态协商所有权时，不要只用全局 Boolean 或总引用计数。排除状态应按起始 `PointerId` 登记和查询，否则表格内的第二根手指会错误屏蔽从表格外开始的第一根手指手势。

### 检查清单

- [ ] `AwaitPointerEventScope` 内只调用允许的接收者挂起 API
- [ ] acquire/release 在同一手势块内通过 `try/finally` 成对清理
- [ ] 手势所有权状态按起始 pointer ID 隔离
- [ ] 多指、取消和重复 release 均有回归测试

---

## 5. 共享 Kotlin 文件 facade 避免 eager 静态初始化

### 问题

Kotlin 文件中的非 `const` 顶层字段会进入生成的 `*Kt.<clinit>`。如果共享工具文件在初始化
`Regex`、formatter 或其他运行时对象时抛错，首次访问会得到 `ExceptionInInitializerError`；
同一进程后续调用该文件中的任意无关函数都会变成 `NoClassDefFoundError`。

```kotlin
// Wrong: 所有 StringUtilsKt 函数都依赖这个初始化成功
private val placeholderPattern = Regex("\\{([^{}]+)}")
```

### 正确做法

共享工具 facade 保持无运行时静态初始化。对象只在需要它的函数内、完成早退判断后构造；
若确实需要缓存，放入职责单一且不会连带其他工具函数的 holder/facade。

```kotlin
// Correct: StringUtilsKt 不再生成有行为的 <clinit>
fun String.applyPlaceholders(vararg values: Pair<String, String>): String {
    if (values.isEmpty()) return this
    return Regex("\\{([^{}]+)}").replace(this) { match -> /* ... */ }
}
```

### 检查清单

- [ ] 共享 `*Utils.kt` 文件不含构造运行时对象的顶层非 `const val`
- [ ] 出现 `ExceptionInInitializerError` 后又出现同一类的 `NoClassDefFoundError` 时，检查最初 cause 和生成类的 `<clinit>`
- [ ] 根修后用 `javap -c -p <Class>` 或等价字节码工具确认目标 facade 不再包含有行为的静态初始化块
- [ ] 行为测试覆盖迁移前的函数契约；不要只在单个 Compose 调用点吞掉 linkage error
