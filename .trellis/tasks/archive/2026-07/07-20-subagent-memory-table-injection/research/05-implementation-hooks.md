# Research: Implementation hooks

1. `SubagentProfile` + field
2. Pure resolver next to profile or transformers package
3. `SubagentHost` needs templates/documents source — inject via constructor deps if Host already has repos, or pass resolved lists from spawn tool builder (`SubagentTools.kt`) where ChatService-like context exists
4. Trace spawn call chain: `SubagentTools` → `SubagentHost` to find best place for repository access

## Call chain note
Check `SubagentTools.kt` and DI for `SubagentHost` construction for available `MemoryTableRepository` / settings / conversation id.
