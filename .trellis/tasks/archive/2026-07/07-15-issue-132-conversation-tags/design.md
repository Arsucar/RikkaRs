# Technical Design

## Data Model

- Add `ConversationTagEntity` and `ConversationTagCrossRef` with explicit table names.
- Cross-ref primary key is `(conversationId, tagId)`; add reverse `(tagId, conversationId)` index and two cascading foreign keys.
- Keep tag relations outside persisted `Conversation` updates. Use a display model/projection for tags.
- Upgrade Room 37→38, register migration and schema, and validate foreign keys after restore/reopen.

## Repository Contract

- Introduce a tag repository with centralized normalization constants, palette validation, and stable domain errors.
- DAO transactions implement create/rename/recolor/delete/merge/add/remove and recheck limits inside the transaction.
- `addTag` and `removeTag` are idempotent; unknown conversation/tag IDs map to stable errors.
- Fork uses one database transaction for new conversation, nodes, and `INSERT ... SELECT` tag relations.

## Query and UI Data Flow

- Build a composable `ConversationFilter` and raw Room paging query using `EXISTS` for selected tag IDs.
- Empty tag selection adds no predicate; tag IDs are OR; other scopes are AND.
- Observe tag/cross-ref data through one Room Flow and map it by conversation ID, avoiding per-row DAO calls.
- Persist selected tag IDs in recoverable drawer state and remove IDs absent from the current vocabulary.

## UI

- Add a settings tag-management route/page.
- Add drawer filter entry/count/clear, conversation tag chips with `+N`, and a long-press management sheet.
- Keep pin/loading controls in a fixed trailing area and let the title/chip group consume remaining width.

## Compatibility and Risk

- Generate normalized names in Kotlin; do not depend on SQLite ASCII-only `LOWER`.
- Count Unicode code points, not UTF-16 length.
- Restored databases must reopen before integrity checking; existing backup transport remains unchanged.
- Dynamic paging parameters must rebuild the Pager after filter changes.
