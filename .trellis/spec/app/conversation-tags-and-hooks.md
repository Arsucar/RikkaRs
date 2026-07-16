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
- `HookRepository.claimQueued(...)`, `isLeaseActive(...)`, terminal completion methods, and `invalidateLeaseAndFailTimeout(...)` own execution state transitions.

### 3. Contracts

- Tags are a global user-managed vocabulary. Runtime callers may reference an existing `tagId`; they must never create a tag from a name or unknown ID.
- Tag names use NFC, trimmed/collapsed whitespace, locale-independent normalized uniqueness, and Unicode code-point limits.
- Tag relations use a composite key and cascading foreign keys. Adding/removing the same relation is idempotent.
- Selected tag IDs are OR with each other and AND with assistant/folder/archive/search filters. Filtering happens in SQL before paging.
- Whole-Conversation saves must not carry tag collections; otherwise concurrent relationship writes can be lost.
- A Hook runs only after a persisted logical turn reaches final assistant success with no resumable pending tool.
- Hook model requests use a frozen in-memory message/prompt/config snapshot. Full prompt, message snapshot, provider output, headers, and credentials never enter Hook Room tables.
- Hook output is one JSON object whose key set is exactly `decision`, `tagId`, and `reason`.
- A timeout invalidates the lease before cancelling work. Parser and action writes must recheck the active lease so late results cannot write tags or overwrite terminal state.
- `generationDoneFlow` remains a UI notification mechanism and is not a Hook success or history source.

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
- The editor exposes Basic, Runtime, Rules, and Action sections. Invalid name,
  model, trigger, prompt, or action state must show visible field/section errors.
- Prompt editors must have a bounded visible height while retaining multiline
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
- Base: `AddConversationTag` renders through the sealed action editor and saves
  the same serialized subtype as before.
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
