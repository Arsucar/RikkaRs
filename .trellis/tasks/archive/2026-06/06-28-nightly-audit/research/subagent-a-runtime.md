# Subagent subsystem audit (data + runtime + permissions)

- Date: 2026-06-28
- Range: upstream/master..HEAD (--no-merges), read-only

## Scope globs and commit counts

- `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/**` + `app/.../data/datastore/PreferencesStore.kt` => **129** commits
- Above + `ChatService.kt` + `GenerationHandler.kt` => **215** commits

No `AIProvider.kt`, no `SubagentRepository.kt`, no `PermissionGate` / `SubagentRuntime` class (runtime = `SubagentHost`).

---

## Findings (path:line + rating)

### P0

**A-01** `app/src/main/java/me/rerere/rikkahub/data/ai/subagent/SubagentHost.kt:405-406`, `app/.../service/ChatService.kt:1765-1774`  
`sandboxToolsForSubagent` sets every tool `needsApproval` to `NO_APPROVAL` (`SubagentHost.kt:35`), overriding `applySubagentWorkspaceApproval` (`SubagentPermissionBuilder.kt:76-92`). Subagent workspace write/shell and inherited MCP tools never hit `GenerationHandler.kt:214-218` Pending flow.  
Fix: scope sandbox to safe tools only, or drop approval builder for child path and document full auto + audit.

### P1

**A-02** `SubagentPermissionBuilder.kt:134-136` — `inheritTools=false` leaves only workspace tools (TODO).  
**A-03** `GenerationHandler.kt:257-265`, `ChatService.kt:1135-1195` — parallel `spawn_subagent` without conversation-level progress backpressure.

### P2

**A-04** `SubagentHost.kt:201-218` — throttle uses non-atomic `var` under concurrent `launch`.  
**A-05** `ChatService.kt:1682-1690` — `runBlocking` in workspace tool factory.  
**A-06** `PreferencesStore.kt:644-649`, `Assistant.kt:53` — migration merges into `disabledGlobalSubagents` but keeps `disabledBuiltinSubagents`.  
**A-07** `SubagentRuntimeTest.kt` — no `SubagentHost.spawn` / cancellation / sandbox tests.

### P3

**A-08** `PreferencesStore.kt:647` — unknown builtin disable names dropped silently.  
**A-09** `SubagentProfile.kt:118-125` — `childTranscript` removed (grep clean).  
**A-10** No `PermissionGate` / approval job join in app; naming drift vs design docs.

---

## Checklist

| Topic | Result |
|---|---|
| BUILTIN->GLOBAL idempotent | OK (`migrateSubagentBuiltinsIfNeeded`, tests in `SubagentModelTest.kt`) |
| disabledBuiltin -> disabledGlobal | Partial (merge OK, source field retained) |
| childTranscript dead code | None |
| CancellationException | OK (`SubagentHost.kt:145`, `GenerationHandler.kt:457`) |
| maxSteps / continuation | OK (`SubagentHost.kt:67-75,117-121,227`) |
| Path prefix gate | OK (`SubagentPermissionBuilder.kt:56-74`, tests) |
| Subagent tool approval | Broken by A-01 |

---

## Top 5 for tomorrow

1. P0: Resolve sandbox vs workspace approval (A-01).
2. P1: Progress backpressure for parallel spawns (A-03).
3. P1: `inheritTools=false` gap (A-02).
4. P2: Clear `disabledBuiltinSubagents` after migrate (A-06).
5. P2: Atomic progress throttle + SubagentHost tests (A-04, A-07).
