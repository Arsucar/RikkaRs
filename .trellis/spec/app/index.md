# App Spec Index

> Code-specs for app-layer AI runtime, service wiring, and Compose settings.

## Specs

| Spec | Purpose |
|------|---------|
| [Subagent Runtime](./subagent-runtime.md) | Subagent control fields, runtime boundaries, and validation points |
| [Skill Runtime](./skill-runtime.md) | Skill file access, private-skill visibility, and workspace mount contracts |
| [Conversation Model Resolution](./conversation-model-resolution.md) | Conversation-level chat model override and fallback contracts |
| [Android Backup Rules](./android-backup-rules.md) | Backup allow-list contracts for app-owned user files |
| [Conversation Tags and Hooks](./conversation-tags-and-hooks.md) | Global conversation-tag relations and assistant Hook execution contracts |
| [UI Localization](./ui-localization.md) | Android UI string-resource and Simplified Chinese delivery contract |
| [Assistant Web Search](./assistant-web-search.md) | Assistant-level search persistence, migration, request wiring, and Web API contracts |
| [Workspace Media Preview](./workspace-media-preview.md) | Workspace file viewing, FileProvider handoff, and LINUX read-only contracts |
| [Memory Capabilities](./memory-capabilities.md) | Independent normal-memory and memory-table persistence, runtime gates, and UI contracts |
| [Conversation Persistence](./conversation-persistence.md) | Lightweight summaries, paged full reads, bounded encoding batches, and metadata-only diagnostics |
| [Workspace Tool Capabilities](./workspace-tool-capabilities.md) | Shared workspace binding, readiness, tool availability, and persistence contracts |
| [Assistant Tool Capability Catalog](./tool-capability-catalog.md) | Stable tool IDs, availability reasons, approvals, and pure snapshots |
| [Assistant Tool Permissions](./tool-permissions.md) | Four-state per-tool policy and runtime enforcement |
| [Tool Diagnostics and Connection Status](./tool-diagnostics-and-connection-status.md) | Read-only diagnostics, redaction, probes, and revision-safe status |

## Pre-Development Checklist

- For subagent settings, trace UI field -> `Assistant` / `SubagentProfile` -> `ChatService` -> `SubagentHost` -> `GenerationHandler`.
- Check whether a field controls the main agent, subagents, or both before wiring it into generic generation code.
- For skills, distinguish global skill storage from assistant-private storage before exposing files to tools or workspace mounts.
- For conversation tags or Hooks, keep relationship writes outside whole-Conversation saves and preserve the logical-turn/lease state machine.
- For Assistant-level settings migrated from a global preference, migrate DataStore and restored backup JSON together; runtime reads must use the conversation Assistant.
- For Workspace file actions, preserve `TextFileUtil` text/Markdown ownership, use cache + FileProvider for external viewing, and enforce LINUX read-only below the UI.
- For memory settings, resolve normal memory and memory-table capabilities independently; never wrap table loading, injection, or tools in `Assistant.enableMemory`.
- For Conversation persistence, keep summary/diff queries lightweight, preserve complete full reads, and bound temporary encoded batches without truncating history.
- For every new or changed user-visible UI string, provide a string resource and a real Simplified Chinese translation before delivery.
- For assistant tool summaries, keep configured/available/effective distinct and construct snapshots without I/O.

## Quality Check

- Verify subagent field changes with focused unit tests under `app/src/test/.../subagent/`.
- Verify skill path changes with focused tests for normal reads, traversal rejection, symlink allowlists, and private-skill visibility.
- For app module runtime changes, run `.\gradlew :app:compileDebugKotlin --no-daemon`.
- For Assistant search changes, run migration/backup/targeted-update tests and verify Android plus Web clients use the Assistant ID.
- For Workspace media changes, test file classification/MIME fallback and LINUX write rejection, then run device installation when available.
- For memory capability changes, parameterize all four normal/table combinations and verify Preview and real generation share the same prepared request path.
- For Conversation persistence changes, test summary sentinel isolation, exact batch/page boundaries, 65+ node full reads, and metadata-only diagnostic formatting.
- For backup XML-only changes, run `.\gradlew :app:processDebugResources --no-daemon --no-configuration-cache --console=plain`.
- For tool catalog changes, cover dynamic sources and status reasons, then compare effective names at the generation boundary.
