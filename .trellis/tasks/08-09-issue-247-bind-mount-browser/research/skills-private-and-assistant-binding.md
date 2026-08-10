# Research: skills_private, SkillManager, assistant.workspaceId binding

- **Query**: How private skills mount works and how workspace↔assistant binding is looked up
- **Scope**: internal
- **Date**: 2026-08-09

## Findings

### Files Found

| File Path | Description |
|---|---|
| `app/.../data/files/SkillManager.kt` | `getAssistantSkillsDir`, private skill paths |
| `app/.../service/ChatService.kt` | `assistantPrivateSkillMounts` session mounts |
| `app/.../data/model/Assistant.kt` | `workspaceId: Uuid?` |
| `app/.../data/datastore/PreferencesStore.kt` | binding write + `getCurrentAssistant` |
| `app/.../ui/pages/assistant/detail/AssistantDetailVM.kt` | `saveWorkspaceBinding` / `persistWorkspaceBinding` |
| `app/.../ui/components/ai/WorkspaceSelectSheet.kt` | UI bind/unbind workspace |
| `app/.../data/repository/WorkspaceRepository.kt` | `cleanupAssistantReferences` on workspace delete |
| `app/.../data/model/WorkspaceToolCapability.kt` | capability from `assistant.workspaceId` |

### Code Patterns

#### Private skills host directory

```kotlin
// SkillManager.kt:60-66
fun getAssistantSkillsDir(assistantId: Uuid, createIfMissing: Boolean = true): File {
    val dir = context.filesDir
        .resolve(FileFolders.ASSISTANT_SKILLS)  // "assistant_skills"
        .resolve(assistantId.toString())
    if (createIfMissing && !dir.exists()) dir.mkdirs()
    return dir
}
```

- Global skills: `getSkillsDir()` → `filesDir/skills` (mounted at `/skills`).
- Private skills: `filesDir/assistant_skills/<assistantId>/` (mounted at `/skills_private`).

#### ChatService session mounts (same shape browser should mirror)

```kotlin
// ChatService.kt:2117-2137
private fun assistantPrivateSkillMounts(assistantId: Uuid, createDirectories: Boolean = true): AssistantSkillMounts {
    val assistantSkillsDir = skillManager.getAssistantSkillsDir(assistantId, createDirectories)
    val skillSharedDir = skillManager.getSkillSharedDir(createDirectories)
    return AssistantSkillMounts(
        knownMounts = listOf(
            WorkspaceKnownMount(
                target = "/skills_private",
                source = assistantSkillsDir,
                allowedSymlinkRoots = listOf(skillSharedDir),
            )
        ),
        bindMounts = listOf(
            WorkspaceBindMount(
                source = assistantSkillsDir,
                target = "/skills_private",
            )
        ),
    )
}
```

- Wired into tools as `extraBindMounts` + `knownMounts` in `createWorkspaceToolsIfReady` (around 2093–2111).
- Global `/skills` and `/upload` also appear as `WorkspaceKnownMount` there; `/tool_outputs` is global constructor mount (PRoot + resolveRootfsPath) but not always listed in knownMounts for tools.

#### Assistant.workspaceId binding

```kotlin
// Assistant.kt
val workspaceId: Uuid? = null
```

**Write path:**

1. UI: `AssistantDetailVM.saveWorkspaceBinding(workspaceId)`
2. `persistWorkspaceBinding` → `settingsStore.updateAssistantWorkspaceBinding`
3. `MutablePreferences.writeAssistantWorkspaceBinding` maps assistants and sets `workspaceId` on matching id.

**Read path for reverse lookup (issue AC2):**

```text
settings.assistants.filter { it.workspaceId?.toString() == workspace.id }
```

No dedicated repository method exists today for “assistants bound to workspace X”. Closest:

```kotlin
// WorkspaceRepository.cleanupAssistantReferences — on workspace delete
if (assistant.workspaceId?.toString() == workspaceId) {
    assistant.copy(workspaceId = null)
}
```

#### Current assistant fallback

```kotlin
// PreferencesStore.kt:1772-1775
fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId && !it.isArchived }
        ?: this.assistants.first { !it.isArchived }
}
```

Issue policy for `/skills_private` entry assistant:

| Bound assistants for this workspace | Behavior |
|---|---|
| Exactly 1 | Use that assistant id → `getAssistantSkillsDir(id)` |
| 0 | Fall back to `getCurrentAssistant()`, UI label “当前助手” |
| 2+ | UI selection (default current assistant); do not silently pick one |

- Do not cache binding snapshot across re-enter; re-resolve from settings each refresh.
- Archived assistants: treat as absent (align with `getCurrentAssistant` filter).

#### Capability / tools use of workspaceId

```kotlin
// ToolCapabilityCatalog / WorkspaceToolCapability
resolveWorkspaceToolCapability(workspaceId = assistant.workspaceId, workspaces = ...)
```

- Forward: assistant → workspaceId → workspace entity.
- Reverse (needed for browser): workspace id → list of assistants — **must be implemented** via settings filter.

### Related Specs

- None found.

## Caveats / Not Found

- `/skills_private` is never in `RepositoryModule` global `bindMounts`; browser must inject it as dynamic extra mount (or path special-case) with assistant context.
- Workspace detail page currently only has workspace `id`; no assistant context unless injected via SettingsStore / SkillManager.
- Multiple assistants can bind the same workspace (no uniqueness enforced in `writeAssistantWorkspaceBinding`).
