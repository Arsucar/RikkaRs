# PRD: HttpSearchService base + ENTRIES registry refactor

**Parent:** `06-29-backport-batch-2`
**Source:** sub `2c5a1786`（基类 + 迁移 4 个）+ `d0eeb56c`（迁移剩余 11 个）+ `b48f3f30`（ENTRIES 统一注册表）+ 配套 bug 修复
**Complexity:** 复杂（19 service 全量重构 + 配套修复）
**集成顺序建议:** 本批第 3 个 / 最后一个（体量最大、回归风险最高）

## 背景

我们 fork 的 `search/src/main/java/me/rerere/search/` 下 19 个搜索服务各自复制一套 HTTP 请求 + JSON 解析 + 异常处理样板，新增 provider 需要从头写约 200 行重复代码。sub fork 在三笔提交里完成了结构性重构：

1. **`2c5a1786`**：引入 `HttpSearchService` 抽象基类（138 行），把 HTTP 请求/响应解析/异常映射/结果裁剪等公共逻辑上提；先迁移 4 个简单 provider（Brave / Metaso / Ollama / Zhipu）验证设计。
2. **`d0eeb56c`**：把剩余 11 个 HTTP provider（Bocha / Exa / Firecrawl / Grok / Jina / LinkUp / Perplexity / RikkaHub / Serper / Tavily / Tinyfish）全量迁移到基类，平均每个 service 从 ~150 行降到 ~50 行。
3. **`b48f3f30`**：把分散的 `TYPES`（displayName 映射）+ `create`（18 分支工厂 when）两套并行的知识源合并为单一 `ENTRIES: Map<KClass, ServiceEntry(displayName, factory)>`，`TYPES` 退化为只读视图（`mapValuesTo` 保留插入顺序 + Bing 首位），新增 provider 只需改一处。

此外 sub 在迁移过程中发现并修复了一批既有 bug，本任务必须**一并带回**，否则重构后会出现已知回归：

- `7f3e9356`：`settingsFlow` 对单字段反序列化失败做防御（一个坏字段不让整个设置流挂）。
- `e12b0610`：`parseSearchResponse` 签名扩展 `commonOptions`，恢复 `resultSize` 截断逻辑。
- `4e08197f`：新建 provider 不再塌缩成单个 Bing 卡片（`TYPES` 推导 bug）。
- `cf2a66af`：`coerceIn` 在搜索服务列表为空时崩溃防护；阻止删除最后一项。
- `bd653f5f`：`SearchPicker` 读 live `SettingsStore`，绕过 `ModalBottomSheet` 内容冻结。
- `bb7f1b8b` / `d96007a2`：fork 移植残留编译修复（`apiKey` 未解析引用、缺 `await` import）—— 我们是新移植，理论上不会遇到，但列作检查项。

## 范围

### In Scope

1. **`HttpSearchService` 抽象基类**（`search/src/main/java/me/rerere/search/HttpSearchService.kt`）
   - 封装 HTTP 请求执行（基于 `common` 模块的 HTTP 工具）、响应 JSON 解析、异常映射、结果裁剪。
   - 暴露抽象方法供子类只实现参数差异（endpoint / headers / body 构造 / response → `SearchResult` 映射）。

2. **15 个 HTTP provider 迁移到基类**
   - Brave / Metaso / Ollama / Zhipu（`2c5a1786` 的 4 个）。
   - Bocha / Exa / Firecrawl / Grok / Jina / LinkUp / Perplexity / RikkaHub / Serper / Tavily / Tinyfish（`d0eeb56c` 的 11 个）。
   - 每个 service 从独立实现 HTTP 样板改为 `extends HttpSearchService`，只保留差异部分。

3. **`ENTRIES` 统一注册表**（`SearchService.kt`）
   - 用 `linkedMapOf<KClass, ServiceEntry>` 替代 `TYPES` + `create` 两套映射。
   - `TYPES` 退化为 `ENTRIES.mapValues { it.value.displayName }` 的只读视图，保留插入顺序 + Bing 首位（UI 调用点 `TYPES.keys` / `TYPES[...]` 无需改）。
   - `create` 退化为 `ENTRIES[service::class]?.factory?.invoke(...)`。

4. **非 HTTP provider 不动**
   - `CustomJsSearchService`（用户自定义 JS）、`SearXNGService`（自部署，协议特殊）、`BingSearchService`（如果有非 HTTP 特殊性）—— 核对后决定是否迁移；sub 的迁移列表里没包含 CustomJs，SearXNG 需核对。

5. **配套 bug 修复**（必须随重构带回）
   - `settingsFlow` 单字段反序列化防御（`7f3e9356`）。
   - `parseSearchResponse` 签名 + `resultSize` 截断（`e12b0610`）。
   - 新建 provider 不塌缩（`4e08197f`）。
   - `coerceIn` 空列表防护 + 阻止删最后一项（`cf2a66af`）。
   - `SearchPicker` 读 live store（`bd653f5f`）。

### Out of Scope

- 不迁移 `CustomJsSearchService`（除非它恰好也是 HTTP 样板）。
- 不改搜索 provider 的**业务逻辑**（API 参数、字段映射、排序规则保持不变）。
- 不重构搜索结果 UI 渲染（`SearchResultCard` 等）。
- 不新增 provider（Serper 已有）。

## 验收标准

- [ ] `HttpSearchService.kt` 存在，封装公共 HTTP/解析逻辑。
- [ ] 15 个 HTTP provider 全部 `extends HttpSearchService`，各自只保留差异实现。
- [ ] `SearchService.kt` 用单一 `ENTRIES` 注册表，`TYPES` 为其派生只读视图，`create` 为其派生工厂。
- [ ] 新建 provider 只需在 `ENTRIES` 加一行 + 实现 `HttpSearchService`，无需改 `TYPES` 或 `create`（手工验证）。
- [ ] 配套 bug 修复全部带回（`settingsFlow` 防御 / `parseSearchResponse` / 新建 provider 不塌缩 / coerceIn 防护 / SearchPicker live store）。
- [ ] `.\gradlew :search:compileDebugKotlin --no-daemon` 通过。
- [ ] `.\gradlew :search:testDebugUnitTest --no-daemon` 通过（含 sub 新增的 `SearchServicesDecodeTest.kt`，141 行）。
- [ ] `.\gradlew :app:installDebug --no-daemon` 成功装到设备。
- [ ] 真机验证：配置一个现有搜索 provider（如 Tavily/Brave）执行搜索，结果与重构前一致（不漏结果、不截断错误）。
- [ ] 真机验证：新建一个 provider 配置后不塌缩成 Bing。

## 约束

- **业务逻辑零变更**：重构只动 HTTP 样板抽取和注册表统一，不动各 provider 的 API 调用参数 / 字段映射 / 结果排序。
- **`TYPES` 的 Bing 首位 + 插入顺序**：UI 调用点依赖 `TYPES.keys` 的顺序，`ENTRIES` 用 `linkedMapOf` 并把 Bing 放首位。
- **向后兼容**：已序列化的 `Settings.searchServices` 不能因基类引入而反序列化失败（`SearchServicesDecodeTest` 覆盖此场景）。
- 每个 provider 迁移后保留原文件名与类名，避免 UI / settings 层的 `KClass` 引用失效。

## 风险

- **回归面广**：19 个 service 全量迁移，任何一个的字段映射改动都会导致搜索结果异常。**强烈建议**：迁移按 sub 的提交粒度分批（先 4 个简单 → 验证 → 再 11 个），每批跑 `SearchServicesDecodeTest` + 真机搜索验证。
- **`KClass` 序列化**：`ENTRIES` 用 `KClass<out SearchService>` 作 key，需确认 settings 层序列化/反序列化链路不依赖类名字符串的可移植性。
- **CustomJsSearchService 边界**：用户自定义 JS provider 不走 HTTP，是否纳入基类需核对；纳入则基类需留扩展点，不纳入则在 `ENTRIES` 单独注册。
- **Bing 的特殊性**：sub 的 ENTRIES 设计里 Bing 被强制首位，核对 Bing 是否有非 HTTP 的特殊性（如内置默认 provider），若有需在基类或 ENTRIES 层特殊处理。

## 复杂任务说明

本任务为**复杂任务**，`task.py start` 前需补 `design.md`（基类抽象边界 / ENTRIES 数据结构 / 迁移分批策略 / 回滚点）+ `implement.md`（分批迁移检查表 + 每批验证命令 + review gate）。PRD-only 不足以承载 19 service 重构的执行计划。
