# Memory Table Safety Foundation Design

## Scope

This task resolves #81, #83, #85, and #87. It covers default memory-table
wording/schema, schema and payload validation, and readable tool errors.

## Boundaries

- Repository layer owns persistence safety. Any template/document write through
  `MemoryTableRepository` must validate JSON before calling DAO upsert.
- Tool layer owns model-facing ergonomics. Tool failures should return a concise
  readable result instead of exposing raw exception/stack output.
- Editor UI may keep its existing local validation, but repository validation is
  the final gate for both UI and tools.

## Contracts

- `schemaJson` must be a JSON object with a non-empty `tables` array. Each table
  needs a non-blank `name`; each table needs a non-empty `columns` array; each
  column needs a non-blank `name`.
- `payloadJson` must be a JSON object. Empty payloads normalize to `{}`.
- Invalid writes throw an `IllegalArgumentException` before DAO persistence, so
  previous valid rows remain untouched.
- Tool execution catches expected argument/write failures and returns a JSON
  result with `success=false` and `error`, allowing the assistant to recover.

## Compatibility

- Existing valid documents keep their revision behavior.
- Existing blank schemas/payloads still normalize to defaults.
- The default schema changes from key/value-only facts to a multi-column
  user-facing table example while retaining a `key` column so existing keyed
  merge behavior remains compatible.
