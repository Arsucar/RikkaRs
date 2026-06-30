# Workspace Shell Policy Spec

> Code-spec for heuristic shell command validation in the workspace module.

---

## Convention: Whitelist Regex Over Blacklist Substring

**What**: When a policy rule needs to block dangerous paths/commands but allow safe exceptions, use a negative lookahead regex (whitelist) rather than a simple substring match (blacklist).

**Why**: Substring matching (`contains(">/dev/")`) produces false positives on legitimate patterns (`2>/dev/null`). Negative lookahead (`(?!safe1|safe2)`) explicitly enumerates permitted exceptions while blocking everything else.

**Example**:
```kotlin
// Wrong — blocks ANY redirect to /dev/*
Regex(""">/dev/""").containsMatchIn(lower)

// Correct — allows safe devices, blocks everything else
Regex("""[0-9]*>\s*/dev/(?!null|zero|urandom|random)""").containsMatchIn(lower)
```

**Safe devices whitelist**: `/dev/null`, `/dev/zero`, `/dev/urandom`, `/dev/random`.

---

## Common Mistake: Over-Broad Regex in Security-Policy Code

**Symptom**: Legitimate commands like `2>/dev/null` or `grep ... 2>/dev/null | head` are rejected by the shell policy.

**Cause**: Using substring matching or overly broad regex patterns that match both safe and unsafe paths.

**Fix**: Replace substring match with regex using negative lookahead for safe exceptions.

**Prevention**: When writing any new heuristic check, enumerate the allowed cases explicitly. If you can't enumerate all safe cases, narrow the match to the most specific dangerous pattern possible.

---

## Gotcha: fd Prefix in Redirections

> **Warning**: Shell redirections may include an optional file descriptor prefix (`2>`, `1>`, `>`) and optional whitespace before the path.

When matching redirections, the regex must account for:
- `>` (stdout)
- `1>` (explicit stdout)
- `2>` (stderr)
- `> /path` (space before path)

Pattern: `[0-9]*>\s*/path/`
