# Design: Hook 编辑器单一标签管理

## 1. Architecture boundaries

| Layer | Responsibility |
|-------|----------------|
| UI `AssistantHooksPage` | Select 动作类型；标签管理只渲 allowlist；提示词折叠；列表摘要 |
| Model `ConversationHook` / `HookActionConfig` | 新 `ManageConversationTags`；旧 subtype 解码迁移 |
| Service `ManageConversationTagsHookAction` | prepare / parse / execute 统一入口 |
| `ConversationTagHookCommitter` | 事务内 multi-op add/remove + 终端状态 |
| `HookOutputParser` | 新严格 multi-op parser |
| History UI | 旧枚举别名 + 新类型展示；Sync Preview/Run/Retry 不动 |

**不改**：logical-turn 门控、lease、exactly-once run 创建、Sync handler 业务规则。

## 2. Config contract

### 2.1 New subtype

```kotlin
@Serializable
@SerialName("manage_conversation_tags")
data class ManageConversationTags(
    val allowedTagIds: Set<Uuid> = emptySet(),
) : HookActionConfig
```

```kotlin
enum class HookActionType {
    MANAGE_CONVERSATION_TAGS,
    SYNC_MEMORY_TABLE,
    // 历史展示别名（若需保留枚举常量供 DB 映射，见 2.3）
}
```

**推荐实现**：`HookActionType` 保留 `ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` 为 **deprecated 展示用常量**（或独立 `fun parseStoredActionType(String)` 映射表），避免历史 `valueOf` 崩溃；**注册表只注册** `MANAGE_CONVERSATION_TAGS` + `SYNC_MEMORY_TABLE`。

### 2.2 Load-time normalization

在单一边界（建议 `HookActionConfig` 自定义 serializer 或 Assistant hooks 加载后 `normalizeHookActionConfig`）：

| 输入 SerialName | 输出 |
|-----------------|------|
| `manage_conversation_tags` | 原样 |
| `add_conversation_tag` | `ManageConversationTags(allowedTagIds)` |
| `transition_conversation_tags` | `ManageConversationTags({add, remove})`；忽略 `filter` |
| `sync_memory_table` | 原样 |

保存路径只写规范化后的 config（旧类型不再 encode）。

### 2.3 configurationHash

`ManageConversationTags` material = sorted `allowedTagIds` joined by `,`（与旧 Add 相同排序规则）。

外层仍含 `actionType.name`：规范化后类型变为 `MANAGE_CONVERSATION_TAGS`，**hash 相对旧配置会变**（用户重新保存后 configVersion++）。接受：旧 in-flight Transition 的「config still current」在升级后 naturally 失效；Sync 字段未改则 hash 稳定。

### 2.4 actionType 映射

```kotlin
val HookActionConfig.actionType: HookActionType
    get() = when (this) {
        is ManageConversationTags -> MANAGE_CONVERSATION_TAGS
        is SyncMemoryTable -> SYNC_MEMORY_TABLE
        // 若旧类型在内存中短暂存在（未 normalize 前）：
        is AddConversationTag -> MANAGE_CONVERSATION_TAGS // 或仅 decode 层产出 Manage
        is TransitionConversationTags -> MANAGE_CONVERSATION_TAGS
    }
```

**优先**：decode 直接产出 `ManageConversationTags`，删除运行时对旧 class 的依赖；旧 class 仅保留在 serializer 兼容层（或迁移函数单元测试用）。

## 3. Model I/O protocol

### 3.1 Frozen request

```
FrozenHookModelRequest.ManageConversationTags(
  modelId, prompt, messageTextSnapshot, allowedTags: List<Pair<Uuid, String>>
)
```

注入 allowlist 名称；**不**注入 GitHub evidence 块；**不**固定 add/remove 标签名。

### 3.2 Strict JSON schema

Exact keys: `decision`, `operations`, `reason`.

```json
{"decision":"apply","operations":[{"op":"add","tagId":"<uuid>"},{"op":"remove","tagId":"<uuid>"}],"reason":"..."}
{"decision":"skip","operations":[],"reason":"..."}
```

Rules:

- `decision` ∈ `apply` | `skip`
- `skip` ⇒ `operations` 必须 `[]`
- `apply` ⇒ `operations` 非空且 `size ≤ MAX_TAG_OPS`（建议 8，放在 `HookRuntimeRules`）
- 每项 exact keys `op`, `tagId`；`op` ∈ `add`|`remove`；`tagId` 合法 UUID 字符串
- reason 截断规则复用 `truncateHookReason`
- raw 长度上限：复用/新增合理常量（可对齐 add 路径 trim 策略；transition 曾不 trim — **统一 trim 后 parse** 以降低模型噪声，在 parser 测试中固定）

### 3.3 Fail-closed (D3=C1)

After parse, before any write:

1. 每条 `tagId ∈ allowedTagIds` 否则整单 `TAG_NOT_ALLOWED`
2. 每条 tag 实体存在否则 `TAG_NOT_FOUND`
3. 可选：同一 tagId 同时 add+remove 或重复 op → `SCHEMA_MISMATCH` 或 `TAG_TRANSITION_CONFLICT`（实现选一种并单测固定）
4. 任一条失败 → **零写入**，execution SKIPPED/FAILED 按现有错误矩阵（越权/找不到 → SKIPPED 与旧 Add 对齐）

### 3.4 Commit

`commitManageTags(executionId, leaseToken, prepared, output, ops)`：

- 单 Room 事务
- 再校验 lease / conversation / source active / tags
- 按 ops 顺序：remove 再 add 或严格按模型顺序（**推荐按模型顺序**；若需容量交换场景，文档说明 remove 应先于 add 由提示词引导）
- 统计 changed count；0 → SKIPPED；>0 → SUCCESS
- audit：`operationSummaryJson` / `diffSummaryJson` 可扩展为 multi-op 列表；`tagId` 列可写 primary op 的第一个 tag 或 null（history 以 JSON 为准）

## 4. UI design

### 4.1 Editor sections

1. Basic（不变）
2. Runtime 摘要一行（触发 + 模型）
3. **Rules**：评估提示词 — `OutlinedTextField` 默认 **collapsed**（`expanded` state；collapsed 显示 1–2 行预览或「点击展开」）
4. Action：
   - `ExposedDropdownMenuBox` / 项目内既有 Select：`标签管理` | `同步记忆表`
   - 标签管理：allowlist chips + 说明 + 清理不可用
   - Sync：现有字段紧凑布局

### 4.2 State

```
selectedActionKind: ManageTags | Sync
manageConfig: ManageConversationTags
syncConfig: SyncMemoryTable
```

加载 hook 时：normalize actionConfig → 填入对应 state。

### 4.3 List card

- label: `hookActionLabelRes(MANAGE | SYNC)`
- manage 副行：allowlist 名称摘要（最多 N 个 + 溢出计数）
- 删除 Transition 专用 `remove → add · filter` 行

### 4.4 History

- `MANAGE_CONVERSATION_TAGS`：展示 decision / reason / multi-op 摘要（若有 JSON）
- 旧 `ADD_*` / `TRANSITION_*`：映射到标签管理文案或保留历史专用文案，**不崩溃**
- Sync 分支与按钮条件不变（仅 `SYNC_MEMORY_TABLE`）

## 5. DI / registry

- 注册 `ManageConversationTagsHookAction`
- 移除 `AddConversationTagHookAction` / `TransitionConversationTagsHookAction` 注册
- 可删除或内联旧类文件；证据检测代码若无其他引用可保留供未来/测试，但 **prepare 不再调用**

## 6. Compatibility & migration

| Surface | Strategy |
|---------|----------|
| Assistant settings JSON | decode 兼容旧 SerialName → Manage |
| Backup/import | 同上（共享 JsonInstant） |
| DB action_type | 读路径别名映射；写新类型名 |
| Audit JSON `action: transition_...` | history `runCatching` 已有；扩展 manage audit |
| Spec | 更新 `conversation-tags-and-hooks.md`：UI 契约、multi-op 协议、废弃 evidence-gated scenario 或标注 superseded |

## 7. Trade-offs

| Choice | Benefit | Cost |
|--------|---------|------|
| 去掉证据硬门控 | 与产品「策略在提示词」一致 | 旧 Transition 行为不等价 |
| 整单 fail-closed | 事务安全、审计清晰 | 模型混入一条坏 op 则全废 |
| 规范化写新类型 | 配置面干净 | 重新保存后 hash/version 变化 |
| multi-op 镜像 Sync | 团队已熟悉 strict ops | 比旧 Add 协议重 |

## 8. Rollback

- 配置：旧 JSON 仍可被 normalize 读取；若需回滚 app 版本，新 `manage_conversation_tags` 在旧 app 上 **decode 失败** — 发版说明：回滚 app 前需导出/注意 hooks；可选短期双写（**本 design 不双写**，接受单向迁移）。
- 代码：git revert；历史行旧枚举仍在。

## 9. Risks

1. Assistant 整包 JSON 解码失败若 serializer 写错 → 优先单测 golden legacy。
2. Exhaustive `when` 漏改 → 编译期发现，全模块搜 `HookActionType` / `AddConversationTag`。
3. 提示词默认文案需引导 multi-op 与 remove-before-add 容量场景。
4. `GITHUB_ISSUE_EVIDENCE_NOT_FOUND` 字符串/错误码可保留但路径不可达（或仅历史展示）。
