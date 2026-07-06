# Conversation Model Resolution Spec

> Code-spec for resolving the chat model used by a conversation.

## Scenario: Conversation Chat Model Override

### 1. Scope / Trigger

- Trigger: changing chat model selection, chat generation, chat input model display, or conversation persistence.

### 2. Signatures

- `Conversation.chatModelId: Uuid?`
- `ConversationEntity.chatModelId: String`
- `fun Settings.resolveChatModelId(conversation: Conversation? = null, assistant: Assistant = ...): Uuid`
- `fun Settings.getCurrentChatModel(conversation: Conversation? = null): Model?`

### 3. Contracts

- Effective chat model order is: `conversation.chatModelId`, then `assistant.chatModelId`, then `settings.chatModelId`.
- `Conversation.chatModelId == null` means no conversation override.
- New blank conversations must keep `chatModelId == null`.
- Chat-page model switching updates only the current conversation.
- Assistant settings and web settings remain the paths for changing assistant defaults.
- Memory scope remains assistant/global based; model override must not alter memory keys.

### 4. Validation & Error Matrix

- Existing DB row without `chat_model_id` -> migration writes `''` -> domain model gets `null`.
- Empty new conversation with no override -> do not persist.
- Empty new conversation with override -> persist so the override survives before the first message.
- Missing model id -> model lookup returns `null`; send path should keep existing "select model" behavior.

### 5. Good/Base/Bad Cases

- Good: Conversation A selects model X; Assistant default remains unchanged; Conversation B keeps fallback default.
- Base: Conversation has null override; assistant/global fallback behaves as before.
- Bad: `ChatVM.setChatModel` mutates `Assistant.chatModelId`.

### 6. Tests Required

- Unit test fallback order: conversation > assistant > global.
- Unit test new conversation default has no model override.
- Compile or Room/KSP validation after adding the DB column and schema.

### 7. Wrong vs Correct

#### Wrong

```kotlin
settings.getCurrentAssistant().chatModelId ?: settings.chatModelId
```

#### Correct

```kotlin
settings.resolveChatModelId(conversation)
```
