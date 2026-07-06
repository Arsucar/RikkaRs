# App Spec Index

> Code-specs for app-layer AI runtime, service wiring, and Compose settings.

## Specs

| Spec | Purpose |
|------|---------|
| [Subagent Runtime](./subagent-runtime.md) | Subagent control fields, runtime boundaries, and validation points |
| [Skill Runtime](./skill-runtime.md) | Skill file access, private-skill visibility, and workspace mount contracts |
| [Conversation Model Resolution](./conversation-model-resolution.md) | Conversation-level chat model override and fallback contracts |
| [Android Backup Rules](./android-backup-rules.md) | Backup allow-list contracts for app-owned user files |

## Pre-Development Checklist

- For subagent settings, trace UI field -> `Assistant` / `SubagentProfile` -> `ChatService` -> `SubagentHost` -> `GenerationHandler`.
- Check whether a field controls the main agent, subagents, or both before wiring it into generic generation code.
- For skills, distinguish global skill storage from assistant-private storage before exposing files to tools or workspace mounts.

## Quality Check

- Verify subagent field changes with focused unit tests under `app/src/test/.../subagent/`.
- Verify skill path changes with focused tests for normal reads, traversal rejection, symlink allowlists, and private-skill visibility.
- For app module runtime changes, run `.\gradlew :app:compileDebugKotlin --no-daemon`.
- For backup XML-only changes, run `.\gradlew :app:processDebugResources --no-daemon --no-configuration-cache --console=plain`.
