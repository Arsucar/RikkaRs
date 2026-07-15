# Search Memory Isolation Design

## Authorization Boundary

- Every memory operation receives the acting assistant id. Effective reads are GLOBAL plus matching ASSISTANT only.
- Create from `memory_tool` defaults to ASSISTANT/current assistant. GLOBAL creation is explicit, never inferred from missing identity.
- Read/update/delete by id first resolves an authorized effective item; naked repository mutation is not exposed to AI tools.
- Memory-table template/document operations reuse #122 scoped repository contracts.

## Lifecycle

- Assistant deletion removes only that assistant's ASSISTANT MemoryEntity rows, templates and documents; GLOBAL and other assistants remain.
- `enableMemory=false` disables prompt injection without broadening read scope.
- SearchTools continue returning tool output only and do not implicitly persist results.

## Optional Search Settings

- Per-assistant web-search settings are implemented only if existing Assistant JSON can carry defaults without splitting global behavior or requiring risky migration. This is not allowed to delay Must isolation.
