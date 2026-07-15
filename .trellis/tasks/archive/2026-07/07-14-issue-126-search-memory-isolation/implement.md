# Search Memory Isolation Implementation

1. Complete and validate #122 scoped memory-table foundation.
2. Inventory every MemoryRepository by-id mutation and every memory_tool/UI caller.
3. Introduce actor-scoped get/update/delete and default ASSISTANT create; reject missing/mismatched identity.
4. Add assistant-deletion cleanup for assistant-scoped memory-table data without touching GLOBAL/other assistants.
5. Verify prompt injection, tool list, page/drawer reads and enableMemory behavior.
6. Add A/B/GLOBAL, guessed-id, deletion and rapid-switch tests.
