# App Spec Index

> Code-specs for app-layer AI runtime, service wiring, and Compose settings.

## Specs

| Spec | Purpose |
|------|---------|
| [Subagent Runtime](./subagent-runtime.md) | Subagent control fields, runtime boundaries, and validation points |

## Pre-Development Checklist

- For subagent settings, trace UI field -> `Assistant` / `SubagentProfile` -> `ChatService` -> `SubagentHost` -> `GenerationHandler`.
- Check whether a field controls the main agent, subagents, or both before wiring it into generic generation code.

## Quality Check

- Verify subagent field changes with focused unit tests under `app/src/test/.../subagent/`.
- For app module runtime changes, run `.\gradlew :app:compileDebugKotlin --no-daemon`.
