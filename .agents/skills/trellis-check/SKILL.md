---
name: trellis-check
description: "Comprehensive quality verification: spec compliance, lint, type-check, tests, cross-layer data flow, code reuse, and consistency checks. Use when code is written and needs quality verification, before committing changes, or to catch context drift during long sessions."
---

# Code Quality Check

Comprehensive quality verification for recently written code. Combines spec compliance, cross-layer safety, and pre-commit checks.

---

## Step 1: Identify What Changed

```bash
git diff --name-only HEAD
git status
```

## Step 2: Read Task Artifacts and Applicable Specs

Read the current task artifacts in order:

- `prd.md`
- `design.md` if present
- `implement.md` if present

```bash
python ./.trellis/scripts/get_context.py --mode packages
```

For each changed package/layer, read the spec index and follow its **Quality Check** section:

```bash
cat .trellis/spec/<package>/<layer>/index.md
```

Read the specific guideline files referenced — the index is a pointer, not the goal.

## Step 3: Run Project Checks

Run the project's lint, type-check, and test commands. Fix any failures before proceeding.

For Gradle commands, always include `--no-daemon` to avoid blocked or lingering daemon processes, for example:

```bash
./gradlew --no-daemon lint
./gradlew --no-daemon test
./gradlew --no-daemon :app:compileDebugKotlin
```

When multiple sub-agents are running in parallel, only the final `trellis-check` sub-agent may run Gradle compile/test/lint commands. Other parallel implementation or review agents should limit themselves to code changes, code search, and static review to avoid exhausting host memory.

## Step 4: Review Against Checklist

### UI Changes

If any changed file affects a user-visible Compose screen, dialog, sheet, list, gesture,
state indicator, string, or accessibility behavior:

- [ ] Read `.trellis/spec/guides/ui-modification-thinking-guide.md`.
- [ ] Review its content/state/viewport/theme/accessibility/interaction matrix.
- [ ] If an active Trellis task exists, confirm its PRD/design records the applicable UI acceptance cases.
- [ ] Without a task, confirm the same matrix evidence is captured in the check or delivery report; missing PRD/design is not a failure.
- [ ] If the change exposed a new reusable rule, failure mode, or UI contract, confirm the guide was updated in the same change.
- [ ] Do not report device visual or gesture verification as passed when no device inspection occurred.

### Code Quality

- [ ] Linter passes?
- [ ] Type checker passes (if applicable)?
- [ ] Tests pass?
- [ ] No debug logging left in?
- [ ] No suppressed warnings or type-safety bypasses?

### Test Coverage

- [ ] New function → unit test added?
- [ ] Bug fix → regression test added?
- [ ] Changed behavior → existing tests updated?

### Spec Sync

- [ ] Does `.trellis/spec/` need updates? (new patterns, conventions, lessons learned)

> "If I fixed a bug or discovered something non-obvious, should I document it so future me won't hit the same issue?" → If YES, update the relevant spec doc.

## Step 5: Cross-Layer Dimensions (if applicable)

Skip this step if your change is confined to a single layer.

### A. Data Flow (changes touch 3+ layers)

- [ ] Read flow traces correctly: Storage → Service → API → UI
- [ ] Write flow traces correctly: UI → API → Service → Storage
- [ ] Types/schemas correctly passed between layers?
- [ ] Errors properly propagated to caller?

### B. Code Reuse (modifying constants, creating utilities)

- [ ] Searched for existing similar code before creating new?
  ```bash
  grep -r "pattern" src/
  ```
- [ ] If 2+ places define same value → extracted to shared constant?
- [ ] After batch modification, all occurrences updated?

### C. Import/Dependency (creating new files)

- [ ] Correct import paths (relative vs absolute)?
- [ ] No circular dependencies?

### D. Same-Layer Consistency

- [ ] Other places using the same concept are consistent?

---

## Step 6: Report and Fix

Report violations found and fix them directly. Re-run project checks after fixes.
