# Conversation Tags and Assistant Hooks

## Scenario: Global Conversation Tags and Post-Response Hooks

### 1. Scope / Trigger

- Trigger: changing conversation tags, tag filtering, conversation fork semantics, Assistant Hook configuration, generation final-success detection, or Hook history.
- Scope: Room schema, repository contracts, `ChatService`, assistant settings, left drawer tags, and right drawer Hook history.

### 2. Signatures

- `ConversationTagRepository.addTag(conversationId: Uuid, tagId: Uuid)` and `removeTag(...)` are the only relationship write boundary.
- `ConversationRepository.getConversationsPaging(filter: ConversationFilter)` applies tag predicates before paging.
- `ConversationRepository.insertForkConversation(sourceConversationId, fork)` inserts the fork and copies tag relations in one Room transaction.
- `Assistant.hooks: List<ConversationHook> = emptyList()` preserves old JSON compatibility.
- `HookRepository.finalizeAndCreateRunExactlyOnce(logicalTurnId, trigger, ..., hooks)` owns exactly-once persistence.
- Versioned Hook sources create a stable `HookEvent` before persistence. `hook_runs.event_id` is the run identity;
  one logical turn may create multiple runs when their event IDs differ.
- Event batches keep the logical turn open while each event is inserted exactly once, then close the turn once after
  all final-success, keyword, tool-failure, and subagent events have been considered. A source with no matching Hook
  must not close or mutate the turn by itself.
- `HookRepository.claimQueued(...)`, `isLeaseActive(...)`, terminal completion methods, and `invalidateLeaseAndFailTimeout(...)` own execution state transitions.

### 3. Contracts

- Tags are a global user-managed vocabulary. Runtime callers may reference an existing `tagId`; they must never create a tag from a name or unknown ID.
- Tag names use NFC, trimmed/collapsed whitespace, locale-independent normalized uniqueness, and Unicode code-point limits.
- Tag relations use a composite key and cascading foreign keys. Adding/removing the same relation is idempotent.
- Selected tag IDs are OR with each other and AND with assistant/folder/archive/search filters. Filtering happens in SQL before paging.
- Whole-Conversation saves must not carry tag collections; otherwise concurrent relationship writes can be lost.
- A Hook runs only after a persisted logical turn reaches final assistant success with no resumable pending tool.
- Keyword, tool-failure, and subagent events are emitted only after the final message is persisted and the pending-tool
  set is empty. Source scans must stay scoped to the current final message so later turns cannot replay historical tools.
- `KEYWORD_MATCHED` is a local final-text prefilter. A miss creates no run and invokes no provider; matching hooks are
  grouped by normalized evidence and dispatched separately from final-success hooks.
- Run history stores a bounded, allow-listed event payload. Tool envelopes must parse structured exit/error fields and
  suppress denied/cancelled operations; subagent payloads record terminal status, bounded context completeness, and
  truncation reason without raw credentials, arbitrary URLs, or workspace paths. A keyword event may retain only a
  canonical GitHub Issue URL because #159 makes that URL an explicit evidence field; it is parsed and normalized
  locally before persistence.
- Terminal tool/Shell and subagent events use their stable event ID as `HookFreezeContext.sourceKey`, allowing existing
  action cursors (including `SYNC_MEMORY_TABLE`) to make retries idempotent without a second action dispatcher.
- Hook model requests use a frozen in-memory message/prompt/config snapshot. Full prompt, message snapshot, provider output, headers, and credentials never enter Hook Room tables.
- Tag management Hook output is one JSON object whose key set is exactly `decision`, `operations`, and `reason`. Each operation is `{ "op": "add"|"remove", "tagId": "<uuid>" }` and must stay within the configured allowlist; invalid ops fail closed with zero tag writes.
- Legacy `add_conversation_tag` / `transition_conversation_tags` configs normalize to `manage_conversation_tags` on load/save. Historical DB `ADD_CONVERSATION_TAG` / `TRANSITION_CONVERSATION_TAGS` enum names remain display-only aliases.
- A timeout invalidates the lease before cancelling work. Parser and action writes must recheck the active lease so late results cannot write tags or overwrite terminal state.
- `generationDoneFlow` remains a UI notification mechanism and is not a Hook success or history source.
- Error-experience `SYNC_MEMORY_TABLE` evaluation uses the exact keys `should_remember`, `deduplication_key`,
  `symptom`, `root_cause`, `correction`, `scope`, `tools`, `commands`, and `reason`. The local mapper requires a
  writable target schema with a deduplication key and at least one durable content column, maps only declared columns,
  and fails closed when the payload cannot be queried, evidence cannot be sanitized, or context is insufficient.
  Equivalent rows update their bounded evidence/time/count fields when those columns exist; source event identity is
  still enforced by the existing Hook action cursor, so a replay cannot increment twice.

### 4. Validation & Error Matrix

- Unknown tag ID -> stable `TAG_NOT_FOUND`; no tag created.
- Unknown conversation ID -> stable `CONVERSATION_NOT_FOUND`; no relation written.
- Duplicate add/remove -> successful no-op.
- Invalid name/color or 40/100/20 limit violation -> reject before visible state changes; transaction rechecks count limits.
- Invalid Hook JSON or extra/missing fields -> `INVALID_JSON` or `SCHEMA_MISMATCH`; action not executed.
- Model/provider/request failure -> current execution `FAILED`; later Hooks continue and main generation remains successful.
- Deleted/inactive source message -> execution `CANCELLED`; no tag write.
- Deleted or disallowed tag -> `SKIPPED`/`FAILED` according to the stable error contract; never recreate by name.
- Process death with QUEUED/RUNNING work -> mark `INTERRUPTED` at startup; do not reconstruct or retry requests.

### 5. Good/Base/Bad Cases

- Good: a fork transaction inserts conversation, nodes, and copied tag relations together; any failure rolls everything back.
- Base: an assistant without Hooks decodes normally, closes its logical turn, and creates no Hook run.
- Good: multiple selected tags use `EXISTS ... IN (...)`, preserving page size and preventing duplicate rows.
- Bad: load a page, filter it in memory, or query tags once per conversation row.
- Bad: execute a Hook from `generationDoneFlow` or `handleMessageComplete.onSuccess` without checking pending tools and logical-turn uniqueness.

### 6. Tests Required

- Migration tests for tag tables, Hook tables, indexes, cascades, and `PRAGMA foreign_key_check`.
- DAO/repository tests for normalization, limits, merge, idempotency, tag OR plus other-filter AND, and fork rollback.
- Assistant JSON compatibility and Hook configuration/hash tests.
- Parser tests for apply/skip, fences, surrounding text, arrays, exact key set, UUIDs, and 500-code-point truncation.
- Gate tests for normal generation, approval continuation, pending tools, cancellation, failure, empty assistant output, and duplicate dispatch.
- Lease/timeout race test proving late parser/action results cannot change tags or terminal metadata.
- Compile `debugAndroidTest` sources even when connected instrumentation tests are not requested.

### 7. Wrong vs Correct

#### Wrong

```kotlin
conversationRepository.update(conversation.copy(tags = updatedTags))
generationDoneFlow.collect { runHooksForLatestMessage() }
```

This couples tag relations to stale whole-object saves and treats a non-replayed UI signal as final-success evidence.

#### Correct

```kotlin
conversationTagRepository.addTag(conversationId, existingTagId)
hookRepository.finalizeAndCreateRunExactlyOnce(logicalTurnId, trigger, metadata)
```

## Scenario: Assistant Hook configuration UI

### 1. Scope / Trigger

Use this contract when changing the assistant Hook list, editor validation,
action presentation, or destructive configuration actions.

### 2. Signatures

- `validateHookDraft(...) -> HookDraftValidation` is the single editor
  validation projection and owns `canSave`.
- `hookActionLabelRes(HookActionType) -> StringRes` maps domain action types to
  localized presentation.
- `HookActionEditor(actionConfig, ...)` dispatches UI through an exhaustive
  sealed-type `when` boundary.

### 3. Contracts

- A Hook card opens the editor as a whole-card action. The enabled Switch has
  its own handler and remains the primary always-visible state control.
- Delete lives behind an overflow action and confirmation; unnamed legacy Hooks
  use the same localized fallback in the list and confirmation.
- Supporting text is `model · action type`; action labels come from
  `HookActionConfig.actionType`, not a hard-coded current subtype.
- Top-level action Select exposes only `MANAGE_CONVERSATION_TAGS` and
  `SYNC_MEMORY_TABLE`. Tag management uses one allowlist plus evaluation prompt
  strategy; no mode segmented control, dual-permission bar, condition dropdown,
  or Issue evidence form gate.
- The editor exposes Basic, Runtime, Rules, and Action sections. Invalid name,
  model, trigger, prompt, or action state must show visible field/section errors.
- Evaluation prompt UI defaults to collapsed/compressed preview and expands on
  demand. Expanded editors keep a bounded visible height with multiline
  scrolling. UI changes must not modify Hook execution, lease, or exactly-once
  persistence contracts.

### 4. Validation & Error Matrix

- Blank name -> name error; save disabled.
- Missing/non-CHAT model -> model error; save disabled.
- Unsupported trigger -> trigger error; save disabled.
- Blank prompt -> prompt error; save disabled.
- No available action target -> action error; save disabled.
- Deleted/stale tag IDs -> unavailable-target error with an explicit cleanup
  action; never silently persist an invisible target set.

### 5. Good/Base/Bad Cases

- Good: derive all errors once and bind both field messages and save enabled
  state to the same validation object.
- Base: `ManageConversationTags` renders through the sealed action editor and
  saves `manage_conversation_tags`; legacy Add/Transition configs normalize on
  load and save.
- Bad: `canSave = name.isNotBlank()` while fields apply separate rules.
- Bad: render `assistant_hook_action_add_tag` directly in every list row.
- Bad: place edit and destructive delete icon buttons together as permanent
  trailing actions.

### 6. Tests Required

- JVM tests for valid draft, each required-field error, stale tag targets, and
  action-type label mapping.
- Compile resources and Kotlin after resource/UI changes.
- When Compose UI test infrastructure is available, assert Switch/overflow
  interaction does not trigger the parent card navigation.
- Existing Hook serialization, parser, final-success gate, and lease tests
  remain green.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val canSave = name.isNotBlank() && prompt.isNotBlank()
Text(stringResource(R.string.assistant_hook_action_add_tag))
```

#### Correct

```kotlin
val validation = validateHookDraft(name, model, trigger, prompt, action, tags)
Text(stringResource(hookActionLabelRes(action.actionType)))
SaveButton(enabled = validation.canSave)
```

Relationship writes remain atomic, and Hook execution starts only from the persisted logical-turn gate.

## Scenario: Action-specific Hooks and memory-table synchronization

### 1. Scope / Trigger

Use this contract when adding a Hook Action, changing frozen Hook input, executing manual preview/run/retry, or
writing structured memory-table data from a Hook.

### 2. Signatures

- `HookActionHandler` owns `prepare`, action-specific `parse`, optional `preview`, and `execute`.
- `HookActionRegistry.requireHandler(HookActionType)` is the only Action dispatch boundary.
- `freezeMemoryTableHookMessages(conversation, cutoffMessageId, config)` returns bounded selected-branch text.
- `MemoryTableHookSyncCommitter.commit(executionId, leaseToken, prepared, output)` owns the Room transaction.
- Room v42 adds generalized `hook_executions` audit fields and durable `hook_action_cursors`.

### 3. Contracts

- Dispatcher and Registry must not cast requests/results to one concrete Action. Registry
  registers only `MANAGE_CONVERSATION_TAGS` and `SYNC_MEMORY_TABLE`. Legacy
  `add_conversation_tag` / `transition_conversation_tags` decode and normalize to
  `ManageConversationTags`; manage allowlist hash material stays order-invariant.
- Sync model input contains only selected, visible, active-branch USER/ASSISTANT text through the frozen cutoff;
  SYSTEM/TOOL content, alternate branches, hidden nodes, and later messages are excluded.
- The Sync provider receives no tools and returns exactly `decision`, `baseRevision`, `operations`, and `reason`.
  Target ID, scope, schema, update policy, and authorization remain local authority.
- Automatic Sync is preflighted before provider invocation and rechecked before execution: global table gate,
  Assistant table gate, global auto permission, Hook enabled/config hash/version, and Action `automatic`.
- Manual preview performs no Room write. Apply Preview and Run Now create ordinary manual Hook history and recheck
  the active cutoff, current Hook configuration, target, frozen schema, and revision.
- Dispatcher unknown failures use stage-specific fallbacks: provider call -> `MODEL_REQUEST_FAILED`, parser ->
  `SCHEMA_MISMATCH`, preparation/action/Room -> `ACTION_FAILED`. Explicit `HookOutputException.code` wins.
- A committed Sync handler returns `HookActionResult.Terminalized`; Dispatcher must not perform a second success write.
- Successful idempotency cursors outlive bounded Hook history and have no foreign key to prunable run/execution rows.

### 4. Validation & Error Matrix

- Hook disabled/config changed -> `HOOK_DISABLED`; zero provider call when detected at preflight, zero write when
  detected after provider output.
- Global/Assistant table disabled -> `MEMORY_TABLE_DISABLED`; zero write.
- Automatic permission/Action automatic disabled -> corresponding stable error; zero provider call/write.
- Inactive cutoff -> `SOURCE_MESSAGE_NOT_ACTIVE`; no preview or execution.
- Extra/missing output keys, non-integer revision, oversized response/operation list -> `SCHEMA_MISMATCH`.
- Invalid table/column/type/key/update policy -> `MEMORY_TABLE_INVALID_OPERATIONS`; all operations rejected.
- Target deleted/foreign/global/scope changed -> stable target/scope error; zero write.
- Revision or frozen-schema mismatch -> `MEMORY_TABLE_REVISION_CONFLICT` or `MEMORY_TABLE_TARGET_CHANGED`; zero write.
- Existing cursor by idempotency key, natural source key, or execution ID -> terminal `SKIPPED` /
  `IDEMPOTENT_REPLAY`, preserving the committed result revision.
- Retry is allowed only for uncommitted failed Sync executions with unchanged Hook configuration; otherwise
  `RETRY_NOT_ALLOWED`.

### 5. Good/Base/Bad Cases

- Good: freeze one bounded branch, call the provider without tools, validate operations locally, then atomically write
  payload/snapshot/cursor/execution/run.
- Base: model returns `skip`; execution becomes `SKIPPED` with audit fields but no payload revision or cursor write.
- Good: user disables the Hook while a slow provider request is running; post-provider gate returns `SKIPPED`.
- Bad: reuse the current template schema after the model answered without comparing it to the frozen schema.
- Bad: catch every handler exception as `MODEL_REQUEST_FAILED` or mark success in Dispatcher after the handler commits.

### 6. Tests Required

- Literal legacy `add_conversation_tag` / `transition_conversation_tags` JSON normalize to manage.
- Sync hash mutation for every Action field plus model/prompt; manage allowlist order invariance.
- Frozen-context branch/hidden/role/cutoff/count/Unicode character-bound tests.
- Sync strict parser exact-key, type, size, operation-limit, and unauthorized-field tests.
- Dispatcher stage-error and post-provider gate regressions when those paths change.
- Hook editor Sync target/scope/role/limit validation and action label mapping.
- Compile production, JVM test, and androidTest sources after cross-layer Hook changes.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val parsed = HookOutputParser.parse(raw) // tag-specific parser in generic dispatcher
handler.execute(parsed)
hookRepository.completeSuccess(executionId) // second transaction after Sync commit
```

#### Correct

```kotlin
val handler = actionRegistry.requireHandler(hook.actionConfig.actionType)
val prepared = handler.prepare(hook, freezeContext)
val parsed = handler.parse(raw, prepared)
when (handler.execute(executionId, leaseToken, prepared, parsed)) {
    HookActionResult.Terminalized -> Unit
    else -> finishFromAction(...)
}
```

## Scenario: Evidence-gated conversation tag transitions (superseded by #150)

### Status

**Superseded.** Product decision D2/B1 removed the Issue-evidence hard gate. Tag strategy lives in the evaluation
prompt plus multi-op model output under `ManageConversationTags`. Keep historical Transition execution rows and
`GITHUB_ISSUE_EVIDENCE_NOT_FOUND` error strings for history display only; prepare paths must not call
`detectGitHubIssueCompletionEvidence`.

### Replacement contract

- Config: `HookActionConfig.ManageConversationTags(allowedTagIds)`.
- Runtime: `ManageConversationTagsHookAction` + `ManageConversationTagsHookOutputParser` +
  `ConversationTagHookCommitter.commitManageTags`.
- Output keys: exactly `decision`, `operations`, `reason`; ops are allowlist-scoped add/remove; any illegal op fails
  closed with zero writes.
- Legacy Transition JSON normalizes to manage allowlist `{addTagId, removeTagId}` and drops filter-driven gating.
