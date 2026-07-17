# Template Deduplication and Management Research

## Existing behavior

- Template identity is UUID `id`; `name` has no unique index or DAO conflict query.
- Effective templates are GLOBAL + current ASSISTANT, returned without name deduplication.
- UI creation only checks `name.trim().isNotEmpty()` and creates a new UUID every time.
- Different IDs with the same name may have different schemas and document families; they must not be auto-deleted or merged.
- Template deletion is ID-scoped and deletes every document whose `template_id` matches the selected ID.

## Contract selected for this task

- Normalize names with trim, Unicode NFC, collapsed whitespace, and `Locale.ROOT` lowercase.
- Private create/edit conflicts with GLOBAL + the same assistant's private templates.
- Global create/edit conflicts with every template, so it cannot introduce a duplicate into another assistant's effective set.
- Picker uses a deduplicated projection only; complete effective templates remain available to document projection and management.
- Legacy duplicate winner priority: has primary document, current ASSISTANT scope, GLOBAL scope, newer `updatedAt`, stable ID.
- Management lists every historical template ID and supports rename/description edit and ID-scoped deletion.
- Global deletion warns that every assistant and all documents using that template are affected.

## Existing APIs to reuse/extend

- `MemoryTableDAO.getTemplates()` supplies all templates for repository conflict checks.
- `MemoryTableRepository.upsertTemplate(template, actorAssistantId)` already preserves existing scope and authorization.
- `AssistantDetailVM.upsertMemoryTableTemplate` and `deleteMemoryTableTemplate` exist but need Result callbacks.
- `MemoryTableDAO.deleteEffectiveTemplateAndDocuments` already performs ID-scoped template/document deletion.

## Known boundary

- No DB unique index is added; historical duplicates and cross-process races remain possible.
- Snapshot orphan cleanup is outside this UI-focused task and remains an existing repository limitation.
