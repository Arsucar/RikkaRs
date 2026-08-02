# D5 Workspace Module — Security + Correctness Audit

**Scope:** `workspace/` (sandbox FS + PRoot shell) + app glue (`WorkspaceRepository`, `WorkspaceTools`, UI/VM, SAF provider, terminal).  
**Mode:** READ-ONLY static analysis. No build/run.  
**Date:** 2026-08-01  
**Auditor:** D5 workspace audit agent

---

## 1. 链路梳理 (Call Chain)

### 1.1 Sandbox filesystem (host-side, non-shell)

```
UI (WorkspaceDetailPage / FileEditor)
  → WorkspaceDetailVM / WorkspaceRepository
    → WorkspaceManager.{list,read,write,import,export,delete,move,glob,grep}
      → WorkspaceFileSystem.resolvePath (canonical containment under area root)
        → FILES: filesBaseDir/{root}/files
        → LINUX: baseDir/{root}/linux  (UI writes blocked by requireWritableArea)
```

Path resolution: `WorkspaceFileSystem.resolvePath` uses `canonicalFile` + prefix check against root. Null bytes rejected. Leading `/` stripped (relative to area root).

Rootfs absolute path map (AI tools / host read of guest paths):

```
WorkspaceManager.resolveRootfsPath(root, absolutePath)
  1. sortedBindMounts (longest target first) → mount.source + relative
  2. /workspace → filesDir(root)
  3. KERNEL_FS_MOUNTS (/dev,/proc,/sys) → hard error
  4. else → linuxDir(root) + relative
  → WorkspaceFileSystem.resolve(rootDir, relativePath)
```

### 1.2 Shell execution (AI tool + simple terminal tab)

```
workspace_shell / WorkspaceDetailVM.executeTerminalCommand
  → WorkspaceRepository.executeCommand (runInterruptible)
    → WorkspaceManager.executeCommand
      → evaluateShellCommand (heuristic policy, NOT security boundary)
      → WorkspaceShellContext (cwd under filesDir via resolvePath)
      → ProotShellRunner.execute
        → WorkspaceProotCommandBuilder (-r rootfs, -w /workspace[/cwd], -b files:/workspace, bind mounts, /dev,/proc,/sys)
        → env -i + bash -l -c 'cd -- "$1" && eval "$2"'  (command as argv, single eval)
        → Process.readResult (timeout, destroyForcibly, StreamCollector max 128KiB/stream)
      → on success: WorkspaceChangedFileScanner.scan(filesDir, scanStart)
```

Program path (git etc.): `executeProgram` / `executeProgramWithValidatedPath` → `programArguments` bypass shell eval; path validated via `resolveRepositoryPath` (no intermediate symlink dirs).

### 1.3 Interactive terminal (Termux)

```
WorkspaceTerminalPage
  → prepareWorkspaceTerminalSession + createWorkspaceTerminalSession
  → TerminalSession(proot, filesDir, args, env)
  → bash interactive (no command policy, no timeout)
  → DisposableEffect → finishIfRunning
```

Bind mounts: `/workspace` + `/skills` only (not full ChatService mount set: missing `/upload`, `/tool_outputs`, `/skills_private`).

### 1.4 AI tool surface

| Tool | Default approval | Write path | Isolation notes |
|------|------------------|------------|-----------------|
| `workspace_read_file` | false | host FS via resolveRootfsPath or knownMounts | 8MB cap; images → FilesManager upload |
| `workspace_write_file` | false | **shell** `cat > path` via proot | approval forced if path outside `/workspace` or `/tmp` |
| `workspace_edit_file` | false | read host + write shell | same |
| `workspace_shell` | **true** | full proot bash | policy heuristic only |

Wiring: `ChatService.createWorkspaceToolsIfReady` (READY only) + `SubagentPermissionBuilder` (access filter + optional path prefixes).

DI bind mounts (`RepositoryModule`): `/skills`, `/tool_outputs`, `/upload` under app `filesDir`. Private skills as `extraBindMounts` at tool creation.

### 1.5 SAF

```
WorkspaceDocumentsProvider (android:authorities=${applicationId}.documents)
  documentId: root | ws/{root} | ws/{root}/{relPath}
  → resolveFile(root, relPath) canonical under filesDir(root)
  open/create/delete/rename/copy/move on FILES area only
```

### 1.6 Rootfs install

```
WorkspaceDetailVM.installRootfs(url)
  → WorkspaceRepository.installRootfs
    → RootfsInstaller.download (HttpURLConnection) + extractTar (safeResolve, .. reject)
    → RootfsPatcher (DNS, hosts, locale, groups, tmp)
```

---

## 2. Findings

### F5-1 — Absolute symlink in rootfs tar can point outside staging root

- **File:line:** `workspace/.../RootfsInstaller.kt:180-194`
- **Severity:** CRITICAL
- **Description:** `createSymlink` only containment-checks **relative** `linkName`. Absolute link targets (`File(linkName).isAbsolute`) are accepted and written with `Files.createSymbolicLink` without verifying the resolved target stays inside the staging/rootfs tree. A malicious/compromised rootfs URL can plant absolute symlinks to host paths (e.g. app private data). Later host IO that follows symlinks (`canonicalFile` in resolve) or guest shell can abuse these.
- **Evidence:**
```kotlin
val linkTarget = if (File(linkName).isAbsolute) {
    File(linkName)  // no containment check
} else {
    val resolved = File(target.parentFile ?: root, linkName).canonicalFile
    require(resolved.path == rootFile.path || resolved.path.startsWith(...))
    ...
}
Files.createSymbolicLink(target.toPath(), linkTarget.toPath())
```
- **Suggested fix:** Reject absolute symlink targets; or resolve against rootfs and require containment; prefer recording link text only without creating host-side absolute links; integrity-pin rootfs URLs (HTTPS + hash).

---

### F5-2 — `workspace_shell` / interactive terminal are not a true security sandbox against host data

- **File:line:** `WorkspaceShellPolicy.kt:3-8,47-95`; `WorkspaceProotCommandBuilder.kt:35-39`; `ProotShellRunner.kt:84-93`; `WorkspaceTerminalSession.kt:65-94`
- **Severity:** CRITICAL (threat model / isolation gap)
- **Description:** Policy is explicitly “heuristic interception only — not a security boundary.” PRoot runs as app UID with bind of host `/dev`,`/proc`,`/sys` and full network (DNS patched to public resolvers). Inside rootfs the process is root-id. Guest can: (a) read/write anything under mounted trees (`/workspace`, `/skills`, `/upload`, `/tool_outputs`, `/skills_private`); (b) often probe host via `/proc` and other binds; (c) bypass string policy with encoding, variables, `printf`, base64, split tokens (`/dat''a/data`), `rm -rf --no-preserve-root /`, etc. Interactive terminal applies **no** `evaluateShellCommand` at all.
- **Evidence:** Policy docstring; `/dev|/proc|/sys` always `-b`; terminal builds bare bash without policy; tests only cover naive string patterns.
- **Suggested fix:** Document threat model clearly for users (workspace shell = same app UID, not multi-tenant jail). Optionally: drop host `/proc` bind or use filtered mounts; seccomp/landlock if available; never claim “sandbox escape proof.” Keep approval default true for shell (already).

---

### F5-3 — Write/edit tools can mutate rootfs system paths with approval only (not hard deny)

- **File:line:** `WorkspaceTools.kt:504-517,142,187,407-434`
- **Severity:** HIGH
- **Description:** Paths outside `/workspace` and `/tmp` force `needsApproval`, but if user/subagent AUTO-approves, `writeTextInRootfs` runs shell `mkdir -p` + `cat > path` for **any** absolute rootfs path (e.g. `/etc/passwd`, `/skills/...`, `/bin/sh`). Subagent `WorkspaceApproval.AUTO` sets `needsApproval = { false }` entirely (`SubagentPermissionBuilder.kt:86`), so AUTO + FULL access can silently rewrite skills/rootfs.
- **Evidence:**
```kotlin
private val WRITABLE_ROOT_PREFIXES = listOf("/workspace", "/tmp")
// needsApproval = default || pathOutsideWritableRoots
// execute still calls writeTextInRootfs without re-checking allowlist
```
- **Suggested fix:** Hard-deny writes outside allowlist (or mount RO for skills/upload). Re-validate path on execute, not only in needsApproval. For AUTO subagents, still enforce hard path policy.

---

### F5-4 — `workspace_read_file` on images has no size limit (DoS / OOM)

- **File:line:** `WorkspaceTools.kt:384-405,313-336`
- **Severity:** HIGH
- **Description:** Text path uses `MAX_READ_FILE_BYTES` (8MB) via `readRootfsBuffer`. Image path uses `file.readBytes()` / full buffer **without** size check, then `createChatFilesByByteArrays` writes entire array to upload dir. HEIC/AVIF multi‑MB or multi‑GB files can OOM the process.
- **Evidence:**
```kotlin
val bytes = resolveKnownMountFile(...)?.let { file ->
    require(file.isFile) { ... }
    file.readBytes()  // no length check
} ?: readRootfsBuffer(...).toByteArray()  // buffer path has size check, knownMount bypass does not
```
Also: knownMount image path skips size; rootfs image path goes through `readRootfsBuffer` (OK). **knownMount branch is the hole.**
- **Suggested fix:** Apply same max bytes before `readBytes`; stream with cap; reject oversized images with shell/head hint.

---

### F5-5 — `importBytes` / SAF import have no size limit

- **File:line:** `WorkspaceFileSystem.kt:56-61`; `WorkspaceDetailVM.kt:120-140`; `WorkspaceDocumentsProvider.kt:109-117`
- **Severity:** HIGH
- **Description:** `importBytes` copies `InputStream` fully with no max. UI OpenDocument and SAF `openDocument`/`createDocument` can fill internal or external storage until disk full. No progress cancel on import stream beyond coroutine cancel on VM scope.
- **Evidence:**
```kotlin
inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
```
- **Suggested fix:** Cap copy (e.g. 100–500MB configurable); check free space; SAF open still OS-mediated but create/write should bound growth.

---

### F5-6 — Shell policy easily bypassed; sensitive-path check is substring-based

- **File:line:** `WorkspaceShellPolicy.kt:78-95`
- **Severity:** HIGH
- **Description:** Rejection of `/data/data/` etc. is `lower.contains(path)`. Bypasses: quote insertion, env vars (`$HOME` only blocked in specific rm patterns), hex, `$(...)`, base64 pipes, relative traversal that never contains the literal substring, writing to mounted app dirs that are **not** listed (`filesDir` workspaces path may not match `/data/data/` string if using external storage). `../` only extra-checked when combined with sensitive absolute substrings — pure `rm -rf ..` inside workspace is allowed (intended) but policy gives false sense of host protection.
- **Evidence:** Tests allow `cd ../build/`; reject only when sensitive literal present.
- **Suggested fix:** Treat policy as UX only (already stated); remove security claims; optionally refuse commands with unquoted host absolute paths after simple tokenization (still heuristic).

---

### F5-7 — `HostShellRunner` fallback runs host shell without PRoot

- **File:line:** `WorkspaceManager.kt:17`; `WorkspaceShellRunner.kt:26-36`
- **Severity:** HIGH (if ever selected in production)
- **Description:** Default `shellRunner = HostShellRunner()` runs `sh -c command` with `directory(workingDir)` on the **host** JVM. Production DI uses `ProotShellRunner`, but unit tests / miswiring / non-Android use of default would execute arbitrary commands on host with only heuristic policy and cwd under filesDir (still can `cd` out via shell builtins once process starts — cwd only sets initial directory).
- **Evidence:**
```kotlin
ProcessBuilder(defaultShell(), "-c", context.command).directory(context.workingDir)
```
- **Suggested fix:** Fail closed if rootfs missing instead of HostShellRunner in app module; mark HostShellRunner test-only.

---

### F5-8 — Concurrent file ops / TOCTOU on write and path validation

- **File:line:** `WorkspaceManager.kt:298-312,315-322`; `WorkspaceFileSystem.kt:37-53`; `WorkspaceTools.kt:196-209`
- **Severity:** MEDIUM
- **Description:** No per-file locking. `executeProgramWithValidatedPath` comments “minimize path-swap exposure” but validation and exec are still separate from any atomic open. `edit_file` reads then writes non-atomically — concurrent shell/edit can clobber. `writeText` check-exists then write races. Changed-file scanner uses mtime window and can miss or false-positive under concurrent writers.
- **Evidence:** Comment at 305; edit_file two-step; scanner `lastModifiedTime >= modifiedSinceMillis`.
- **Suggested fix:** Document; optional file locks for AI write tools; write via temp+rename for text tools.

---

### F5-9 — Process cleanup: timeout kills process group incompletely; collector join 1s

- **File:line:** `WorkspaceShellRunner.kt:45-72`; `ProotShellRunner.kt:20` (`--kill-on-exit`)
- **Severity:** MEDIUM
- **Description:** On timeout/interrupt, `destroyForcibly()` on proot parent; `--kill-on-exit` helps but grandchild reparenting can leave orphans (comment acknowledges daemon collector). `join(1000)` may abandon blocked reader threads (daemon mitigates JVM hang). Interactive terminal relies on `finishIfRunning` — good — but no global registry of running proot processes for app background kill.
- **Suggested fix:** Track PIDs; on app pause kill workspace jobs; consider `Process.destroyForcibly` + proot signal propagation tests on device.

---

### F5-10 — Media export to cache uses unsanitized display name; no size cap on prepareMediaFile

- **File:line:** `WorkspaceDetailVM.kt:175-201`
- **Severity:** MEDIUM
- **Description:** `File(dir, File(entry.name).name)` — name comes from listing under sandbox so path traversal via name is limited, but very long names or collision overwrite same cache file. Full file streamed with no max — multi-GB copy to `cacheDir` possible from LINUX or FILES.
- **Evidence:** `prepareFile` exports entire entry with no size check (unlike text preview `MAX_PREVIEW_BYTES` / `MAX_TEXT_FILE_VIEW_BYTES`).
- **Suggested fix:** Cap export size; unique temp names (UUID); delete on dispose.

---

### F5-11 — SAF `parseDocId` does not validate workspace root token format

- **File:line:** `WorkspaceDocumentsProvider.kt:290-299,276-287`
- **Severity:** MEDIUM
- **Description:** `root` segment is not checked against `ROOT_NAME_REGEX` (`[A-Za-z0-9._-]+`). Crafted documentIds like `ws/../other` — `filesDir` calls `requireValidRoot` which **would** throw on `..` in root. `ws/evil/../../` — relPath still canonical-checked. Main risk: confusing IDs / error paths; if `filesDir` validation ever weakened, escape risk. `isChildDocument` for parent root returns true for any child — broad.
- **Evidence:** `requireValidRoot` on `filesDir`; parseDocId has no regex.
- **Suggested fix:** Validate root with same `ROOT_NAME_REGEX` in parseDocId; return not-found on invalid.

---

### F5-12 — `resolveRootfsPath` does not normalize `..` before bind-mount matching (mitigated later)

- **File:line:** `WorkspaceManager.kt:135-160`; `WorkspaceFileSystem.kt:174-189`
- **Severity:** LOW (defense in depth)
- **Description:** Matching uses string prefix on raw path; ` /skills/../secret` fails later at `resolvePath` (tested). Paths like `/skills/foo/../../linux/...` similarly caught by canonical. Good tests exist. Residual: reliance on canonical following symlinks means **in-tree absolute/relative symlinks** can redirect reads to other locations still under the same rootDir (by design for git) — for bind mounts, symlink under skills with `allowedSymlinkRoots` can intentionally leave source root (`WorkspaceTools.resolveKnownMountFile`).
- **Suggested fix:** Lexically normalize `..` before mount match for clearer errors; keep symlink policy documented.

---

### F5-13 — Subagent path prefix validation skips shell when no path/cwd key

- **File:line:** `SubagentPermissionBuilder.kt:48-77`
- **Severity:** MEDIUM
- **Description:** `withPathValidation` only checks if input has `path`/`directory`/`cwd`. `workspace_shell` with only `command` (no cwd) skips prefix check; command can `cd /skills` or touch any mounted path. READ_ONLY still includes `workspace_shell`, so “read-only” subagents retain full shell write capability inside mounts.
- **Evidence:**
```kotlin
WorkspaceAccess.READ_ONLY to setOf("workspace_read_file", "workspace_shell")
// extractPathCandidateFromInput returns null if no path keys → validation skipped
```
- **Suggested fix:** READ_ONLY should exclude shell or force shell to readonly rootfs; always validate cwd default; consider command AST policy (hard).

---

### F5-14 — Rootfs download: cleartext HTTP allowed; no integrity hash

- **File:line:** `RootfsInstaller.kt:49-98`; UI install URL from user
- **Severity:** HIGH
- **Description:** Any URL string; follows redirects; no TLS pin, no checksum. Combined with F5-1 (absolute symlinks) and world-writable rootfs content, supply-chain risk is real if user pastes HTTP mirror or MITM on cleartext.
- **Suggested fix:** HTTPS-only; pin expected SHA-256; ship default official URL with hash in app.

---

### F5-15 — `grep` with `regex=true` can ReDoS

- **File:line:** `WorkspaceFileSystem.kt:121-167`
- **Severity:** MEDIUM
- **Description:** User/AI-controlled regex compiled and run over many files up to `maxReadBytes` each and `maxSearchResults`. Catastrophic backtracking can block IO dispatcher.
- **Suggested fix:** Timeout per file; use non-backtracking engine or length limit on pattern; cap walk time like changed-file scanner.

---

### F5-16 — `writeTextInRootfs` stdin size unbounded vs WorkspaceConfig.maxWriteBytes

- **File:line:** `WorkspaceTools.kt:407-433`; `Workspace.kt:39-44` (5MB host write)
- **Severity:** MEDIUM
- **Description:** Host `WorkspaceFileSystem.writeText` enforces `maxWriteBytes` (5MB). AI write path sends full `text.toByteArray()` as process stdin with only shell timeout — multi‑100MB strings can stress memory and proot.
- **Suggested fix:** Enforce same max before executeCommand; stream chunked write for large files intentionally.

---

### F5-17 — Image tool always names uploads `image.png` (wrong type / cache confusion)

- **File:line:** `WorkspaceTools.kt:394-396`; `FilesManager.kt:147-171`
- **Severity:** LOW
- **Description:** HEIC/AVIF/SVG read still saved as `image.png` mime `image/png`. Downstream encoders may mis-handle; not a sandbox escape.
- **Suggested fix:** Preserve extension/mime from path.

---

### F5-18 — Terminal interactive session missing several bind mounts vs AI tools

- **File:line:** `WorkspaceTerminalSession.kt:70-79` vs `RepositoryModule.kt:87-100`
- **Severity:** LOW (correctness / UX)
- **Description:** User terminal only mounts `/skills`, not `/upload`, `/tool_outputs`, `/skills_private`. Commands that work in AI shell may fail in UI terminal — inconsistency, not escape.
- **Suggested fix:** Share single mount table builder used by ProotShellRunner DI and terminal.

---

### F5-19 — `deleteWorkspace` race with running shell

- **File:line:** `WorkspaceRepository.kt:344-351`; `WorkspaceManager.kt:58-67`
- **Severity:** MEDIUM
- **Description:** Delete removes DB row then `deleteRecursively` workspace dirs while other sessions may still run proot against those paths — undefined FS errors, possible recreate empty dirs. GlobalLock only for storage migration, not delete.
- **Suggested fix:** Lock workspace id; kill processes; then delete.

---

### F5-20 — Policy false sense: `dd if=` blocked but `dd of=` / `curl|sh` allowed

- **File:line:** `WorkspaceShellPolicy.kt:70-76`
- **Severity:** LOW
- **Description:** Only `dd if=` and selected device redirects blocked. Network exfil and pipe-to-shell allowed (by design for dev workspace).
- **Suggested fix:** Keep as-is with clear UX copy that shell is powerful.

---

## 3. 亮点 / 可复用 (Strengths)

1. **Canonical path jail** in `WorkspaceFileSystem.resolvePath` + tests for `../` escape — solid host FS boundary for non-shell APIs.
2. **Unified bind-mount table** for PRoot `-b` and `resolveRootfsPath` (comment in DI) — avoids classic dual-path drift; longest-prefix sort for mounts.
3. **Command as argv + single `eval "$2"`** avoids classic double-quoting injection into proot argv construction; `programArguments` path for git avoids shell entirely.
4. **Output caps** (`MAX_OUTPUT_CHARS`), truncation drain to avoid pipe deadlock, interrupt → `destroyForcibly`, daemon collectors.
5. **KERNEL_FS hard reject** for host file API with shell hint; rootfs tar `..` rejection and relative symlink containment.
6. **Changed files** only in UI metadata, bounded, path-normalized — not leaked into model-visible tool JSON body.
7. **LINUX area read-only** in repository; tool approval defaults shell=true; subagent path prefix hook (when path present).
8. **Terminal lifecycle** dispose finishes session; produceState cancels unfinished create.
9. **Startup `cleanupAllTempDirs`** reduces proot tmp residue.

---

## 4. 遗漏与风险 (Gaps / Residual Risk)

| Area | Gap |
|------|-----|
| True isolation | PRoot ≠ VM; same UID as app; mounts leak skills/uploads; `/proc` host visibility |
| Shell policy | Heuristic only; interactive terminal unrestricted |
| Symlinks | Absolute tar symlinks (F5-1); final component symlinks allowed for git |
| Resource | No global disk quota per workspace; import/image unbounded paths |
| Concurrency | No file locks; delete vs shell |
| Network | Full guest network; DNS forced public |
| Integrity | Rootfs URL trust |
| Coverage | Few tests for write allowlist hard-deny, image size, SAF root validation, absolute symlink install |
| Zip | No dedicated zip API in workspace module; zip only as mime/open external |

**Threat model recommendation:** Treat workspace as **trusted local coding environment** under app sandbox, not untrusted multi-tenant jail. AI + user share risk of data loss inside workspace and skills mounts.

---

## 5. Severity summary

| ID | Severity | One-liner |
|----|----------|-----------|
| F5-1 | CRITICAL | Absolute symlink from rootfs tar can escape staging |
| F5-2 | CRITICAL | Shell/PRoot not a real host isolation boundary |
| F5-3 | HIGH | Approved writes can alter any rootfs/mount path |
| F5-4 | HIGH | Image read OOM (knownMount no size cap) |
| F5-5 | HIGH | Import/SAF unbounded copy |
| F5-6 | HIGH | Shell policy bypass trivial |
| F5-7 | HIGH | HostShellRunner default dangerous if miswired |
| F5-14 | HIGH | Rootfs HTTP + no hash |
| F5-8 | MEDIUM | TOCTOU / concurrent clobber |
| F5-9 | MEDIUM | Process orphan risk on timeout |
| F5-10 | MEDIUM | Media export no size cap |
| F5-11 | MEDIUM | SAF root id weak validation |
| F5-13 | MEDIUM | READ_ONLY + shell; path validation skip |
| F5-15 | MEDIUM | grep ReDoS |
| F5-16 | MEDIUM | AI write size unbounded vs host write cap |
| F5-19 | MEDIUM | Delete vs running proot |
| F5-12 | LOW | Pre-normalize `..` before mount match |
| F5-17 | LOW | Image always saved as png |
| F5-18 | LOW | Terminal mount set incomplete |
| F5-20 | LOW | Policy incomplete by design |

---

## 6. Priority fix order (suggested)

1. F5-1 absolute symlink + F5-14 rootfs integrity  
2. F5-4 image size + F5-5 import cap + F5-16 write cap  
3. F5-3 hard write allowlist (execute-time)  
4. F5-13 READ_ONLY without unrestricted shell  
5. Document F5-2 threat model; tighten mounts if feasible  

---

*End of D5 report.*
