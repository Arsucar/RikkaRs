# PRD: Issue #302 — Tavern card bindings import with confirmation dialog

## Problem
SillyTavern character card import only extracts basic fields (name, first_mes, system_prompt, description, personality, scenario). World book (`data.character_book` / `data.extensions.world`) and presets embedded in the card are silently discarded.

## Requirements
1. Parse `data.character_book` and `data.extensions.world` / `data.extensions.character_book` from v2/v3 cards
2. Detect embedded bindings before persisting the assistant
3. Show confirmation dialog listing detected bindings (world book count, preset count)
4. On user confirm: import bindings as new Lorebook/Preset entries in Settings, associate IDs to the new Assistant
5. On user skip: import only the assistant body (current behavior)
6. No bindings → no dialog, direct import (current behavior)

## Scope
- **In scope**: world book (character_book) detection + import; confirmation dialog; association to assistant
- **Out of scope (MVP)**: preset detection from character cards (ST cards rarely embed presets; preset import already exists as standalone). Can add later if needed.
- **Out of scope**: `selective`/`selective_logic`/`vectorized` world info fields (flattened to keyword-OR)

## Acceptance Criteria (from issue)
- [ ] AC1: Import with world book → dialog shows "detected world book" with option to import or skip
- [ ] AC2: Selecting import → world book entries persist as Lorebook, associated to new assistant
- [ ] AC3: Selecting skip → only assistant body imported, no bindings written
- [ ] AC4: No bindings → no dialog, direct import (unchanged behavior)
- [ ] AC5: PNG and JSON paths behave identically
- [ ] AC6: Invalid binding data → toast noting skipped entries, no crash
- [ ] AC7: Unit tests for binding detection + parsing
