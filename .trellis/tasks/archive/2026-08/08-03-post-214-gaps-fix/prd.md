# PRD: Post-214 gaps fix + verified issue comments

## Goal

Close remaining gaps found in the post-#214 audit: fix open bugs/features, harden #217 MVU for ST XML, then post per-issue verification comments with checklists marked after evidence.

## In scope

1. **#222** — Add `@Serializable` to `Screen.SettingClash` so background/rotation no longer crashes.
2. **#217 / #216** — Support SillyTavern MVU body with `<Analysis>…</Analysis><JSONPatch>…</JSONPatch>` (and keep existing raw JSON / wrapper object paths). Unit tests for XML shell.
3. **#224** — Clash settings debug panel: in-memory ring buffer of 429 decision traces; instrument all skip/switch/replay paths; UI list + clear + copy (redacted).
4. **Comments** — After compile/tests/(install if device): for each of #214–#220, #222, #224 post Chinese + English comments with **checkbox lines checked against actual evidence** (not template boilerplate). Reopen nothing that is already correct; only document remaining known boundaries honestly.

## Out of scope

- Full ST global vars / worldbook variable triggers / tool channel (#217 optional follow-ups).
- #215 status/scope badges, enable Toast, UITests, README.
- #218 store-level concurrency harness, R4 ChatPage collect split, ModeInjection partial write.
- #219 AC5 “Don’t keep activities” survival A/B study.
- Formal release / version bump.
- Re-implementing already-correct core of #214/#218/#219/#220/#215.

## Acceptance criteria

- [ ] #222: `SettingClash` is `@Serializable`; process death/rotation path no longer throws missing serializer (compile + code review; device if available).
- [ ] #217: XML MVU shell parses and strips; JSON array and `{"JSONPatch":[…]}` still work; malformed keeps original; unit tests green.
- [ ] #224: AC1–AC7 code paths record traces; panel empty/list/clear/copy; max 20; no secrets in copy text; unit tests for tracer + interceptor skip reasons.
- [ ] `compileDebugKotlin` + focused unit tests pass with `--no-daemon`.
- [ ] Device: `installDebug` if adb device present; else assemble + report APK path honestly.
- [ ] GitHub: CN + EN comments on each relevant issue with checked items matching real verification.

## Non-goals

Do not invent green checks for tests/install not run.