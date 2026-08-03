# issue-215 design

## 边界

| 层 | 改动 |
|----|------|
| `ExperimentalFeatureRegistry` + FeatureSpec + Scope + resolve | 新文件 |
| Settings / PreferencesStore | experimentalFeatures Map + 读写 + 旧键迁移 |
| Assistant | experimentalFeatureOverrides Map |
| Screen / RouteActivity | Experiments + AssistantExperiments |
| SettingPage / AssistantDetailPage | 入口 |
| 新 Compose 页（可共用 list UI） | 警告 + Switch + 确认 |
| 消费点 | keepalive, checkpoint, variable_system |
| 删除散落 UI | Notification page, AssistantMemoryPage |

## 契约

### FeatureSpec
```kotlin
enum class ExperimentalFeatureScope { Global, Assistant }
enum class ExperimentalFeatureStatus { Experimental, Beta, Stable }

data class FeatureSpec(
  val id: String,
  val titleRes: Int,
  val descriptionRes: Int,
  val scope: ExperimentalFeatureScope,
  val defaultEnabled: Boolean = false,
  val status: ExperimentalFeatureStatus = ExperimentalFeatureStatus.Experimental,
)

object ExperimentalFeatureRegistry {
  val all: List<FeatureSpec>
  fun get(id: String): FeatureSpec?
  fun byScope(scope): List<FeatureSpec>
}

fun resolveExperimentalFeature(
  id: String,
  settings: Settings,
  assistant: Assistant? = null,
): Boolean
```

### 注册项（本轮）
| id | scope | bridges |
|----|-------|---------|
| chat_keepalive | Global | enableKeepAliveNotification |
| checkpoint_cache | Global | enableCheckpointCache |
| variable_system | Assistant | enableVariableSystem |

### 迁移策略
1. **读路径兼容（必须）**：`resolve` 若 Map 无 key，回退读旧布尔字段（若存在），避免丢用户已开状态。
2. **写路径**：UI 只写 Map（+ 同步旧布尔一版双写或只写 Map 并在 resolve 只读 Map+旧）。
3. **推荐双写一个版本**：开启实验项时 `experimentalFeatures[id]=true` 且旧字段 true，便于旧代码路径；下一版本再删旧字段。
4. **本轮目标**：消费点全部走 resolve；旧字段保留作 bridge，散落 UI 删除。

### checkpoint interval
- 保留 `checkpointStepInterval` 在 Settings。
- UI：在 Global 实验页，当 checkpoint_cache 开启时显示间隔 chips（从通知页挪来）。

### 原子写
```kotlin
settingsStore.updateExperimentalFeature(id, enabled) // mutex + edit only experimental_features key (+ bridge keys)
assistantStore / updateAssistantConfig with overrides map transform
```

### 导航
```kotlin
data object Experiments : Screen
data class AssistantExperiments(val id: String) : Screen
```

## 数据流

```
Registry → ExperimentsPage lists specs by scope
Toggle → confirm → write Map (+ bridge) → settingsFlow/assistant flow
Consumer → resolve(id, settings, assistant)
```

## 回滚

- 恢复散落 UI + 旧字段直读；删注册表页。

## 风险

- 双写不一致：resolve 优先 Map，再 bridge。
- 字符串资源重复：实验页用新 key，旧 key 可复用 desc。
