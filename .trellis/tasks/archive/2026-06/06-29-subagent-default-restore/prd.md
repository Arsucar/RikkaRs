# PRD: Restore builtin subagents (coder/reviewer) + union fallback

**Parent:** `06-29-backport-batch-2`
**Complexity:** 轻量（改 2 处 + 测试）
**集成顺序建议:** 第 1 个（P0 止血，最小最快）

## 背景

用户反馈：合并子代理全局化后，默认子代理只剩 explore，coder/reviewer 消失。

**根因**（调研见 `.trellis/tasks/06-29-screentime-accuracy/research/builtin-subagent-missing-coder-reviewer.md`）：
- `6dca48a1` 全局化后，默认集合 = `Settings.globalSubagentProfiles` 持久化内容。
- `effectiveGlobalProfiles`（`SubagentRegistry.kt:67`）**只在 global 整表为空时** fallback `BUILTIN_PROFILES`；global 里有任何项（哪怕只有 explore）就不 fallback。
- `deleteGlobalSubagent`（`SettingVM.kt:28-33`）物理移除且**无内置名保护**。
- 一旦 global 里只留 explore，运行时 `resolveProfile("coder")` 和 UI 都拿不回 coder/reviewer。

代码层 `BUILTIN_PROFILES`（`SubagentRegistry.kt:7-55`）始终是 3 个，没被 commit 删过；问题在持久化层与 fallback 逻辑。

## 范围

### In Scope

1. **`effectiveGlobalProfiles` 改为 union 而非 empty-fallback**（`SubagentRegistry.kt:62-63`）
   - 现状：`global.ifEmpty { BUILTIN_PROFILES }`
   - 改为：对 `BUILTIN_PROFILES` 中所有 name，若 `global` 里没有则补入（union by name，global 里的自定义/修改优先）。
   ```kotlin
   internal fun effectiveGlobalProfiles(global: List<SubagentProfile>): List<SubagentProfile> {
       val globalNames = global.map { it.name }.toSet()
       val missingBuiltins = BUILTIN_PROFILES.filter { it.name !in globalNames }
       return global + missingBuiltins
   }
   ```
   - 效果：用户删除 coder 后，下次启动/读取时自动补回（静默恢复）；用户对 builtin 的自定义修改（如改了 systemPrompt）仍保留（因为 global 里那条 name 存在，不会被覆盖）。

2. **`deleteGlobalSubagent` 内置名保护**（`SettingVM.kt:28-33`）
   - 内置名（explore/coder/reviewer）禁止物理删除，改为：
     - 方案 A（推荐）：内置名删除时改为"重置为 BUILTIN 默认"（从 `BUILTIN_PROFILES` 取默认值替换 global 里那条）。
     - 方案 B：内置名直接禁删，UI 隐藏删除按钮或 toast 提示"内置子代理不可删除，可重置"。
   - 采用方案 A：用户点删除内置子代理时，实际效果是恢复默认配置，更符合直觉。

3. **测试**
   - `effectiveGlobalProfiles` union 测试：global=[explore 自定义] → 结果含 explore(自定义)+coder+reviewer。
   - `effectiveGlobalProfiles` 空全局测试：global=[] → 结果含全部 3 BUILTIN。
   - `deleteGlobalSubagent` 内置名测试：删 coder → global 里 coder 被重置为 BUILTIN 默认而非移除。
   - 补到现有 `SubagentModelTest.kt` 或新建 `SubagentRegistryTest.kt`。

### Out of Scope

- 不改 migration（`migrateSubagentBuiltinsIfNeeded`）—— union fallback 已覆盖首次迁移失败的场景。
- 不改 UI 展示逻辑（`ExtensionSubagentsPage`）—— union 后 UI 自动显示全 3 个。
- 不加"内置子代理"badge 标记 —— 后续 UI 统一任务（`local-tool-ui-consolidation`）处理。
- 不处理 `disabledGlobalSubagents`（助手级禁用）—— 那是 per-assistant 开关，不影响 global 列表。

## 验收标准

- [ ] `effectiveGlobalProfiles` 在 global 只有 explore 时返回 3 个（explore + coder + reviewer）。
- [ ] `effectiveGlobalProfiles` 在 global 有 explore 自定义 + coder 自定义时返回：explore(自定义) + coder(自定义) + reviewer(BUILTIN)。
- [ ] `deleteGlobalSubagent("coder")` 后，global 里 coder 被重置为 BUILTIN 默认而非消失。
- [ ] 删除非内置名（如用户自建的 "my-helper"）仍正常物理移除。
- [ ] 真机验证：启动后扩展 → 子代理页显示 explore/coder/reviewer 全部 3 个，即使之前删过。
- [ ] `.\gradlew :app:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :app:testDebugUnitTest --no-daemon --tests "*subagent*"` 通过。

## 约束

- union 必须保留 global 里的自定义（by name 去重，global 优先），不能因为 BUILTIN 有同名就覆盖用户修改。
- 内置名集合从 `BUILTIN_PROFILES.map { it.name }` 派生，不硬编码字符串（未来可能新增内置）。
- `deleteGlobalSubagent` 重置逻辑：用 `BUILTIN_PROFILES.first { it.name == name }` 取默认值。
