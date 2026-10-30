# Behavior and interface contracts

Existing gateway OpenAPI is the network source of truth; preserve endpoint names and success schemas.
Before changing backend error behavior update descriptions/responses in both service contracts.

- Save(imageUri, optional intent): explicit authorization only; returns Created(cardId), Duplicate(cardId)
  or a sanitized error. Empty intent remains absent. Only success dismisses the pending save.
- Retry(cardId): coalesces concurrent work, resets the failed stage; missing cards are a no-op.
- Delete(cardId)/clear: cancel owned work, delete rows and associated private files; never delete originals.
- Connection feed: each batch includes source card and candidate items with reason, benefit and group.
- Auth: POST /v1/auth/login-demo with user-entered credentials; store returned token only. HTTPS for
  external hosts; debug loopback aliases remain supported. No provider credentials in the client.
- AI errors expose stable HTTP/category only, never upstream body or exception text.
- Search: time filter then local keyword and semantic ranking; no match below 0.55 without lexical evidence.
- Every network stage has a finite deadline and honors cancellation; stored successful stages survive retries.
