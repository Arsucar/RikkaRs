# Skill Runtime

## Storage

- Global skills live under `files/skills/<skill>/` and may be inspected through the workspace `/skills` mount.
- Assistant-private skills live under `files/assistant_skills/<assistant-id>/<skill>/`.
- Shared symlink targets for skill support files live under `files/skill_shared/`.

## Contracts

- `SkillManager.listSkills()` returns global skills only.
- Runtime tool creation must use `SkillManager.listSkillsForAssistant(assistant.id)` when building `use_skill` visibility.
- `use_skill` must read from the `SkillMetadata` visible in the current tool list. Do not re-resolve by global name after the tool call starts.
- Slash skill completion must use the current assistant id so UI visibility matches `use_skill`.
- Workspace `/skills` resolution is global-only. Private skills must not be exposed through workspace file tools or rootfs mount resolution.
- `SkillPaths.resolveSkillFile()` rejects blank, absolute, NUL, and `..` paths before following symlinks.
- Symlink targets outside the skill directory require an explicit allowed root, currently `files/skill_shared/`.

## Validation

- Normal `use_skill(name)` and `use_skill(name, path)` reads.
- Traversal rejection for `../` paths.
- Symlink allowlist acceptance and unapproved symlink rejection.
- Private-skill metadata is unavailable to non-owner assistants.
- `/skills/<skill>/SKILL.md` known-mount mapping resolves to the host global skill root.
