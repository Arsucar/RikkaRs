# Research: workspace shell policy /dev/ redirect false positive

- **Query**: Shell policy bug for PRD — matching logic, blocked patterns, tests, fix approach
- **Scope**: internal (`workspace` module + `.trellis/spec/workspace/shell-policy.md`)
- **Date**: 2026-07-01

## Findings

### Files Found

| File Path | Description |
|---|---|
| `workspace/src/main/java/me/rerere/workspace/WorkspaceShellPolicy.kt` | `evaluateShellCommand` and all heuristic rules |
| `workspace/src/test/java/me/rerere/workspace/WorkspaceShellPolicyTest.kt` | Unit tests (allowed/rejected command lists) |
| `.trellis/spec/workspace/shell-policy.md` | Documents bug cause and whitelist-regex fix |
| `.trellis/tasks/archive/2026-06/06-30-fix-workspace-shell-redirection/prd.md` | Prior PRD describing original bug (`Regex(">/dev/")`) |
| `.trellis/tasks/07-01-bug-shell-policy-redirect/prd.md` | Active task PRD (still TBD) |

### Bug: historical vs current tree

**Historical false positive (Issue #16 / archived PRD):**

```kotlin
Regex(""">/dev/""").containsMatchIn(lower)
```

Any command containing the substring `>/dev/` was rejected, including `2>/dev/null` (because `2>/dev/null` contains `>/dev/`).

**Current implementation (line 74):**

```kotlin
if (Regex("""[0-9]*>\s*/dev/(?!null|zero|urandom|random)""").containsMatchIn(lower)) {
    return ShellCommandVerdict.Rejected(NOT_ALLOWED)
}
```

- Matches optional fd prefix (`2>`, `1>`, `>`), optional whitespace, then `/dev/` **unless** path starts with whitelisted device names.
- Safe redirects to `null`, `zero`, `urandom`, `random` are **not** matched → **Allowed**.
- Redirects to e.g. `/dev/sda`, `/dev/mmcblk0` **are** matched → **Rejected**.

**Note for PRD:** If the active task assumes the bug is still unfixed, reconcile with current source: fix appears already landed; tests `allowsSafeDeviceRedirection` / `rejectsUnsafeDeviceRedirection` exist. PRD may target regression, release verification, or edge cases not in whitelist.

### `evaluateShellCommand` flow

1. Trim; empty → `Rejected("Command is required")`.
2. `lower = trimmed.lowercase()` (most checks use `lower` or `trimmed` as noted).
3. Sequential rule checks; first match wins with `Rejected(...)`; else `Allowed`.

### Full list of blocked patterns (heuristic rules)

| Rule | Pattern / condition | Rejection message |
|---|---|---|
| `rm -rf` root-class | `RM_RF_ROOT_CLASS` — `rm -rf` on `/`, `/*`, `/home`, `/etc`, `/data`, `/system`, `/sdcard`, `/storage`, `~`, `$HOME`, `*` | `NOT_ALLOWED` |
| Fork bomb heuristic | `FORK_BOMB_HEURISTIC` — `:(){` or `\|:.*&` style | `NOT_ALLOWED` |
| chmod world on `/` | `CHMOD_WORLD_ROOT` — `chmod -R 777 /` | `NOT_ALLOWED` |
| Power commands | `POWER_COMMAND` — `shutdown`, `reboot`, `halt`, `init 0/6` at start or after `;` `&&` `\|\|` | `NOT_ALLOWED` |
| Filesystem destroy | `lower.contains("mkfs.")` OR `dd if=` (case-insensitive) | `NOT_ALLOWED` |
| Unsafe `/dev/` redirect | `[0-9]*>\s*/dev/(?!null\|zero\|urandom\|random)` on `lower` | `NOT_ALLOWED` |
| Sensitive absolute paths | Substring in `lower`: `/data/data/`, `/data/user/`, `/sdcard/`, `/storage/`, `/system/` | `PATH_NOT_ALLOWED` |
| Traversal + sensitive | `../` in command AND (sensitive path in `lower` OR regex `\.\./.*(/data/\|/sdcard/\|/system/)`) | `TRAVERSAL_NOT_ALLOWED` |

Constants:

- `NOT_ALLOWED` = "This command is not allowed in the workspace shell"
- `PATH_NOT_ALLOWED` = "Access to that path is not allowed from the workspace shell"
- `TRAVERSAL_NOT_ALLOWED` = "Path traversal outside the workspace is not allowed"

**Safe device whitelist (redirect rule only):** `/dev/null`, `/dev/zero`, `/dev/urandom`, `/dev/random`.

**Not blocked by redirect regex alone:** `dd if=/dev/zero of=/dev/sda` — blocked by `dd if=` rule, not redirect rule.

### Test cases (`WorkspaceShellPolicyTest`)

| Test | Intent |
|---|---|
| `allowsCommonDevCommands` | `ls`, `cat`, `grep`, `pwd`, `cd ../build/` → Allowed |
| `rejectsSensitivePathsAndDestructiveCommands` | `/data/data/...`, `rm -rf /`, `mkfs.ext4`, `dd if=...`, traversal to data → Rejected |
| `rejectsExtendedDestructiveAndPowerCommands` | `rm -rf /*`, `/home`, `~`, `$HOME`, `chmod -R 777 /`, `reboot`, fork bomb → Rejected |
| `allowsExtendedDevCommandsWithoutFalsePositives` | `npm test`, `find`, `grep shutdown` (word in file), `cd && ls` → Allowed |
| `allowsSafeDeviceRedirection` | `2>/dev/null`, pipes with `2>/dev/null`, `>/dev/null`, `2> /dev/null`, `1>/dev/urandom`, `>/dev/null`, `2>/dev/urandom` → Allowed |
| `rejectsUnsafeDeviceRedirection` | `dd ... of=/dev/sda`, `cat /dev/sda > /dev/sda1`, `echo data > /dev/mmcblk0`, `dd ... > /dev/sda` → Rejected |

### Suggested fix approach (for PRD / if bug still reported)

1. **Replace substring** `>/dev/` with **negative lookahead whitelist** per `.trellis/spec/workspace/shell-policy.md`:
   - Pattern: `[0-9]*>\s*/dev/(?!null|zero|urandom|random)`
2. **Account for** fd prefix and whitespace (`2>`, `1>`, `> /dev/null`).
3. **Add tests** for safe vs unsafe redirection (already present in current test file).
4. **Document** heuristic-only boundary in policy spec (already documented).
5. **Optional hardening:** extend whitelist only with explicit safe devices; block `of=/dev/` via existing `dd if=` / separate `of=/dev/(?!...)` if gaps found.

### Related Specs

- `.trellis/spec/workspace/shell-policy.md` — whitelist regex convention, symptom/cause/fix

## Caveats / Not Found

- No separate `WorkspaceShellPolicy` under a `workspace/` directory name other than module `workspace/src/main/java/...`.
- `evaluateShellCommand` is invoked from `workspace/src/main/java/me/rerere/workspace/WorkspaceManager.kt` (not fully traced here).
- Active task has no `current` Trellis pointer; research written under `07-01-bug-shell-policy-redirect/research/`.

---

## Appendix: full `WorkspaceShellPolicy.kt`

```kotlin
package me.rerere.workspace

/**
 * Heuristic shell command policy for workspace tools.
 *
 * **Heuristic interception only — not a security boundary.** Real isolation depends on
 * [WorkspaceManager] working-directory limits and process permissions.
 */
sealed class ShellCommandVerdict {
    data object Allowed : ShellCommandVerdict()
    data class Rejected(val userMessage: String) : ShellCommandVerdict()
}

private const val NOT_ALLOWED = "This command is not allowed in the workspace shell"
private const val PATH_NOT_ALLOWED = "Access to that path is not allowed from the workspace shell"
private const val TRAVERSAL_NOT_ALLOWED = "Path traversal outside the workspace is not allowed"

/** Workspace cwd should not reference these absolute prefixes. */
private val SENSITIVE_ABSOLUTE_PATHS = listOf(
    "/data/data/",
    "/data/user/",
    "/sdcard/",
    "/storage/",
    "/system/",
)

private val RM_RF_ROOT_CLASS = Regex(
    """rm\s+-rf\s+(/\*|/\s*(${'$'}|;)|/(home|etc|data|system|sdcard|storage)(\s|${'$'}|;|/)|~|\${'$'}HOME|\*)""",
    RegexOption.IGNORE_CASE,
)

private val FORK_BOMB_HEURISTIC = Regex(
    """:\s*\(\s*\)\s*\{|:.*\|\s*:.*&""",
    RegexOption.IGNORE_CASE,
)

private val CHMOD_WORLD_ROOT = Regex(
    """chmod\s+-R\s+777\s+/($|\s|;|\*)""",
    RegexOption.IGNORE_CASE,
)

private val POWER_COMMAND = Regex(
    """^(shutdown|reboot|halt)\b|(?:;|&&|\|\|)\s*(shutdown|reboot|halt)\b|^(init\s+[06])\b|(?:;|&&|\|\|)\s*init\s+[06]\b""",
    RegexOption.IGNORE_CASE,
)

fun evaluateShellCommand(command: String): ShellCommandVerdict {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) {
        return ShellCommandVerdict.Rejected("Command is required")
    }
    val lower = trimmed.lowercase()

    if (RM_RF_ROOT_CLASS.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (FORK_BOMB_HEURISTIC.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (CHMOD_WORLD_ROOT.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (POWER_COMMAND.containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (lower.contains("mkfs.") || Regex("""dd\s+if=""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    if (Regex("""[0-9]*>\s*/dev/(?!null|zero|urandom|random)""").containsMatchIn(lower)) {
        return ShellCommandVerdict.Rejected(NOT_ALLOWED)
    }

    for (path in SENSITIVE_ABSOLUTE_PATHS) {
        if (lower.contains(path)) {
            return ShellCommandVerdict.Rejected(PATH_NOT_ALLOWED)
        }
    }

    if (trimmed.contains("../")) {
        for (path in SENSITIVE_ABSOLUTE_PATHS) {
            if (lower.contains(path)) {
                return ShellCommandVerdict.Rejected(TRAVERSAL_NOT_ALLOWED)
            }
        }
        if (Regex("""\.\./.*(/data/|/sdcard/|/system/)""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return ShellCommandVerdict.Rejected(TRAVERSAL_NOT_ALLOWED)
        }
    }

    return ShellCommandVerdict.Allowed
}
```

---

## Appendix: full `WorkspaceShellPolicyTest.kt`

```kotlin
package me.rerere.workspace

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceShellPolicyTest {
    @Test
    fun allowsCommonDevCommands() {
        val allowed = listOf(
            "ls -la",
            "cat README.md",
            "grep -r TODO .",
            "pwd",
            "cd ../build/",
        )
        for (cmd in allowed) {
            assertTrue("$cmd should be allowed", evaluateShellCommand(cmd) is ShellCommandVerdict.Allowed)
        }
    }

    @Test
    fun rejectsSensitivePathsAndDestructiveCommands() {
        val rejected = listOf(
            "cat /data/data/me.arsucar.rikka/shared_prefs/foo.xml",
            "rm -rf /",
            "mkfs.ext4 /dev/sda1",
            "dd if=/dev/zero of=/dev/sda",
            "cat ../../data/data/me.arsucar.rikka/foo.xml",
        )
        for (cmd in rejected) {
            assertTrue("$cmd should be rejected", evaluateShellCommand(cmd) is ShellCommandVerdict.Rejected)
        }
    }

    @Test
    fun rejectsExtendedDestructiveAndPowerCommands() {
        val rejected = listOf(
            "rm -rf /*",
            "rm -rf /home",
            "rm -rf ~",
            "rm -rf \$HOME",
            "chmod -R 777 /",
            "reboot",
            ":(){ :|:& };:",
        )
        for (cmd in rejected) {
            assertTrue("$cmd should be rejected", evaluateShellCommand(cmd) is ShellCommandVerdict.Rejected)
        }
    }

    @Test
    fun allowsExtendedDevCommandsWithoutFalsePositives() {
        val allowed = listOf(
            "npm test",
            "find . -name '*.kt'",
            "grep shutdown log.txt",
            "cd ../build/ && ls",
        )
        for (cmd in allowed) {
            assertTrue("$cmd should be allowed", evaluateShellCommand(cmd) is ShellCommandVerdict.Allowed)
        }
    }

    @Test
    fun allowsSafeDeviceRedirection() {
        val allowed = listOf(
            "echo test 2>/dev/null",
            "grep -r TODO . 2>/dev/null | head -5",
            ">/dev/null",
            "2> /dev/null",
            "1>/dev/urandom",
            "cat file.txt >/dev/null",
            "make 2>/dev/urandom",
        )
        for (cmd in allowed) {
            assertTrue("$cmd should be allowed", evaluateShellCommand(cmd) is ShellCommandVerdict.Allowed)
        }
    }

    @Test
    fun rejectsUnsafeDeviceRedirection() {
        val rejected = listOf(
            "dd if=/dev/zero of=/dev/sda",
            "cat /dev/sda > /dev/sda1",
            "echo data > /dev/mmcblk0",
            "dd if=input.img > /dev/sda",
        )
        for (cmd in rejected) {
            assertTrue("$cmd should be rejected", evaluateShellCommand(cmd) is ShellCommandVerdict.Rejected)
        }
    }
}
```