# Repository Hygiene Guide

> Prevent temporary debugging artifacts and captured user data from entering Git history.

## Device UI Capture Artifacts

### Scope / Trigger

- Using UIAutomator dumps, screenshots, ADB capture commands, or other device UI inspection.
- Temporary capture files appear in the repository, especially root-level `_*.xml` and `_*.png` files.

### Contracts

- Treat UIAutomator XML dumps and device screenshots as sensitive. They may contain chat text, conversation titles,
  timestamps, model details, commands, and local workspace paths.
- Delete temporary captures after inspection; do not retain them as ordinary repository files.
- Use narrowly scoped ignore rules for known temporary naming patterns. Do not ignore all XML or PNG files because
  Android resources, documentation images, and test fixtures use those extensions.
- Before `git add .` or `git add -A`, inspect `git status --short` and exclude unexpected capture artifacts.
- Intentional screenshots or XML fixtures must use descriptive, non-temporary paths and be reviewed for sensitive data.

### Validation

- `git status --short` contains no unexpected root-level `_*.xml` or `_*.png` files.
- Ignore rules match the temporary naming convention without hiding normal project resources.
- The staged diff contains no captured user, device, or workspace data.

### Wrong vs Correct

Wrong:

```gitignore
*.xml
*.png
```

Correct for the repository-root temporary capture convention:

```gitignore
/_*.xml
/_*.png
```
