# Conversation Variables Contract

## Scenario: SillyTavern-compatible conversation variables (#217 / #216)

### 1. Scope / Trigger

- Trigger: assistant-gated variable macros on send, MVU blocks on generation finish, drawer edit, branch switch/fork.
- Applies to: `Assistant.enableVariableSystem`, `Conversation.variables`, `MessageNode.variableSnapshots`, transformers, Room 49→50, drawer UI.

### 2. Signatures

- `Assistant.enableVariableSystem: Boolean = false`
- `Assistant.isVariableSystemEnabled(): Boolean`
- `Conversation.variables: Map<String, String>`
- `MessageNode.variableSnapshots: Map<String, Map<String, String>>` // messageId → vars
- `Conversation.withVariableSnapshot(messageId, variables)`
- `ConversationRepository.updateConversationVariables(id, transform)`
- `ChatService.updateConversationVariables(id, transform)`
- `VariableMacroTransformer` (input, after PromptInjection, before Placeholder)
- `UpdateVariableOutputTransformer` (output `onGenerationFinish`)
- Room `Migration_49_50`

### 3. Contracts

- **Default off**: macros/MVU/drawer are no-ops or hidden; ST magic strings pass through unchanged.
- **Input order**: `PromptInjection → VariableMacro → Placeholder`.
- **Macros**: `getvar` → value or `""`; `setvar`/`addvar` mutate working map and self-remove from text; nested macros inner-first; `getglobalvar` → `""` (MVP).
- **Limits**: max variable count and max value length enforced (sanitize/truncate).
- **MVU**: parse `<UpdateVariable>` + JSON Patch (`/name` add|replace|remove string); success strip block; fail keep text.
- **Working map**: generation starts from `conversation.variables`; after finish snapshot onto last assistant message id in `variableSnapshots`.
- **Branch**: `selectMessageNode` restores snapshot for selected message id when present; fork copies `variables` (+ node snapshots).
- **Atomic write**: UI/macros/MVU persistence under session `persistenceMutex` + Room transaction; no unlocked RMW.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Feature off | passthrough / no drawer / no MVU apply |
| Undefined getvar | empty string |
| Malformed MVU | keep original text |
| Over limit | sanitize; do not crash generation |
| Missing snapshot on branch switch | keep current working variables |

### 5. Good / Base / Bad Cases

- Good: enable → setvar in injection → getvar expands; MVU patch updates and strips; regenerate then switch branch restores prior vars.
- Base: new assistant/conversation has feature off and empty maps.
- Bad: VariableMacro before PromptInjection (macros never see injected text).
- Bad: conversation-only map without per-message snapshots (branch pollution).

### 6. Tests Required

- `VariableMacroTransformerTest` — set/get/add/nested/undefined/disabled
- `UpdateVariableOutputTransformerTest` — strip success / keep fail / patch ops
- Device: enable on assistant, ST preset smoke, drawer CRUD

### 7. Wrong vs Correct

#### Wrong

```kotlin
// inputTransformers: VariableMacro before PromptInjection
```

#### Correct

```kotlin
PromptInjectionTransformer,
VariableMacroTransformer,
PlaceholderTransformer,
```

**Related**: transformers pipeline, conversation persistence, #215 experimental feature page (future migration of gate).
