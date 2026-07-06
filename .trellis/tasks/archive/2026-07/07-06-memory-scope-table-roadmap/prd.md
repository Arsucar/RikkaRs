# Memory scope and table roadmap

## Goal

Sequence memory-related issues so existing per-memory global scope work can ship independently before the larger generic memory-table feature.

## Issues

- #39: support per-memory global scope instead of assistant-level whole-pool global switch.
- #41: add generic configurable memory tables with schema, gated injection, tools, sync jobs, and UI.

## Requirements

- Treat #39 as the first concrete implementation target.
- Treat #41 as a larger roadmap/epic unless explicitly narrowed to P0.
- Avoid concurrent implementation of #39 and #41 against the same database/repository/UI surfaces.
- Preserve current flat `memory_tool` behavior while extending scope semantics.
- For #41, default all new table memory behavior off and zero-intrusion when disabled.

## Acceptance Criteria

- [ ] #39 has an implementation-ready design before any memory-table work starts.
- [ ] #41 is split into phases or a child task before implementation.
- [ ] Room migrations are sequenced so flat memory and table memory changes do not collide.
- [ ] Existing assistant/global memory behavior remains compatible or has a documented migration path.
- [ ] Disabled memory-table mode adds no system prompt, tool, background job, token cost, or document read.

## Out of Scope

- Implementing the full memory-table feature in one PR.
- Changing memory partitioning by model or conversation as part of #31.
