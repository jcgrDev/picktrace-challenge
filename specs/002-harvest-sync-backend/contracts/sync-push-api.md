# Contract: `POST /v1/sync/push`

Authoritative wire contract for the POC. It is the server side of
`specs/001-field-event-sync/contracts/sync-api.md`; any divergence is a bug on one side.

## Request

| Item | Value |
|------|-------|
| Method, path | `POST /v1/sync/push` (client base URL ends in `/v1`, client path is `/sync/push`) |
| Headers | `Content-Type: application/json` (required), `X-Device-Id: <1..128 chars>` (required) |
| Body | `{ "ops": [ <op>, ... ] }`, 1 to 500 elements, body at most 2 MB |

Op envelope (identical to the client's `PendingOpDto`):

| Field | Type | Required | POC rule |
|-------|------|----------|----------|
| `opId` | string, UUID | yes | Unparseable → whole request malformed (cannot be reported per op). |
| `entityType` | string | yes | Must be `FIELD_EVENT`, else `UNSUPPORTED_OP_KIND`. |
| `entityId` | string, UUID | yes | Record id. |
| `opType` | string | yes | Must be `CREATE`, else `UNSUPPORTED_OP_KIND`. |
| `schemaVersion` | integer | yes | Stored. |
| `hlc` | string | yes | Stored. |
| `baseVersion` | integer or null | no | Ignored. |
| `fields` | object | yes for CREATE | `workerId` string, `blockId` string, `quantity` integer, `timestamp` ISO-8601 instant. Unknown keys inside `fields` are ignored. |

Unknown top-level keys in the request and in each envelope are ignored (forward compatibility).

## Response `200 OK`

```
{
  "acked":    [ "<opId>", ... ],
  "rejected": [ { "opId": "<opId>", "reason": "<code>", "detail": "<text>" }, ... ]
}
```

- Each distinct request `opId` appears exactly once across both lists, ordered by first
  occurrence in the request.
- `detail` is optional, human-readable, at most 200 characters, never contains another device's
  data.
- `serverVersion` is reserved (client DTO has it as optional); the POC never sends it.

## Per-op reason codes

| Code | Condition | Stored? |
|------|-----------|---------|
| `INVALID_RECORD` | Validation failure; `detail` names the field and rule. | No |
| `OP_CONTENT_MISMATCH` | Known opId with a different fingerprint, or the opId occurs in this request with two different fingerprints. | Nothing new |
| `ID_CONFLICT` | Record id exists with a different fingerprint under another opId, or two ops in this request target the same id with different fingerprints (first wins). | Nothing new |
| `UNSUPPORTED_OP_KIND` | `opType` not `CREATE` or `entityType` not `FIELD_EVENT`. | No |

## Validation rules (produce `INVALID_RECORD`)

| Field | Rule | `detail` text |
|-------|------|---------------|
| `entityId` | Well-formed UUID | `entityId must be a UUID` |
| `fields` | Present for CREATE | `fields is required for CREATE` |
| `fields.workerId` | Non-empty, ≤ 128 chars, no trimming | `workerId is required` / `workerId exceeds 128 characters` |
| `fields.blockId` | Same | `blockId is required` / `blockId exceeds 128 characters` |
| `fields.quantity` | Integer (JSON number without fraction), > 0, ≤ `maxQuantity` | `quantity must be a whole number greater than 0` / `quantity exceeds maximum <n>` |
| `fields.timestamp` | Parses as an ISO-8601 instant; ≤ server now + `futureTolerance` | `timestamp is not a valid instant` / `timestamp is in the future` |

## Whole-batch errors

Body: `{ "error": "<code>", "message": "<text>" }`. `Retry-After` is in seconds.

| HTTP | `error` | Condition | `Retry-After` |
|------|---------|-----------|---------------|
| 400 | `MALFORMED_REQUEST` | Not JSON, `ops` missing or not an array, or an element without a parseable `opId`. | no |
| 401 | `MISSING_DEVICE_ID` | Header absent or empty. | no |
| 413 | `BATCH_TOO_LARGE` | More than 500 ops or body over 2 MB. | no |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content type not JSON. | no |
| 429 | `BUSY` | Pool acquisition timed out. | yes, 5–30 random |
| 503 | `DATABASE_UNAVAILABLE` | Cannot connect, or deadlock retries exhausted. | yes, 5–30 random |
| 500 | `INTERNAL` | Anything else. | no |

Semantics: a 4xx means nothing in the batch was stored. A 5xx or 429 means nothing is known to be
stored; the client resends the same ops and idempotency resolves it.

## Health

`GET /health` → `200 {"status":"ok"}` when the pool can hand out a connection, else `503`.
Used by Docker Compose and the load test to wait for readiness.

## Examples

Fresh batch, one invalid op:

Request: two ops as in spec Section 4. Response:

```
{ "acked": ["01924b1e-3a2c-7d10-9f3e-0a1b2c3d4e5f"],
  "rejected": [ { "opId": "01924b1e-3a2c-7d10-9f3e-0a1b2c3d4e60",
                  "reason": "INVALID_RECORD",
                  "detail": "quantity must be a whole number greater than 0" } ] }
```

Same batch resent unchanged: identical response, nothing written.

Same first opId resent with `quantity` 13:

```
{ "acked": [],
  "rejected": [ { "opId": "01924b1e-3a2c-7d10-9f3e-0a1b2c3d4e5f",
                  "reason": "OP_CONTENT_MISMATCH",
                  "detail": "opId already stored with different content" } ] }
```

Pool exhausted:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 17

{ "error": "BUSY", "message": "server busy, retry after 17 seconds" }
```
