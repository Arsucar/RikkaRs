# Research: 子代理记忆表注入 Code Review

- **Query**: Review uncommitted changes for subagent memory table injection feature
- **Scope**: internal (code review)
- **Date**: 2026-07-20

## 1. 功能概述

本特性允许子代理 Profile 选择父助手的记忆表（Memory Table Template），在子代理 spawn 时以只读方式注入到子代理的上下文中。核心目的是让子代理"看到"父助手的记忆表数据，但不授予写入权限。

### 变更文件清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `SubagentMemoryTableInjection.kt` | 新增 | 纯函数 resolveSubagentMemoryTableInjection，负责按 selectedTemplateIds 过滤 templates/documents |
| `SubagentMemoryTableInjectionTest.kt` | 新增 | 5 个单元测试 |
| `SubagentProfile.kt` :85 | 修改 | 新增 `injectedMemoryTableTemplateIds: Set<String>` 字段 |
| `SubagentHost.kt` :245-250, :385-397, :408-411, :616 | 修改 | 构造器接收 lambda 加载器；spawn 时按条件构建 transformers；传递给 runToCompletion |
| `DataSourceModule.kt` :284-322 | 修改 | DI 组装 lambda 加载器，串联 MemoryTableRepository → resolve → Transformer |
| `AssistantSubagentProfilePage.kt` :713-789 | 修改 | UI 增加记忆表多选 chips + 失效 id 提示 |
| `strings.xml` (3 locale) | 修改 | 新增 5 个字符串资源 |

## 2. 架构适配分析

### 与既有子代理系统的关系

- **SubagentProfile** 是序列化配置，新增字段有默认值 `emptySet()`，向后兼容 ✅
- **SubagentHost** 通过可选 lambda（`memoryTableInjectionLoader`）注入依赖，避免了直接依赖 Repository 层，DI 松耦合 ✅
- 复用既有 `MemoryTableInjectionTransformer`，与主聊天流程注入逻辑一致 ✅
- 纯函数 `resolveSubagentMemoryTableInjection` 被单独抽到新文件，便于独立测试 ✅

### 与主聊天流程（ChatService）的对比

主聊天流程（`ChatService.kt` :1583-1627）做了以下步骤：
1. 检查 settings + assistant 双重门控
2. 获取 effectiveTemplates
3. 获取 effectiveDocuments
4. **按 visibleTemplateIds 过滤** documents
5. **按 conversation.memoryTableIsolation 过滤** documents
6. 构建 MemoryTableInjectionTransformer

子代理流程（`DataSourceModule.kt` :289-321）做了：
1. 检查 settings + assistant 双重门控 ✅
2. 获取 effectiveTemplates ✅
3. 获取 effectiveDocuments（传入 conversationId）✅
4. 调用 resolveSubagentMemoryTableInjection（按 selectedTemplateIds 过滤）✅
5. 构建 MemoryTableInjectionTransformer ✅

**缺失：conversation isolation 过滤** ⚠️（见 Finding #2）

## 3. 代码审查发现

### Finding #1 — FQN 未收 import（Nit）

**文件**: `SubagentHost.kt` :250, :616

`me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer` 在构造器和 `runToCompletion` 参数中使用了全限定名而非 import。功能正确，但不符合仓库风格。

```kotlin
// SubagentHost.kt:250
) -> List<me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer>)? = null,

// SubagentHost.kt:616
inputTransformers: List<me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer> = emptyList(),
```

**严重度**: nit
**建议**: 在文件头添加 `import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer`，替换 FQN。

---

### Finding #2 — 缺少 conversation memory table isolation 过滤（Major）

**文件**: `DataSourceModule.kt` :296-298

主聊天流程中，当 `conversation.memoryTableIsolation == true` 时，只注入 CONVERSATION scope 的 documents，不注入 ASSISTANT scope 的。子代理 loader 直接调用 `getEffectiveDocuments(assistantId, conversationId)` 没有做 isolation 过滤：

```kotlin
// DataSourceModule.kt:296-298 — 当前代码
val documents = memoryTableRepository.getEffectiveDocuments(
    assistantId = assistantId,
    conversationId = conversationId?.toString(),
)
```

对比主聊天流程（`ChatService.kt` :1595-1600）：
```kotlin
if (conversation.memoryTableIsolation) {
    templateScopedDocuments.filter { it.scopeType == MemoryTableScopeType.CONVERSATION }
} else {
    templateScopedDocuments
}
```

**影响**: 当用户为某个对话启用了 memory table isolation 时，从该对话 spawn 的子代理仍然会看到所有 ASSISTANT scope 的文档数据，相当于绕过了 isolation 意图。

**注意**: 这与设计文档的描述不一致——`design.md` :73 明确写道 "Conversation-scoped docs only when spawn has conversationId and isolation rules match ChatService"。

**严重度**: major（设计与实现不一致；隐私/数据隔离语义不符）

---

### Finding #3 — 仅首次 spawn 注入，continuation 不重复注入（正确行为说明）

**文件**: `SubagentHost.kt` :385-397

memory table transformers 仅在 `!contextAcquisition.reusedContext` 且 `profile.injectedMemoryTableTemplateIds.isNotEmpty()` 时构建。后续的 truncated/summary continuation 调用 `runToCompletion` 时**不传** inputTransformers（:425-438, :458-471）。

这是**正确行为**：第一次 `generateText` 已将 memory table 内容注入到 system message 中，后续 continuation 的 messages 已包含该 system message，不需要也不应该重复注入。

**严重度**: 无问题（确认设计正确）

---

### Finding #4 — `parentMemoryTableEnabled = true` 硬编码（Nit）

**文件**: `DataSourceModule.kt` :305

```kotlin
val (resolvedTemplates, resolvedDocuments) =
    resolveSubagentMemoryTableInjection(
        selectedTemplateIds = selectedTemplateIds,
        templates = templates,
        documents = documents,
        parentMemoryTableEnabled = true,  // ← 硬编码
    )
```

lambda 内部已在 :290 检查了 `parentEnabled`，走到这里时 `parentMemoryTableEnabled` 必定为 true，所以硬编码没有逻辑问题。但 `resolveSubagentMemoryTableInjection` 的签名中该参数的语义是"双重检查门控"，硬编码 true 让函数内的第一行检查（:16 `if (!parentMemoryTableEnabled || selectedTemplateIds.isEmpty())`）永远为 false（对 parentMemoryTableEnabled 部分）。

**严重度**: nit（功能无影响，但有冗余路径）

---

### Finding #5 — `resolveSubagentMemoryTableInjection` 不包含 conversation isolation 逻辑

**文件**: `SubagentMemoryTableInjection.kt` :10-31

纯函数 `resolveSubagentMemoryTableInjection` 只按 `selectedTemplateIds` 过滤 templates 和 documents。根据设计文档，conversation isolation 过滤应在 resolution 阶段或调用前完成。当前 resolution 函数不感知 scope 类型过滤。

这意味着如果要在调用方补充 conversation isolation 过滤（修复 Finding #2），要么在 loader lambda 内做，要么给 `resolveSubagentMemoryTableInjection` 增加参数。

**严重度**: major（与 Finding #2 关联）

---

### Finding #6 — UI 缺失 memory table scope-aware 可见性过滤

**文件**: `AssistantSubagentProfilePage.kt` :728-758

UI 从 `AssistantDetailVM.memoryTableTemplates` 获取模板列表，这是 `getEffectiveTemplatesFlow(assistantId)` 的结果——包含 ASSISTANT scope 和 GLOBAL scope 的模板。UI 展示所有 effective templates 供选择，这本身是合理的。

但如果父对话启用了 `memoryTableIsolation`，某些 ASSISTANT scope 的文档实际上不可见。用户可能选择了该模板，spawn 时 resolver 也能找到 templates，但注入时 scope 过滤的缺失会导致非预期数据进入子代理。

这与 Finding #2 属于同一根因。

**严重度**: major（与 Finding #2 同根因，UI 层不感知 isolation 状态）

---

### Finding #7 — 测试覆盖不足

**文件**: `SubagentMemoryTableInjectionTest.kt`

现有 5 个测试覆盖了：
- ✅ 空选择返回空
- ✅ parent gate off 返回空
- ✅ 过滤无效 template ids 并排序
- ✅ Profile 序列化 round-trip
- ✅ 旧配置反序列化兼容

**缺失测试**:
- ❌ 无 conversation isolation 过滤测试（一旦修复 Finding #2 需要补充）
- ❌ 无 documents scope 过滤测试（documents 的 scopeType 是否被正确考虑）
- ❌ 无 DataLoader 集成场景测试（虽然纯函数已有单元测试，但 loader lambda 逻辑在 DataSourceModule 中无法直接测试）

**严重度**: minor（核心纯函数已有测试；缺失场景可后续补充）

---

### Finding #8 — DI lambda 捕获 memoryTableRepository 变量作用域

**文件**: `DataSourceModule.kt` :285-321

```kotlin
single {
    val memoryTableRepository = get<me.rerere.rikkahub.data.repository.MemoryTableRepository>()
    me.rerere.rikkahub.data.ai.subagent.SubagentHost(
        generationHandler = get(),
        contextCache = ...,
        memoryTableInjectionLoader = { parentAssistant, conversationId, selectedTemplateIds, settings ->
            // 使用 memoryTableRepository
            val templates = memoryTableRepository.getEffectiveTemplates(...)
```

lambda 捕获了 `memoryTableRepository`，这在 Koin `single` scope 下是安全的（single 生命周期与 app 一致）。但 `SubagentHost` 也是 `single`，所以 `memoryTableInjectionLoader` 也只在 app 生命周期内存在一次。这是正确的。

**严重度**: 无问题

---

### Finding #9 — 缺少 `isEffectiveFor` scope 过滤在 resolver 中的体现

**文件**: `SubagentMemoryTableInjection.kt` :27-29

```kotlin
val resolvedDocuments = documents
    .filter { it.templateId in allowedTemplateIds }
    .sortedWith(compareBy({ it.templateId }, { it.id }))
```

`documents` 参数来自 `MemoryTableRepository.getEffectiveDocuments()`，该方法内部已调用 `isEffectiveFor(assistantId, conversationId)` 过滤。所以 documents 进入 resolver 时已是 effective 状态。

但 resolver 自身不检查 scopeType，这意味着如果调用方传入了未经 scope 过滤的 documents（例如直接从 `getDocuments()` 而非 `getEffectiveDocuments()`），会有 scope 泄漏风险。当前的调用路径是安全的，但函数签名没有约束。

**严重度**: nit（当前调用链安全；防御性编程建议）

---

### Finding #10 — 字符串资源完整

**文件**: `values/strings.xml` :110-114, `values-zh/strings.xml` :109-113, `values-zh-rTW/strings.xml` :1374-1378

三个 locale 均有完整的 5 个字符串资源（title, desc, empty, missing, invalid）。✅

**严重度**: 无问题

---

### Finding #11 — `memoryTableInjectionLoader` 的可空性

**文件**: `SubagentHost.kt` :245-250

```kotlin
private val memoryTableInjectionLoader: (suspend (...) -> List<...>)? = null,
```

lambda 默认 `null`，在 :389 使用 `?.invoke(...).orEmpty()`。这意味着如果 `SubagentHost` 没有通过 DI 注入 loader（例如测试场景），注入静默跳过。这是合理的降级设计。

**严重度**: 无问题（设计正确）

## 4. 严重度汇总

| 严重度 | Finding | 简述 |
|---|---|---|
| **major** | #2 | 缺少 conversation memory table isolation 过滤，子代理可能绕过隔离 |
| **major** | #5 | resolve 函数不包含 scope 过滤，与设计文档不一致 |
| **major** | #6 | UI 层不感知 memory table isolation 状态 |
| **minor** | #7 | 缺少 isolation / scope 相关单元测试 |
| **nit** | #1 | FQN 未收 import |
| **nit** | #4 | parentMemoryTableEnabled 硬编码 true（冗余但无害） |
| **nit** | #9 | resolver 函数签名不约束 documents 来源 |

## 5. Verdict: fix-first

**建议**: fix-first（修复 Finding #2/#5/#6 后合入）

Finding #2 是核心问题：当父对话启用 `memoryTableIsolation` 时，从该对话 spawn 的子代理会看到 ASSISTANT scope 的文档数据，违背了 isolation 的设计意图。设计文档明确要求 mirror ChatService 的 isolation 规则，但实现中完全缺失。

修复方案建议：
1. 在 `DataSourceModule.kt` 的 lambda 中补充 conversation isolation 过滤逻辑（需从 Settings 或 Assistant 获取 isolation 配置，但当前 lambda 签名不包含此信息——需考虑是否将 `memoryTableIsolation` 通过参数传递或从 Settings 中读取）。
2. 或者在 `resolveSubagentMemoryTableInjection` 中增加 scopeType 过滤参数。
3. 补充对应单元测试。

除 isolation 问题外，其余代码质量良好：架构清晰、DI 松耦合、序列化兼容、UI 模式一致、测试覆盖核心逻辑。
