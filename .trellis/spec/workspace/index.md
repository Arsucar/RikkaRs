# Workspace Layer Spec Index

> Code-specs for the `workspace` module — sandboxed file system and shell execution.

---

## Specs

| Spec | Purpose |
|------|---------|
| [Shell Policy](./shell-policy.md) | Heuristic command validation patterns and conventions |
| [Storage Policy](./storage-policy.md) | Workspace files storage contracts and Git pack-write support boundaries |

---

## Key Contracts

- `evaluateShellCommand(command: String): ShellCommandVerdict` — returns `Allowed` or `Rejected(userMessage)`
- Heuristic-only, not a security boundary; real isolation via `WorkspaceManager` cwd limits + process permissions
