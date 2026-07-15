# Workspace Image Tool Result Implementation

1. Trace workspace read result through tool serialization, UIMessage merge and every provider adapter.
2. Reuse existing user-image content type and conversion path; add only missing tool-result support.
3. Add MIME/size/error validation while preserving sandbox checks.
4. Test jpg/png, OCR-capable payload shape, invalid/oversize image, text regression and non-vision fallback.
5. Reference both issues in the fix; close #130 as duplicate after #129 is verified.
