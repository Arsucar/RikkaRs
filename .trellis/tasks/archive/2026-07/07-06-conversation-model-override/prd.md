# Conversation chat model override

## Goal

Allow each conversation to optionally override the chat model without mutating the assistant default model.

## Issues

- #31: conversation-level model override; new conversations should use assistant default model.

## Requirements

- Add nullable `Conversation.chatModelId`.
- `null` means resolve model from `assistant.chatModelId ?: settings.chatModelId`.
- Chat-page model switching updates only the current conversation override.
- Assistant settings and web settings remain the only paths for changing assistant default model.
- New conversations keep `chatModelId == null`.
- Memory behavior remains assistant/global scoped and does not change based on model override.

## Acceptance Criteria

- [ ] Changing model in conversation A does not change `Assistant.chatModelId`.
- [ ] Conversation B under the same assistant is unaffected unless it has its own override.
- [ ] New conversations start with `chatModelId == null`.
- [ ] Chat input display and generation model resolution use the same fallback chain.
- [ ] Existing memory read/write keys are unchanged.
- [ ] Room/data migration preserves existing conversations.
