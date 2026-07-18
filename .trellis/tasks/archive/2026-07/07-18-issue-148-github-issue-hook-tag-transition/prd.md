# 处理 Issue #148 GitHub Issue Hook 标签联动

## Goal

扩展 Assistant Hook 的 action-specific 框架，新增受限的会话标签状态转换 Action：仅当当前 logical turn 的最终助手文本提供可审计的 GitHub Issue 创建成功证据时，原子地添加“完成”标签并移除“进行中”标签；普通聊天、草稿、失败、待确认或证据不足时 fail closed。

## Requirements

- 保持 `add_conversation_tag` 与 `sync_memory_table` JSON、hash、provider/parser、history 和执行语义兼容；复用现有 final-success、logical-turn exactly-once、lease、timeout、顺序执行与 action registry，不新增调度器。
- 新增 `HookActionConfig.TransitionConversationTags` / 对应 Action type，配置使用稳定 `addTagId`、`removeTagId` 与固定 `GITHUB_ISSUE_COMPLETION` filter；两个标签必选、必须不同，缺失或已删除时不得按名称重建。
- GitHub Issue-only 本地筛选只读取当前 logical turn 的最终 assistant 文本，不扫描旧轮次、完整 prompt、原始 tool output 或子代理 transcript。
- 本地证据必须在同一 sentence/clause 的 80 Unicode code-point 窗口内同时包含：有效 GitHub Issue ref（严格 `https://github.com/{owner}/{repo}/issues/{positiveInt}` 或边界安全的 `#positiveInt`）与明确创建完成语义（created/submitted/filed/published successfully；已创建/创建成功/已提交/提交成功/已登记等）。
- 代码块、行内代码、引用块中的 ref，以及同一 clause 的草稿、建议、计划、失败、报错、否定、等待确认、尚未执行等语义优先拒绝；普通数字、PR/build/order/commit 编号、仓库 URL、issues 列表/new、pull URL、相似域名均拒绝。
- 证据筛选在 provider 前执行；失败记录稳定 SKIPPED 原因，零模型调用、零标签写入。通过筛选后，模型只能返回严格 `{decision, reason}`，标签 ID 和删除边界完全来自本地配置。
- 两个标签与冲突配置必须在 provider 前校验；模型返回后重查当前 Hook enabled/config version/hash。新标签转换 committer 必须在一个 Room transaction 内重查 lease、会话、活动 source node/message 和两个标签实体；先 remove 再 add，并在同一事务完成 execution/run 终态。
- remove/add 均复用现有 ConversationTag relationship 写边界；重复关系操作幂等，不覆盖整会话或其他标签。任一步骤、execution 终态或 lease 检查失败时整体回滚。
- generalize prepared/terminal audit 写入，使 filter reject、provider skip、apply changed/no-op 均保存有界结构化 summary/diff：固定 action/filter、add/remove tag IDs、evidence enum、changed/no-op 与 operation count；不落最终文本、完整 prompt、原始模型响应、tool output 或凭据。
- UI 在现有 Hook editor 中提供新 Action、完成标签单选、进行中标签单选和固定的 GitHub Issue-only 条件说明；覆盖 Loading/Error/Empty、失效标签、同标签冲突、草稿切换保留、错误 announcement、已保存摘要和本地化。网络状态不得禁用本地编辑/保存。
- 不新增 Room Entity/列；优先复用 v42 `tag_id`、`operation_count`、`operation_summary_json`、`diff_summary_json` 与 error code，避免无必要 v43 migration。

## Acceptance Criteria

- [x] 旧 AddTag/Sync 配置、哈希、parser、执行和历史回归通过；新 Action round-trip/hash 字段完整。
- [x] 新 Action 可配置完成/进行中两个稳定 tag ID，并固定使用 Issue-completion filter；缺失、失效、相同 ID 时不可保存，且零 provider 调用。
- [x] 本地 filter 只在同一 clause/80 code-point 窗口接受严格 Issue URL 或 `#N` + 明确创建成功语义；普通数字、PR/build/order/commit ID、repo/pull/issue-list URL、代码/引用、草稿/建议/否定/失败/等待确认/无 ref 全部 SKIPPED。
- [x] filter 只使用当前 logical turn 最终 assistant 文本；旧轮次 Issue ref、tool output 或 transcript 不会触发。
- [x] filter 失败零 provider 调用、零 add/remove；模型输出不能指定/更换标签或扩大删除范围。
- [x] 模型返回后 Hook disabled/version/hash 变化阻止 commit；成功路径在同一 lease 保护事务中先 remove 进行中再 add 完成，并原子写 execution/run audit；满 20 标签时可安全交换。
- [x] 已完成/已移除为幂等；其他标签不变；任一 tag/conversation/source/lease/终态失败均零半写入。
- [x] history 区分 decision 与 status，并展示 add/remove changed/no-op、证据 enum 和稳定错误；filter reject、provider skip、0/1/2 changed 均有结构化 audit，且不记录正文/raw response/tool output。
- [x] UI 的 selector label/semantics、Loading/Error/Empty 下保存行为、draft 保留、错误可读 announcement、Dark Mode 和 6-locale 本地化满足现有页面规范；offline 不禁用本地编辑/保存。
- [x] JVM/Room/Hook/UI 回归、生产与 androidTest 编译、资源/lint 审计及可用设备安装通过，或如实记录环境限制。

## Confirmed Facts

- #147 已提供 action-specific Handler/Registry/Dispatcher、stage-specific errors 和 handler-owned atomic terminalization。
- `ConversationTagRepository.addTag/removeTag` 已存在且关系表主键提供重复操作幂等；当前 AddTag handler 的标签副作用与 execution 终态仍分事务。
- 每会话最多 20 个标签，因此转换事务必须先 remove 后 add；外层事务可在 add 失败时回滚 remove。
- `HookFinalSuccessGate` 不负责 GitHub 语义；`UIMessage.toText()` 只拼 Text part，适合明确限定为最终文本证据。
- 当前 DB v42 generalized audit 字段足以存最小标签转换摘要，无需新 schema。

## Out of Scope

- 解析或持久化完整 workspace shell/subagent transcript、从 stderr 推断成功、信任模型返回标签名称/ID。
- 自动创建/重命名标签、按名称模糊匹配、删除配置外标签、仓库级硬编码、独立后台 Job。
- 阻止用户在 Hook 提交后再次手动修改标签；关系最后写入者语义保持现状。

## Open Questions

- 无阻塞问题。V1 filter 固定为 `GITHUB_ISSUE_COMPLETION`，不提供关闭后由模型自由判断的模式；采用“同 clause 的有效 Issue ref + 明确创建成功语义”双条件，结构化 tool/transcript 解析留待后续独立需求。
