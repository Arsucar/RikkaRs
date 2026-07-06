# Design

## Affected Areas

- `app/src/main/java/me/rerere/rikkahub/data/files/SkillManager.kt`
- `app/src/main/java/me/rerere/rikkahub/data/files/SkillPaths.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/WorkspaceReminderTransformer.kt`
- `app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt`
- Assistant extension UI and subagent skill inheritance paths.

## Proposed Boundaries

- Global skills remain in the existing shared skill root.
- Assistant-private skills use `files/assistant_skills/<assistant-id>/<skill>/`, separate from the global `files/skills/<skill>/` root.
- `SkillManager.listSkills()` remains global-only. Runtime code uses `listSkillsForAssistant(assistantId)` when building `use_skill` and slash-skill visibility.
- `SkillMetadata.ownerAssistantId` marks private skills. A private skill is visible only when the current assistant id matches the owner namespace used to load metadata.
- Workspace `/skills` maps only to global skills. Private skills are intentionally not bind-resolved through workspace file tools; authorized access goes through `use_skill`.
- Symlink following should be opt-in and constrained to:
  - the resolved skill directory, or
  - the explicit shared skill-resource root `files/skill_shared/`.
- Workspace file tools should not assume proot bind mounts exist inside the rootfs mirror. Supported known mount targets use explicit host-side resolution.

## Implemented Shape

- `SkillPaths.resolveSkillFile()` rejects blank, absolute, NUL, and `..` paths before following filesystem symlinks.
- `use_skill(name, path)` resolves files from the `SkillMetadata` visible in the current tool list, so an enabled name alone is not enough to read another assistant's private skill.
- `workspace_read_file('/skills/...')` resolves against the host global skills root via a known-mount mapping and keeps traversal checks.
- Slash skill completion receives the current assistant id so UI visibility matches runtime `use_skill` visibility.

## Trade-offs

- Prompt-only fix for #34/#37 is low risk but leaves the file tool unable to inspect mounted skill files.
- Host-side bind-mount resolution is more useful but must be designed alongside #36 to avoid exposing private skills through `/skills`.
- Private skills are a larger feature; if needed, ship a narrow `/skills` read fix first only for global skills.
