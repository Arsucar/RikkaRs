# 修复审查发现的问题

## 背景
基于 `06-28-review-all-changes` 审查报告，对近期改动中的代码问题进行修复。

## 修复清单

### P0 - 必须修复

| ID | 问题 | 位置 | 描述 |
|----|------|------|------|
| F1 | 协程竞态 | `SubagentHost.kt` | `launch { cb }` 与 `finally { cancel() }` 存在竞态，progressScope 取消后仍可能跑完回调 |
| F2 | CAS 竞态 | `ChatService.kt` | 主生成用 `updateConversation` 直接赋值，子代理进度用 `updateConversationState` CAS，存在覆盖/丢进度风险 |
| F3 | remember 键遗漏 | `ModelList.kt` | `remember(settings.value.favoriteModels, providers, modelType)` 漏了 `settings.value.providers`，依赖可能陈旧 |

### P1 - 建议修复

| ID | 问题 | 位置 | 描述 |
|----|------|------|------|
| F4 | generateCloneName 缺参数 | `AssistantSubagentPage.kt` | 克隆子代理时未传 `globalProfiles`，可能产生重名 |
| F5 | 未用 import | `ExtensionSubagentProfilePage.kt` | `SubagentRegistry` import 未使用 |
| F6 | currentToolCallId null 匹配过宽 | `ChatService.kt` | 为 null 时匹配所有工具调用，应加保护 |
| F7 | profileName 未转义 | `ChatService.kt` | 手工拼 JSON 时 `profileName` 含特殊字符会被破坏 |
| F8 | 全局空列表解析失败 | `SubagentRegistry.kt` | `global` 为空时 `mergeSubagentProfiles` 行为异常 |

### P2 - 可优化

| ID | 问题 | 位置 | 描述 |
|----|------|------|------|
| F9 | i18n 缺失 | 多语言 strings.xml | Provider 标签 key 未入 ja/ko/ru；`subagent_global_*` 多数 locale 缺翻译；英文改后简中未同步 |
| F10 | 节流 / transcript 测试缺失 | `SubagentHost.kt` / `SubagentModelTest.kt` | 缺节流和 `createdAt`/`executed` 字段的单元测试 |
| F11 | 无障碍缺失 | `ModelList.kt` | 多处 `contentDescription = null` |

## 验收标准
- F1-F3 修复后无已知竞态条件
- F4-F8 修复后功能行为正确
- F9 补全 i18n 缺失 key
- 编译通过，现有测试全部通过
