# Skill access scope and symlink handling

## Goal

Resolve skill file access inconsistencies and define the product boundary between global skills and assistant-private skills.

## Issues

- #34: `workspace_read_file` cannot read `/skills/<skill>/SKILL.md`.
- #37: duplicate detailed report for the same `/skills` workspace read failure.
- #38: `use_skill` with `path` cannot read skill-local symlinks whose target resolves outside the skill directory.
- #36: support global skills and assistant-specific skills without cross-assistant leakage.

## Requirements

- Make `/skills` guidance and tool behavior consistent:
  - Either route known bind-mount paths to their host-side sources, or make prompts/tool errors direct models to `use_skill`.
  - Prefer a real implementation fix for `/skills` reads if it can be done without broad mount abstraction churn.
- Keep `use_skill` path traversal protections for `../` and absolute paths.
- Support symlinked files inside skill directories only under a clear allowlist policy.
- Define assistant-private skill storage and runtime visibility before changing mount behavior for private skills.
- Prevent unauthorized assistants from using `use_skill`, slash skill UI, or workspace file access to read private skill contents.

## Acceptance Criteria

- [ ] `use_skill(name)` still reads enabled skill `SKILL.md`.
- [ ] `use_skill(name, path)` can read valid skill subfiles and approved symlink targets.
- [ ] Traversal paths remain rejected with clear errors.
- [ ] `/skills/<skill>/SKILL.md` behavior no longer contradicts workspace prompt text.
- [ ] Global and assistant-private skill visibility rules are documented in design before implementation.
- [ ] Tests cover normal subfile reads, traversal rejection, symlink handling, and unauthorized private-skill access.

## Out of Scope

- Full marketplace or WebDAV redesign unless required to preserve private skill semantics.
- Changes to unrelated workspace paths beyond known bind mounts needed for skill access.
