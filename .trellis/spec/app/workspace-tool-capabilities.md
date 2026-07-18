# Workspace Tool Capability Contract

## 1. Scope / Trigger

Use this contract whenever workspace binding, shell readiness, workspace tool counts, workspace tool injection,
workspace reminders, or workspace selection UI changes.

## 2. Signatures

```kotlin
fun resolveWorkspaceToolCapability(
    workspaceId: Uuid?,
    workspaces: List<WorkspaceEntity>,
    readOnly: Boolean = false,
): WorkspaceToolCapability

fun decideWorkspaceEnable(workspaces: List<WorkspaceEntity>): WorkspaceEnableDecision
suspend fun SettingsStore.updateAssistantWorkspaceBinding(
    assistantId: Uuid,
    workspaceId: Uuid?,
): AssistantWorkspaceBindingUpdateResult
```

`WorkspaceToolCapability` exposes `configured`, resolved `workspace`, `available`, `availableToolNames`, and a stable
`WorkspaceUnavailableReason`. `AssistantWorkspaceBindingUpdateResult` is `UPDATED` or `NOT_FOUND`.

## 3. Contracts

- `configured` means Assistant has a non-null binding; `available` additionally requires the entity to exist and its
  normalized shell status to be `READY`.
- Normal available tools are `workspace_read_file`, `workspace_write_file`, `workspace_edit_file`, and
  `workspace_shell`; read-only capability exposes only `workspace_read_file`.
- DISABLED, INSTALLING, BROKEN, unknown status, missing entity, and invalid binding are fail-closed with no tools.
- UI counts, tool rows, `ChatService`, and `WorkspaceReminderTransformer` consume the same capability resolver.
- Enabling with zero workspaces opens management/creation; one valid workspace binds directly; multiple valid
  workspaces require explicit selection. Invalid IDs never throw or write. Selection cancel does not write.
- Disabling only sets `Assistant.workspaceId = null`; it never deletes a workspace or rootfs.
- Binding persistence updates only `workspaceId` for the current Assistant ID inside the DataStore edit transaction,
  preserving fields written by newer Settings state. A missing target returns `NOT_FOUND` without writing.
- UI binding saves are serialized and stale queued intents are skipped; feedback is buffered until a collector consumes it.

## 4. Validation & Error Matrix

- Null binding -> `UNCONFIGURED`, zero tools.
- Unknown/malformed status -> `UNKNOWN`, zero tools.
- Missing entity -> `MISSING`, zero tools.
- READY + normal -> four tool names; READY + read-only -> one read tool.
- 0/1/N valid workspaces -> create/manage, direct bind, or selection respectively.
- Cancel -> no persistence call; invalid UUID -> failure feedback and no persistence call.
- Persistence target missing -> `NOT_FOUND`, failure feedback, unchanged stored JSON.
- Persistence exception or cancellation -> failure/rethrow; no optimistic committed binding.

## 5. Good / Base / Bad Cases

- Good: page and runtime both call `resolveWorkspaceToolCapability` before displaying/injecting tools.
- Good: `WorkspaceSelectSheet` supplies a selected/radio semantic on the whole row while the caller interprets null
  according to its flow (cancel for enable flow, unbind for file picker).
- Base: a configured non-READY workspace remains checked so the user can navigate to repair it.
- Bad: `workspaceId != null` is used as the effective tool count or runtime gate.
- Bad: `workspaces.firstOrNull()` silently binds a workspace when there are multiple candidates.
- Bad: replacing the complete Assistant object for a one-field workspace change, or treating a missing target as success.

## 6. Tests Required

- Table-driven capability tests for null, missing, every known status, unknown/whitespace status, normal/read-only.
- Enable/selection tests for 0/1/N, invalid IDs, cancel, and order preservation.
- Persistence tests prove latest stored Assistant fields survive a workspace-only update and missing targets do not write.
- UI statistics and ChatService/reminder tests compare the same effective tool names for READY and non-READY cases.
- Resource tests ensure every locale contains new workspace capability keys and Android apostrophe escapes remain valid.

## 7. Wrong vs Correct

### Wrong

```kotlin
if (assistant.workspaceId != null) {
    enabledTools += 4
    workspaceRepository.getById(assistant.workspaceId)?.let { createTools(it) }
}
```

### Correct

```kotlin
val capability = resolveWorkspaceToolCapability(assistant.workspaceId, workspaces, readOnly)
val tools = capability.availableToolNames
    .mapNotNull { name -> toolFactory.create(name, capability.workspace) }
```
