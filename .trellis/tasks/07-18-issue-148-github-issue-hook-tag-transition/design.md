# Issue #148：GitHub Issue Hook 标签联动设计

## Architecture

- 新增 `TransitionConversationTags` Action，不修改旧 `AddConversationTag` 的序列化标识或输出协议。
- Action `prepare()` 先调用纯函数 `detectGitHubIssueCompletionEvidence(finalAssistantText)`；未通过直接返回 audited SKIPPED，不调用 provider。
- 通过后冻结 evidence + add/remove tag IDs，provider 仅返回严格 `{decision, reason}`；`apply` 执行预配置转换，`skip` 无副作用。
- 提取 `ConversationTagHookCommitter`，供旧 AddTag 与新 Transition 共用同事务副作用/终态边界，消除标签写入与 execution completion 的双事务窗口。

## Evidence contract

- 输入只取 `HookFinalMessageSnapshot.text` 对应的最终 active assistant message；不读取整个 conversation 历史或 tool raw output。
- 预处理移除 fenced code、inline code 和 Markdown quote 行。
- URL 使用 URI 解析并要求 scheme/host case-insensitive 等于 https/github.com、无 userInfo/port、raw path 无 percent encoding、路径精确 owner/repo/issues/positiveInt；允许尾斜杠、query、fragment 和句末标点，编号解析为正 Long 且禁止溢出。
- shorthand 使用 Unicode 边界安全 `#N`，拒绝 `#0`、前导零、`C#123`、路径片段、普通数字，以及同 clause 的 PR/pull request/build/order/commit/release marker。
- 将文本按换行、`.?!。！？;；` 切成 clause。ref 与成功词必须在同 clause 且相距不超过 80 code points；匹配使用 `Locale.ROOT` case-folding 和词边界。
- 成功 lexicon 至少含 `created`, `submitted`, `filed`, `published successfully`, `已创建`, `创建成功`, `已提交`, `提交成功`, `已登记`；负面 lexicon 至少含 `not created`, `couldn't create`, `failed`, `error`, `draft`, `plan`, `suggest`, `waiting`, `confirm`, `未创建`, `创建失败`, `报错`, `草稿`, `计划`, `建议`, `等待`, `确认`, `尚未执行`，负面优先。
- 只冻结 `evidenceType`、issue number 和规范化 URL（若有）；不持久化源文本。

## Action and provider contracts

- `HookActionConfig.TransitionConversationTags(addTagId, removeTagId, filter=GITHUB_ISSUE_COMPLETION)`；V1 filter 不可关闭。
- 新 `FrozenHookModelRequest.TransitionConversationTags` 包含 configured prompt、最终文本 snapshot、evidence metadata 和两个允许 ID 的本地显示名；不提供 tools。
- 新 parser 要求 raw text 无外围空白/Markdown、response 不超过既有 Hook 上限、exact keys 为 `decision`, `reason`，decision 大小写敏感且仅 `apply|skip`；reason 使用既有 500 code-point truncation。
- configuration hash 覆盖 add/remove IDs、filter、model、prompt 与既有公共字段。

## Atomic tag transition

handler 在 provider 后、committer 前从 SettingsStore 重查当前 Hook enabled/config version/hash；不通过返回 audited SKIPPED。随后 `ConversationTagHookCommitter.commit` 在 `AppDatabase.withTransaction` 内：

1. 检查 execution 仍 RUNNING 且 lease token 有效。
2. 通过新增窄 MessageNode DAO 查询重读 source node，确认 conversation/node、`hidden=false`、selected message ID 与冻结 source 一致。
3. 重读 add/remove tag 实体；拒绝缺失与同 ID。
4. `removeTag(conversationId, removeTagId)`，再 `addTag(conversationId, addTagId)`。
5. 生成 bounded summary/diff（changed/no-op），调用 `finishExecutionRaw` 并 `recalculateRun`。
6. 任一步失败抛出，标签关系与 history 一起回滚；handler 返回 `Terminalized`。

旧 AddTag 走相同 committer，但只提供 add ID，保持 parser/provider/config 不变。

## History and errors

- 将 `HookPreparedAudit` 泛化为所有 Action 可选字段，并让 prepared-audit update/terminal completion 能写 `operation_count`、summary/diff。bounded JSON schema 固定为：
  - summary: `{"action":"transition_conversation_tags","filter":"github_issue_completion","evidence":"issue_url|issue_number|none","addTagId":"uuid","removeTagId":"uuid","added":true|false|null,"removed":true|false|null}`；
  - diff: `[{"type":"remove","tagId":"uuid","changed":bool},{"type":"add","tagId":"uuid","changed":bool}]`，filter/provider skip 为 `[]`。
- `tag_id` 保存 add tag ID；`operation_count` 是实际 changed 关系数。filter reject 在 prepare 阶段写 evidence=`none` + operation_count=0；provider skip 保留 evidence pass + 0；apply 覆盖 added/removed 和 0/1/2。
- 新稳定错误：`GITHUB_ISSUE_EVIDENCE_NOT_FOUND`、`TAG_TRANSITION_CONFLICT`；缺失标签继续 `TAG_NOT_FOUND`，source/lease 使用既有错误。
- Transition terminal matrix（未列字段均为 null；reason 仅 provider 已返回且通过 `finishFromAction` 时使用 parsed/truncated reason）：
  - filter reject -> `(SKIPPED, decision=null, error=GITHUB_ISSUE_EVIDENCE_NOT_FOUND, operationCount=0)`；
  - same IDs preflight -> `(SKIPPED, null, TAG_TRANSITION_CONFLICT, 0)`，零 provider；
  - stale/missing add 或 remove tag preflight -> `(SKIPPED, null, TAG_NOT_FOUND, 0)`，零 provider；
  - provider skip -> `(SKIPPED, SKIP, error=null, 0)`；
  - provider 后 Hook disabled/version/hash changed -> `(SKIPPED, parsed decision, HOOK_DISABLED, 0)`；
  - APPLY: add absent/remove present -> `(SUCCESS, APPLY, null, 2)`；add absent/remove absent -> `(SUCCESS, APPLY, null, 1)`；add present/remove present -> `(SUCCESS, APPLY, null, 1)`；add present/remove absent -> `(SKIPPED, APPLY, null, 0)`；
  - tag deleted after provider -> `(FAILED, decision=null, TAG_NOT_FOUND)`；20-tag add failure -> `(FAILED, null, TAG_LIMIT_REACHED)`，remove 回滚；
  - conversation missing -> `(CANCELLED, null, CONVERSATION_NOT_FOUND)`；source inactive -> `(CANCELLED, null, SOURCE_MESSAGE_NOT_ACTIVE)`；
  - timeout/lease invalidation -> 保留既有 `(FAILED, null, HOOK_TIMEOUT)` 或当前 terminal row，late committer 不覆盖；terminal update count 0 -> side effects rollback且不覆盖当前 row。

## UI

- Action selector 增加 Transition；切换 Action 时保留 AddTag/Sync/Transition 各自 draft。
- 两个单选 tag selector：完成标签、进行中标签；同一 ID 冲突，失效 ID 提供清理/重选。
- 固定条件行显示 `GITHUB_ISSUE_COMPLETION` 及证据说明，不提供关闭开关。列表摘要显示“进行中 → 完成”与 filter 状态。
- AssistantDetailVM 将 tag list 暴露为显式 loading/success/error state；本地 Room 配置无需网络，offline 只影响外部 provider 执行，不影响编辑。
- selector 提供明确 label/contentDescription；Loading 时禁止保存，Error 保留 draft 并提供重试，Empty 引导创建标签，失效/冲突错误通过可读 supporting text/semantics announcement 展示；Action 切换保留各 draft。
- 所有新增文案使用 locale-tui 覆盖 6 个 locale。

## Compatibility and rollout

- DB 保持 v42；不新增 Entity/列。
- 旧 `add_conversation_tag` JSON 与 hash material 保持 golden；旧 execution rows 无新必填字段。AddTag golden execution matrix 固定为：
  - model skip -> `(status=SKIPPED, decision=SKIP, tagId=null, error=null, reason=parsed)`；
  - changed add -> `(SUCCESS, APPLY, configured tagId, null, parsed reason)`；
  - duplicate add -> `(SKIPPED, APPLY, configured tagId, null, parsed reason)`；
  - tag outside allowlist -> `(SKIPPED, APPLY, returned tagId, TAG_NOT_ALLOWED, parsed reason)`；
  - missing configured/returned tag -> `(SKIPPED, APPLY, returned tagId, TAG_NOT_FOUND, parsed reason)`；
  - missing conversation -> `(CANCELLED, decision/tag/reason=null, CONVERSATION_NOT_FOUND)`；inactive source -> `(CANCELLED, nulls, SOURCE_MESSAGE_NOT_ACTIVE)`；
  - lease timeout/loss -> 不覆盖当前 terminal row；若 timeout owner 已终结，则保持 `(FAILED, decision/tag/reason=null, HOOK_TIMEOUT)`；
  - 所有 `target_*`, revision, operation summary/diff, retry, idempotency generalized audit 字段继续为 null/default，reasonTruncated 保持 parsed 值。
- 新 Action 可通过 disabled Hook 或删除配置快速停用；主生成不等待/不因 Hook 失败而失败。

## Key risks

- 证据 regex 过宽会误改普通会话；采用正向 ref + 成功词双条件与负面优先。
- AddTag 若不迁入原子 committer仍保留 side-effect/history 漂移窗口；本任务一并收紧，但必须用 execution-row golden matrix锁定既有 status/decision/tag/reason/error/null audit。
- 先 add 后 remove 会在 20 标签上限下错误失败；事务固定先 remove 后 add。
