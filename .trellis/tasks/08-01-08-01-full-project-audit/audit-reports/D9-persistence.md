# D9 — Data Persistence Audit (READ-ONLY)

**Scope:** Room schema / DAO / migrations (v1→v48) / repositories / DataStore Preferences / file & OCR cache / FTS  
**Package root:** `app/src/main/java/me/rerere/rikkahub/data`  
**DB version:** 48 (`AppDatabase.kt`)  
**Date:** 2026-08-01  
**Method:** static analysis only (no Gradle / no runtime)

---

## 1. 链路梳理

```
UI / ChatService / ViewModels
        │
        ▼
┌───────────────────┐     ┌──────────────────────────┐
│ SettingsStore     │     │ Repositories             │
│ (DataStore prefs) │     │ ConversationRepository   │
│ assistants/models │     │ MemoryTableRepository    │
│ providers/MCP…    │     │ Folder/Tag/Files/…       │
└─────────┬─────────┘     └────────────┬─────────────┘
          │                            │
          │                            ▼
          │              ┌─────────────────────────────┐
          │              │ Room AppDatabase (v48, WAL) │
          │              │ ConversationEntity          │
          │              │ message_node (FK CASCADE)   │
          │              │ message_stats / daily       │
          │              │ tags / hooks / memory_table │
          │              │ favorites / managed_files…  │
          │              └────────────┬────────────────┘
          │                           │
          │              ┌────────────▼────────────────┐
          │              │ message_fts (FTS5, onOpen)  │
          │              │ MessageFtsManager           │
          │              └─────────────────────────────┘
          │
          ▼
  filesDir/upload + images + managed_files
  cacheDir/ocr_cache.json + webview_content
```

**Write path (conversation):**  
`insert/updateConversation` → `database.withTransaction { conversationDAO + save/syncMessageNodes + stats }` → **then** `messageFtsManager.indexConversation` (outside txn).

**Read path:**  
List uses light projections / Paging; detail uses paged `message_node` load with `SQLiteBlobTooBigException` single-row fallback.

**Settings path:**  
`dataStore.data` → normalize/migrate → `toMutableStateFlow`; writes via `dataStore.edit` (atomic per call) or full `update(Settings)` that also mutates in-memory `settingsFlow.value` first.

**Migrations:**  
AutoMigration for many early hops + ~26 manual `Migration_*` registered in `DataSourceModule`. Exported schemas `app/schemas/.../1.json`–`48.json`. No `fallbackToDestructiveMigration`.

---

## 2. Findings (F9-n)

### F9-1 — Migration_11_12 skips oversized conversations (message body loss)

| Field | Value |
|-------|--------|
| **id** | F9-1 |
| **file:line** | `app/.../migrations/Migration_11_12.kt:86-90` |
| **severity** | **CRITICAL** |
| **description** | When extracting `nodes` JSON into `message_node`, `SQLiteBlobTooBigException` is caught per conversation: the row is **skipped** (no nodes written) but migration still commits. Those conversations keep empty/unmigrated content relative to the new schema path (nodes later forced to `[]` only on success path; on skip neither extraction nor clear runs). Users with huge threads can permanently lose access to message content after upgrade. |
| **evidence** | ```86:90:app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12.kt
                } catch (e: SQLiteBlobTooBigException) {
                    skippedCount++
                    Log.w(TAG, "migrate: skip conversation $conversationId due to large nodes blob", e)
                    continue
                }
``` |
| **suggested fix** | Stream/chunk-read blob; or export oversized rows to external files and insert empty placeholder with recovery flag; never silently skip without user-visible repair path. Add instrumentation for skipped IDs. |

---

### F9-2 — Migration_11_12 regenerates node IDs (breaks external refs)

| Field | Value |
|-------|--------|
| **id** | F9-2 |
| **file:line** | `Migration_11_12.kt:71-74` |
| **severity** | **HIGH** |
| **description** | Migration always uses `Uuid.random()` for node primary keys instead of preserving original node `id` from JSON. Favorites, hooks, logical turns, or any stored node id from pre-v12 data become dangling after upgrade. |
| **evidence** | ```71:74:app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12.kt
                            val nodeId = Uuid.random().toString()
                            db.execSQL(
                                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES (?, ?, ?, ?, ?)",
``` |
| **suggested fix** | Prefer `nodeObject["id"]` when present; only randomize if missing. Backfill note for already-migrated installs. |

---

### F9-3 — FTS reindex outside Room transaction (stale / lost search)

| Field | Value |
|-------|--------|
| **id** | F9-3 |
| **file:line** | `ConversationRepository.kt:297-309`, `344-356`, `358-365` |
| **severity** | **HIGH** |
| **description** | Conversation + nodes are written in `withTransaction`, then `messageFtsManager.indexConversation` / `deleteConversation` run **after**. Process kill, crash, or exception after commit leaves FTS out of sync. Delete removes FTS **before** DB delete—if txn fails, search entries vanish while conversation remains. `message_fts` is created in `onOpen` only (not Room entity)—not in exported schema; backup/restore integrity tooling must open via Requery+libsimple. |
| **evidence** | ```297:309:app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt
    suspend fun insertConversation(conversation: Conversation) {
        database.withTransaction {
            conversationDAO.insert(...)
            saveMessageNodes(...)
        }
        messageFtsManager.indexConversation(conversation)
    }
``` |
| **suggested fix** | Index FTS inside same transaction (raw SQL on same `SupportSQLiteDatabase`); or use outbox/version table + background reconcile; on delete, FTS delete last or compensate on failure. |

---

### F9-4 — Dual `compress_hidden_count` migrations (34→35 and 44→45)

| Field | Value |
|-------|--------|
| **id** | F9-4 |
| **file:line** | `Migration_34_35.kt:11`, `Migration_44_45.kt:8-16` |
| **severity** | **HIGH** |
| **description** | Column added in 34→35; 44→45 re-adds with PRAGMA guard (safe for that hop). Indicates schema/version fork or incomplete history: devices that somehow skip 34→35 or fork identity-hash paths rely on 44→45. More importantly, **34→35 has no IF NOT EXISTS guard**—re-running or dual paths can fail with “duplicate column”. Fresh install (v48) equals upgraded path only if every hop applies correctly; fork comment on 26→27 shows this class of risk is known. |
| **evidence** | ```11:11:app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_34_35.kt
            db.execSQL("ALTER TABLE message_node ADD COLUMN compress_hidden_count INTEGER")
``` |
| **suggested fix** | Single source of truth; make 34→35 idempotent like 44→45; document which fork required the second hop; add migration test 34→46. |

---

### F9-5 — Settings RMW races via `settingsFlow.value` + full rewrite

| Field | Value |
|-------|--------|
| **id** | F9-5 |
| **file:line** | `PreferencesStore.kt:586-690`, `701-737` |
| **severity** | **HIGH** |
| **description** | `update(fn)` does `update(fn(settingsFlow.value))`—read from in-memory snapshot, then full preference rewrite. Concurrent partial updates (`updateAssistantConfig`, `updateCompressionPreferences`, archive, etc.) can lose fields if two coroutines interleave: A reads, B writes, A writes stale full blob. In-memory `settingsFlow.value = settings` is set **before** `dataStore.edit` completes—UI can show unpersisted state; if edit fails, memory/disk diverge. |
| **evidence** | ```586:592:app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt
    suspend fun update(settings: Settings) {
        if(settings.init) { ... return }
        settingsFlow.value = settings
        dataStore.edit { preferences ->
``` |
| **suggested fix** | Prefer single-key `edit` transforms reading **inside** the transform block from `preferences`; or Mutex around full Settings updates; only assign `settingsFlow` after successful edit (or from `data` flow). |

---

### F9-6 — Title search uses leading-wildcard LIKE (full scan)

| Field | Value |
|-------|--------|
| **id** | F9-6 |
| **file:line** | `ConversationDAO.kt:49-59`, `ConversationRepository.kt:989-991` |
| **severity** | **HIGH** (perf) |
| **description** | `title LIKE '%' || :searchText || '%'` cannot use B-tree indexes. `ConversationEntity` has **zero** indices on `assistant_id` / `update_at` / `is_pinned` (schema 48 confirms `indices count: 0`). List/sort/filter by assistant and pin order are full table scans as history grows. Message body search correctly uses FTS; title path does not. |
| **evidence** | ```49:50:app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt
    @Query("SELECT * FROM conversationentity WHERE title LIKE '%' || :searchText || '%' ORDER BY is_pinned DESC, update_at DESC")
    fun searchConversations(searchText: String): Flow<List<ConversationEntity>>
``` |
| **suggested fix** | Index `(assistant_id, is_pinned, update_at DESC)`; for title search use FTS secondary index or `title MATCH` / prefix search; avoid non-paging `Flow<List>` for global search on large DBs. |

---

### F9-7 — Upload / chat files lack global eviction (unbounded growth)

| Field | Value |
|-------|--------|
| **id** | F9-7 |
| **file:line** | `FilesManager.kt:105-171`, `204-220`, `222-230` |
| **severity** | **HIGH** |
| **description** | Chat attachments land under `filesDir/upload` and `managed_files` with no size/age cap. Deletion only when conversation deleted, attachment chip removed, or avatar replaced. Orphans from failed saves, abandoned drafts, or message edits that drop file URIs without `deleteChatFiles` accumulate. `countChatFiles` exists but no automatic cleanup. `images/` gen files similarly directory-listed without retention policy in FilesManager. |
| **evidence** | `createChatFilesByContents` always writes; only `deleteChatFiles` removes. No GC job found under `data/files`. |
| **suggested fix** | Periodic orphan scan: files not referenced by any `message_node` JSON / managed refs; quota + LRU; cleanup on app start / backup. |

---

### F9-8 — `syncMessageNodes` rewrites nodeIndex with wrong index for partial upsert list

| Field | Value |
|-------|--------|
| **id** | F9-8 |
| **file:line** | `ConversationRepository.kt:652-702`, `932-938` |
| **severity** | **MEDIUM** |
| **description** | `computeNodeSyncOps` returns **all** `newNodes` as upsert set (not only changed). `forEachIndexed` sets `nodeIndex = index` over that full list—correct **if** `newNodes` is complete ordered tree. If callers ever pass a subset, indices corrupt. Deletion of orphans then sequential insert is correct for full trees but O(n) per message save (no dirty-set). REPLACE insert can briefly leave gaps if crash mid-loop—mitigated by outer transaction. |
| **evidence** | ```932:938:app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt
internal fun computeNodeSyncOps(...): Pair<List<String>, List<MessageNode>> {
    val newIds = newNodes.map { it.id.toString() }.toSet()
    val deleteIds = existingIds.filter { it !in newIds }
    return deleteIds to newNodes
}
``` |
| **suggested fix** | Document invariant “always full ordered list”; optionally skip unchanged nodes by content hash while still fixing indices in one UPDATE pass. |

---

### F9-9 — Favorites have no FK to conversation/node (orphan favorites)

| Field | Value |
|-------|--------|
| **id** | F9-9 |
| **file:line** | `FavoriteEntity.kt:8-15`, `ConversationRepository.deleteConversation` |
| **severity** | **MEDIUM** |
| **description** | Favorites store `ref_key` / snapshot JSON without FK CASCADE to conversation or message_node. Deleting a conversation does not clear related favorites (only memory_table data + conversation row + FTS + files). Stale favorites and possible UI dead links. |
| **evidence** | `deleteConversation` cleans memory_table + conversationDAO + files; no `favoriteDAO` cleanup. |
| **suggested fix** | On delete, remove favorites by conversation/node ref; or FK/soft-ref GC. |

---

### F9-10 — Folder / memory_table / managed_files soft integrity (no FK)

| Field | Value |
|-------|--------|
| **id** | F9-10 |
| **file:line** | `FolderEntity.kt:11-13`, `ConversationEntity.folderId`, memory table entities |
| **severity** | **MEDIUM** |
| **description** | Documented choice: no FK from conversation→folder to avoid cascade delete. Repository must clear `folder_id` on folder delete—if any path skips it, dangling folder ids remain (queries still work via string match). Memory table documents reference templates/conversations by string scope without DB FK. Acceptable if all repo paths covered; risk on raw SQL / backup merge. |
| **evidence** | Folder entity comment explicitly avoids FK; `clearFolder` exists on ConversationDAO. |
| **suggested fix** | Audit all folder delete call sites; optional FK with `ON DELETE SET DEFAULT`. |

---

### F9-11 — Token stats RawQuery full-table JSON scan

| Field | Value |
|-------|--------|
| **id** | F9-11 |
| **file:line** | `MessageNodeDAO.kt:78-99` |
| **severity** | **MEDIUM** (perf) |
| **description** | `getTokenStats` / `getMessageCountPerDay` scan **all** `message_node` rows with `json_each`—no conversation filter on token stats. Scales poorly; partially mitigated by `message_stats` denormalized table (Migration_45_46) if UI prefers it. |
| **evidence** | TOKEN_STATS_SQL joins entire `message_node` without WHERE. |
| **suggested fix** | Prefer `message_stats` aggregates; deprecate global json_each path or scope by conversation. |

---

### F9-12 — `runBlocking` on DocumentsProvider binder threads

| Field | Value |
|-------|--------|
| **id** | F9-12 |
| **file:line** | `WorkspaceDocumentsProvider.kt:41` |
| **severity** | **MEDIUM** |
| **description** | `allWorkspaces() = runBlocking { dao().getAll() }` on SAF provider paths can block binder threads / ANR under load. Main-source `runBlocking` otherwise rare (ChatService comment notes removal of another use). DataStore itself avoids main-thread `runBlocking`. |
| **evidence** | ```41:41:app/src/main/java/me/rerere/rikkahub/data/provider/WorkspaceDocumentsProvider.kt
    private fun allWorkspaces(): List<WorkspaceEntity> = runBlocking { dao().getAll() }
``` |
| **suggested fix** | Cache workspace list with invalidation; or blockingQuery with timeout; avoid nested runBlocking. |

---

### F9-13 — Hook / logical-turn interrupt on every DB open

| Field | Value |
|-------|--------|
| **id** | F9-13 |
| **file:line** | `DataSourceModule.kt:142-197` |
| **severity** | **MEDIUM** |
| **description** | `onOpen` marks QUEUED/RUNNING hooks and ACTIVE/WAITING turns as INTERRUPTED. Correct for crash recovery, but any open (including tests, multi-process, backup validation) mutates production audit state. Multi-process or concurrent open edge cases could interrupt live work if shared DB. |
| **evidence** | UPDATE hook_executions / generation_logical_turns / hook_runs in onOpen callback. |
| **suggested fix** | Gate with process ownership / lease; only interrupt rows older than lease TTL. |

---

### F9-14 — Migration_6_7 hard-fails entire upgrade on one bad row

| Field | Value |
|-------|--------|
| **id** | F9-14 |
| **file:line** | `Migration_6_7.kt:73-76` |
| **severity** | **MEDIUM** |
| **description** | Parse failure calls `error(...)` inside transaction → whole 6→7 fails → app cannot open DB (no destructive fallback). One corrupt conversation blocks upgrade for all. |
| **evidence** | ```73:76:app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_6_7.kt
                } catch (e: Exception) {
                    error("Failed to migrate messages for conversation $id: ${e.message}")
                }
``` |
| **suggested fix** | Quarantine bad rows with empty nodes + log; continue migration; surface repair UI. |

---

### F9-15 — Migration_15_16 drops unparseable nodes

| Field | Value |
|-------|--------|
| **id** | F9-15 |
| **file:line** | `Migration_15_16.kt:43-48`, `66-74` |
| **severity** | **MEDIUM** |
| **description** | Nodes that fail `UIMessage` decode are omitted from re-insert set; if any node migrates successfully, conversation is DELETE+re-INSERT without failed nodes → silent message loss. |
| **evidence** | `onFailure { Log.w }` then only successful rows re-inserted after DELETE. |
| **suggested fix** | Keep original JSON for failed nodes; or abort conversation-level change if any parse fails. |

---

### F9-16 — FTS full reindex loads one conversation at a time but no incremental

| Field | Value |
|-------|--------|
| **id** | F9-16 |
| **file:line** | `ConversationRepository.kt:381-393`, `MessageFtsManager.kt:34-55` |
| **severity** | **MEDIUM** |
| **description** | `indexConversation` deletes all FTS rows for conversation then re-inserts every message text (capped 10k chars). Update path always full rebuild. `rebuildAllIndexes` is heavy for large libraries. No content-hash skip. Ranking uses FTS `rank` + `update_at`—good; jieba_dict init failure only logs (search may degrade). |
| **evidence** | DELETE then per-message INSERT; rebuild loops all IDs. |
| **suggested fix** | Incremental upsert by message_id; background rebuild with progress (already partially present). |

---

### F9-17 — OCR / WebView caches bounded; SingleFileCacheStore rewrite cost

| Field | Value |
|-------|--------|
| **id** | F9-17 |
| **file:line** | `OcrTransformer.kt:32-47`, `WebViewContentCache.kt:10-49`, `SingleFileCacheStore.kt:26-30` |
| **severity** | **LOW** |
| **description** | OCR: LruCache capacity 64, 3-day TTL, `deleteOnEvict=true`—good. WebView content: 7-day age eviction on store—good, but no max total size (many large HTML blobs until age). SingleFileCacheStore rewrites entire JSON map per put—OK at n=64. |
| **evidence** | LruCache capacity 64; WebView only time-based delete. |
| **suggested fix** | Cap WebView cache total bytes; optional. |

---

### F9-18 — ConversationEntity.nodes column retained as dead `[]`

| Field | Value |
|-------|--------|
| **id** | F9-18 |
| **file:line** | `ConversationEntity.kt:17-18`, `ConversationRepository.kt:407` |
| **severity** | **LOW** |
| **description** | Legacy `nodes` column always written as `"[]"` after split to `message_node`. Wastes schema clarity; risk if any code still reads `entity.nodes`. |
| **suggested fix** | Future migration drop column with AutoMigrationSpec `@DeleteColumn`. |

---

### F9-19 — No Room foreign_keys enable assertion in production open path

| Field | Value |
|-------|--------|
| **id** | F9-19 |
| **file:line** | `DataSourceModule.kt:81-213`, `DatabaseIntegrity.kt` |
| **severity** | **LOW** |
| **description** | Room normally enables FK; integrity helpers used for restore. No explicit production `PRAGMA foreign_keys` check on open. CASCADE assumed for message_node/stats/hooks. |
| **suggested fix** | Debug assert `PRAGMA foreign_keys` = 1 on open. |

---

### F9-20 — AutoMigration 23→24 vs manual Migration_23_24.kt

| Field | Value |
|-------|--------|
| **id** | F9-20 |
| **file:line** | `AppDatabase.kt:103`, `Migration_23_24.kt`, `DataSourceModule` (no 23→24 manual register) |
| **severity** | **LOW** |
| **description** | `is_archived` added via AutoMigration(23,24) in `@Database`; file `Migration_23_24.kt` exists but is **not** in `addMigrations`—dead/confusing duplicate. Later AutoMigration 39→40 deletes archive columns. Dead code risk for future maintainers. |
| **suggested fix** | Delete unused migration object or document AutoMigration ownership. |

---

## 3. Migration coverage matrix (1 → 48)

| From→To | Mechanism | Notes |
|---------|-----------|--------|
| 1→2 … 5→6 | AutoMigration | |
| 6→7 | Manual | messages→nodes rewrite; hard fail on parse |
| 7→8 … 10→11 | Auto | |
| 11→12 | Manual | split message_node; skip huge; new UUIDs |
| 12→13 | Auto | |
| 13→14 | Manual | SerialName remap |
| 14→15 | Manual | favorites |
| 15→16 | Manual | tool node merge; drop bad parses |
| 16→17 … 22→23 | Auto (+specs) | |
| 23→24 | Auto | is_archived (manual file unused) |
| 24→25 | Manual | archived_at |
| 25→26 | Manual | folder + folder_id |
| 26→27 | Manual | fork heal folders |
| 27→28 … 38→39 | Manual | chat_model, memory, subagent, tags, hooks |
| 39→40 | Auto + DeleteColumn | drop archive cols |
| 40→41, 41→42 | Auto | |
| 42→43 … 47→48 | Manual | hooks event_id, compress guard, stats, api_call, semantic memory |
| Destructive fallback | **None** | upgrade failure → crash / open failure |

**Fresh vs upgrade:** Exported schema 48 present; identity-hash fork documented at 26→27. Dual compress column migrations (34/44) show residual fork hygiene debt.

**Tests:** androidTest migrations for many hops (11, 23–25, 34–41, 44, 46…); not every hop has a dedicated test.

---

## 4. 亮点 / 可复用

1. **Conversation node split + paged load** with `SQLiteBlobTooBigException` single-row fallback and diagnostic logging thresholds.
2. **`withTransaction` around conversation + nodes + stats** for primary write consistency.
3. **LightConversationEntity + Paging** for list UI (avoids loading full message JSON).
4. **Memory table CAS / soft-delete / snapshots** with `@Transaction` helpers and strong instrumented tests.
5. **No destructive migration fallback**—data-preserving upgrade posture.
6. **Fork-aware Migration_26_27** (`hasTableColumn`) and Migration_44_45 idempotent ALTER.
7. **FTS5 + jieba simple tokenizer** with snippet ranking; rebuild API with progress.
8. **Backup integrity:** `PRAGMA integrity_check` + `foreign_key_check` via Requery+libsimple.
9. **DataStore versioned migrations** V1–V4; partial updates for assistant/preset avoid full Settings rewrite when used.
10. **OCR LruCache** with TTL + evict-to-store cleanup; WebView content age eviction.
11. **onOpen interrupt** of in-flight hooks/turns for crash recovery.
12. **Exported Room schemas 1–48** under `app/schemas`.

---

## 5. 遗漏与风险

| Gap | Risk |
|-----|------|
| `message_fts` not a Room entity | Schema export/backup tooling must special-case; easy to miss in migrations |
| Assistants/providers live in DataStore JSON blobs | Large write amplification; corruption of one key affects many features |
| No in-memory conversation cache layer with invalidation bus | Stale UI depends on Flow/Paging invalidation correctness |
| Concurrent conversation updates (two devices/tabs) | Last-writer-wins REPLACE on nodes; no revision field on message_node |
| Episodic embeddings stored as TEXT | Size growth; no purge policy audited here |
| Api call records unbounded | Similar growth risk |
| Subagent contexts have `expires_at` | Need confirm purge job runs (out of deep scope) |
| Windows/agent env: static only | Runtime migration of production DBs not executed in this audit |

---

## 6. Severity summary

| Severity | Count | IDs |
|----------|-------|-----|
| CRITICAL | 1 | F9-1 |
| HIGH | 6 | F9-2, F9-3, F9-4, F9-5, F9-6, F9-7 |
| MEDIUM | 8 | F9-8 … F9-16 |
| LOW | 4 | F9-17 … F9-20 |

**Priority fix order:** F9-1 → F9-3 → F9-2 → F9-5 → F9-7 → F9-6 → F9-4 → remaining MEDIUM.

---

## 7. File map (primary)

| Area | Paths |
|------|--------|
| DB | `data/db/AppDatabase.kt`, `DataSourceModule.kt` |
| Entities | `data/db/entity/*` |
| DAOs | `data/db/dao/*` |
| Migrations | `data/db/migrations/Migration_*.kt` (31 files + utils) |
| Repos | `data/repository/ConversationRepository.kt`, Memory*, Files*, … |
| Datastore | `data/datastore/PreferencesStore.kt`, `migration/PreferenceStoreV*.kt` |
| FTS | `data/db/fts/MessageFtsManager.kt`, `SimpleDictManager.kt` |
| Files | `data/files/FilesManager.kt` |
| Cache | `common/.../cache/*`, `OcrTransformer`, `WebViewContentCache` |
| Schemas | `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/{1..48}.json` |

---

*End of D9 persistence audit.*
