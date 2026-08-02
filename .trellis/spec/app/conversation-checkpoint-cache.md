# Conversation Checkpoint Cache Contract

## Scenario: Experimental mid-generation checkpoints (#220)

### 1. Scope / Trigger

- Trigger: multi-step tool generation while `Settings.enableCheckpointCache` is on.
- Applies to Settings keys, Conversation/Entity metadata, GenerationHandler `stepIndex`, ChatService checkpoint writer, recovery toast on hydrate.

### 2. Signatures

- `Settings.enableCheckpointCache: Boolean = false`
- `Settings.checkpointStepInterval: Int = 8` (coerced to `{4,8,16,32}`)
- `SettingsStore.updateCheckpointCache(enabled: Boolean? = null, stepInterval: Int? = null)`
- `shouldWriteCheckpoint(enable, stepIndex, lastCheckpointStep, interval): Boolean`
- `Conversation.checkpointStep: Int?`, `Conversation.isCheckpointSnapshot: Boolean`
- `ConversationRepository.updateConversation(conversation, skipFts: Boolean = false)`
- `GenerationChunk.Messages(messages, stepIndex: Int = 0)`
- Room migration `Migration_48_49` (DB 48→49)

### 3. Contracts

- **Default off**: no extra mid-generation saves; existing save points unchanged.
- **Trigger**: when enabled and `(stepIndex - lastCheckpointStep) >= N`, schedule async checkpoint under `persistenceMutex`.
- **Checkpoint write**: mark `isCheckpointSnapshot=true`, `checkpointStep=stepIndex`; **Room only** — must **not** overwrite live streaming `session.state` with a stale copy.
- **FTS**: checkpoint uses `skipFts=true`; Final success/cancel/stop uses normal full FTS path after `asFinalSnapshot()` (clears checkpoint flags).
- **Failure**: log only; never throw into generation.
- **Recovery**: on hydrate, if `isCheckpointSnapshot` and not generating → one-shot toast; then clear durable flags so reopen does not re-prompt.
- **Settings write**: partial DataStore keys only (#202); coerce interval.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Setting off | no checkpoint writes |
| Invalid interval | coerce to nearest allowed / default 8 |
| Checkpoint IO fail | log; generation continues |
| Final after checkpoint | Final flags; full FTS |
| Concurrent stream + checkpoint | live state untouched by checkpoint writer |

### 5. Good / Base / Bad Cases

- Good: N=8, step 7 then 15 produce two checkpoints; kill process mid-run; reopen shows recovery toast once.
- Base: default install never writes checkpoint columns as true.
- Bad: `updateConversation(checkpointed)` also applied to live session during stream (UI rollback).

### 6. Tests Required

- `CheckpointCacheSettingsTest` — coerce + `shouldWriteCheckpoint` math
- `CheckpointCacheTest` — Final clear + recovery identity
- Device: enable, multi-step tool gen, force-stop, reopen toast

### 7. Wrong vs Correct

#### Wrong

```kotlin
session.updateConversation(checkpointed) // overwrites live stream with older snapshot
```

#### Correct

```kotlin
conversationRepo.updateConversation(checkpointed, skipFts = true)
// leave session.state to streaming updates
```

**Related**: [Conversation Persistence](./conversation-persistence.md), [Chat Generation Keep-Alive](./chat-generation-keepalive.md).
