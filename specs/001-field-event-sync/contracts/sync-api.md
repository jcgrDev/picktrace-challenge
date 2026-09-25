# Contract: Sync HTTP API (as consumed by the client)

Source of truth is `CLAUDE.md` → "Backend contract" and the DTOs agreed in `interview-session.md`.
This file doesn't add endpoints or fields. It records how the client **uses** them. Behavior the
contract doesn't define is marked *client decision* and linked to an open question in
[plan.md](../plan.md#open-questions).

Base URL: `BuildConfig.SYNC_BASE_URL` (release), with `FakeSyncServer` installed in debug.
Serialization: kotlinx.serialization with `Json { ignoreUnknownKeys = true; explicitNulls = false }`.

## DTOs (`:core:network`)

```kotlin
@Serializable enum class OpType { CREATE, UPDATE, DELETE }
@Serializable enum class EntityType { FIELD_EVENT }

@Serializable data class PendingOpDto(
    val opId: String, val entityType: EntityType, val entityId: String, val opType: OpType,
    val schemaVersion: Int, val hlc: String,
    val baseVersion: Long? = null, val fields: JsonObject? = null,
)
@Serializable data class FieldEventFields(
    val workerId: String, val blockId: String, val quantity: Int, val timestamp: String,
)
@Serializable data class PushRequest(val ops: List<PendingOpDto>)          // 1..500
@Serializable data class RejectedOp(val opId: String, val reason: String, val serverVersion: Long? = null)
@Serializable data class PushResponse(val acked: List<String>, val rejected: List<RejectedOp>)

@Serializable data class ChangeDto(
    val entityType: EntityType, val entityId: String, val version: Long, val hlc: String,
    val deleted: Boolean = false, val fields: JsonObject? = null,
)
@Serializable data class PullResponse(val changes: List<ChangeDto>, val nextCursor: Long, val hasMore: Boolean)
@Serializable data class SnapshotResponse(val entities: List<ChangeDto>, val page: Int, val hasMore: Boolean, val cursor: Long)

interface SyncApi {
    @POST("sync/push") suspend fun push(@Body body: PushRequest): Response<PushResponse>
    @GET("sync/pull") suspend fun pull(@Query("cursor") cursor: Long, @Query("limit") limit: Int): Response<PullResponse>
    @GET("sync/snapshot") suspend fun snapshot(@Query("page") page: Int): Response<SnapshotResponse>
}
```

The one deviation from the setup reply is the return type: `Response<T>` instead of `T`. The
classifier needs the status code and the `Retry-After` header. The wire format doesn't change.
Pull and snapshot are declared and covered by DTO tests, but nothing calls them in this feature.

## `POST /sync/push`: example (v1 client)

```json
{ "ops": [ {
  "opId": "0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f60",
  "entityType": "FIELD_EVENT",
  "entityId": "0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f5f",
  "opType": "CREATE",
  "schemaVersion": 1,
  "hlc": "0000001757800000000-0000-3f9a1c0e7b2d4a61",
  "fields": { "workerId": "w_001", "blockId": "block_42", "quantity": 3, "timestamp": "2025-06-10T08:32:00Z" }
} ] }
```

```json
{ "acked": ["0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f60"], "rejected": [] }
```

The v1 client only ever sends `opType = CREATE`. It omits `baseVersion`, and `fields` holds all four
`FieldEventFields`. Ops go in `seq` order, with batch size `min(SyncConfig.batchSize, 500)` and a
default of 50.

## Client handling of push results

The input is one push call for one claimed batch. The output is exactly one row below. The local
writes are described in [data-model.md](../data-model.md#transactions).

| Result | Classified as | Per-op effect | Run | Worker returns |
|--------|---------------|---------------|-----|----------------|
| 2xx, body parses, `opId ∈ acked` | Acked | delete op, event SYNCED | continue | n/a |
| 2xx, body parses, `opId ∈ rejected` | Rejected | op FAILED (`REJECTED`, `reason`), event FAILED | continue | n/a |
| 2xx, body parses, `opId` in neither list | Transport (*client decision*, OQ2) | `attempts += 1`; → QUEUED, or FAILED (`EXHAUSTED`) at 5 | continue | n/a |
| 2xx, body doesn't parse | Transport | batch: as above | stop | `Result.retry()` |
| `IOException` (no route, timeout, reset) | Transport | batch: as above | stop | `Result.retry()` |
| 5xx | Transport | batch: as above | stop | `Result.retry()` |
| 429 | RateLimited | batch: as Transport | stop | `requestSync(delay = Retry-After ?: 30 s)`, then `Result.success()` |
| other 4xx | Refused (*client decision*, OQ3) | batch: op FAILED (`REFUSED`, `"HTTP <code>"`), event FAILED | stop | `Result.success()` |

The loop ends when a claim returns no rows, and then the run is `COMPLETED`. `acked` or `rejected`
entries with an unknown `opId` are ignored. A repeated `opId` that the server already accepted comes
back in `acked` (idempotency), so the event becomes SYNCED (FR-016).

`Retry-After` is parsed as delta-seconds, or as an HTTP-date relative to the device clock. A missing
or unparseable value falls back to 30 s.
