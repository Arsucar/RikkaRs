# Design

- Derive the list projection from `documents` and resolve the display name/schema
  through the effective-template lookup already exposed by the VM.
- Replace the direct `onAddTemplate` action with a dialog state that offers an
  effective-template choice and private/global template creation entry points.
- Add a VM operation that returns a persisted template/document identifier (or a
  typed failure) so navigation is emitted only after Room success.
- Keep deletion wired to the document repository method; template deletion is
  not part of the card action.
- Add pure/state tests for projection, success-before-navigation, failure hold,
  and document-only deletion.
