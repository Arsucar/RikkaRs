# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`
- Conversation entity/serialization/migration paths.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`

## Model Resolution

Effective chat model:

1. `conversation.chatModelId`
2. `assistant.chatModelId`
3. `settings.chatModelId`

`updateAssistantModel` remains a settings/default-model operation, not a chat-page override operation.

## Migration

Existing conversations receive `chatModelId = null`. This preserves current default resolution after migration, except future chat-page switches no longer mutate the assistant.
