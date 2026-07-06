# Workspace git pack write reliability

## Goal

Make Git clone/fetch/pull reliable inside `/workspace`, or document and surface a clear limitation with a supported workaround.

## Issues

- #35: `git pull` under `/workspace` fails writing/renaming `.git/objects/pack/*.pack`, especially with EXTERNAL workspace files storage.
- #33: original combined report includes the same Git pack failure during writer-dna installation.

## Requirements

- Reproduce or reason from storage/proot code whether EXTERNAL storage plus `--link2symlink` causes Git pack rename/link failures.
- Compare PRIVATE and EXTERNAL workspace file storage behavior.
- Preserve existing workspace migration guarantees.
- If reliable Git support is not feasible on affected storage, expose a clear user-facing warning and supported fallback.

## Acceptance Criteria

- [ ] Root cause is documented in the task or implementation notes.
- [ ] `git clone` and `git pull` succeed in `/workspace` for the supported storage modes, or unsupported combinations are clearly blocked/warned.
- [ ] Existing workspace files migration behavior is not regressed.
- [ ] Validation includes at least compile plus device/rootfs manual checks when available.

## Out of Scope

- Subagent cancellation and delegation-tool behavior from #33.
- Skill installation workflow redesign unless needed for Git fallback messaging.
