# Data Model: Offline Field Event Capture & Sync

Phase 1 output for [plan.md](plan.md). Decisions behind these shapes are in [research.md](research.md).

## Domain (`:core:model`, pure Kotlin)

```kotlin
@JvmInline value class WorkerId(val value: String)
@JvmInline value class BlockId(val value: String)

enum class SyncStatus { PENDING, SYNCED, FAILED }
enum class FailureKind { REJECTED, EXHAUSTED, REFUSED }

data class FieldEvent(
    val id: UUID,              // UUIDv7, client-generated
    val workerId: WorkerId,
    val blockId: BlockId,
    val quantity: Int,         // >= 1
    val timestamp: Instant,    // UTC, millisecond precision
    val status: SyncStatus,
)

data class FieldEventDetail(
    val event: FieldEvent,
    val attempts: Int,              // 0 when synced (no op row)
    val failureKind: FailureKind?,  // non-null iff status == FAILED
    val failureReason: String?,     // non-null iff status == FAILED
    val inFlight: Boolean,          // true while inside an active batch
) {
    val canEdit: Boolean get() = event.status != SyncStatus.SYNCED && !inFlight
    val canRetry: Boolean get() = event.status == SyncStatus.FAILED
}

data class FieldEventDraft(
    val workerId: String, val blockId: String, val quantity: String,  // raw form input
    val timestamp: Instant,                                            // truncated to ms on record
)

interface IdGenerator { fun next(): UUID }   // Uuid7 is the production impl; tests inject fakes
```

`WorkerId` and `BlockId` are separate types, so passing one where the other is expected doesn't
compile (FR-003). Nothing converts one into the other.

### Validation (FR-006)

| Field | Rule | `ValidationError` |
|-------|------|-------------------|
| workerId | non-blank after `trim()`; stored trimmed | `WorkerIdMissing` |
| blockId | non-blank after `trim()`; stored trimmed | `BlockIdMissing` |
| quantity | parses as `Int` | `QuantityNotANumber` |
| quantity | `>= 1` | `QuantityNotPositive` |

`FieldEventValidator.validate(draft): ValidationResult` (sealed: `Valid(workerId, blockId, quantity)` or
`Invalid(errors: List<ValidationError>)`) returns every error at once. `FieldEventValidator.validateQuantity(raw): QuantityResult` (sealed: `Valid(quantity: Int)` or
`Invalid(error: ValidationError)`) is used by edits. Edits run the quantity rows only.

### Hlc

`Hlc(wallMs: Long, counter: Int, nodeId: String)`. `encode()` produces
`"%019d-%04d-%s"`, and `tick(last: Hlc, nowMs: Long): Hlc` is pure. See research R7.

## Room schema v1 (`:core:database`)

Database: `PicktraceDatabase`, file `picktrace.db`, `version = 1`, `exportSchema = true`.

### `field_event`

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | UUIDv7 string. Display order is `id DESC` (recording order) |
| `worker_id` | TEXT NOT NULL | |
| `block_id` | TEXT NOT NULL | |
| `quantity` | INTEGER NOT NULL | `>= 1` (enforced in the repository) |
| `timestamp_ms` | INTEGER NOT NULL | epoch ms, UTC |
| `status` | TEXT NOT NULL | `PENDING` / `SYNCED` / `FAILED` |

Index: `status`.

### `pending_op`

The FIFO outbox (requirement 1).

| Column | Type | Notes |
|--------|------|-------|
| `seq` | INTEGER PK AUTOINCREMENT | delivery order, never reused, kept across edit/retry |
| `op_id` | TEXT NOT NULL UNIQUE | UUIDv7. **Replaced** on manual retry |
| `entity_type` | TEXT NOT NULL | `FIELD_EVENT` |
| `entity_id` | TEXT NOT NULL | = `field_event.id` |
| `op_type` | TEXT NOT NULL | always `CREATE` in v1 |
| `schema_version` | INTEGER NOT NULL | `1` |
| `hlc` | TEXT NOT NULL | re-ticked on edit and retry |
| `base_version` | INTEGER NULL | always null in v1 (CREATE) |
| `fields_json` | TEXT NOT NULL | encoded `FieldEventFields`, exactly what is sent |
| `state` | TEXT NOT NULL | `QUEUED` / `IN_FLIGHT` / `FAILED` |
| `attempts` | INTEGER NOT NULL DEFAULT 0 | transport attempts for this `op_id` |
| `failure_kind` | TEXT NULL | `REJECTED` / `EXHAUSTED` / `REFUSED` |
| `last_error` | TEXT NULL | server reason or last transport error |

Indexes: unique `(entity_type, entity_id)`; `(state, seq)` for the claim query.
There's no foreign key. The outbox stays entity-agnostic for later entity types, and the pairing is
enforced by the transactions below.

### `sync_state`

A single row, `id = 0`.

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK | always 0 |
| `node_id` | TEXT NOT NULL | 16 hex chars, generated once in `onCreate` |
| `hlc_wall_ms` | INTEGER NOT NULL | last issued HLC |
| `hlc_counter` | INTEGER NOT NULL | |

The pull cursor column is added by a migration in the pull phase.

### `sync_run`

The FR-020 log. It keeps the latest 20 rows.

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK AUTOINCREMENT | |
| `started_at_ms` | INTEGER NOT NULL | |
| `finished_at_ms` | INTEGER NULL | |
| `outcome` | TEXT NOT NULL | `RUNNING` / `COMPLETED` / `TRANSPORT_ERROR` / `RATE_LIMITED` / `REFUSED` / `INTERRUPTED` |
| `batches` | INTEGER NOT NULL | |
| `acked` | INTEGER NOT NULL | |
| `rejected` | INTEGER NOT NULL | |
| `last_error` | TEXT NULL | |

## Invariants

These hold after every committed transaction, and DAO tests assert them.

1. `field_event.status = PENDING` ⇔ an op row exists with `state ∈ {QUEUED, IN_FLIGHT}`.
2. `field_event.status = FAILED` ⇔ an op row exists with `state = FAILED`, with `failure_kind` and
   `last_error` non-null.
3. `field_event.status = SYNCED` ⇔ no op row exists for it.
4. Every op row references an existing `field_event` row.
5. `IN_FLIGHT` rows exist only while a `sync_run` row is `RUNNING`.

## State machine (event status × op state)

```text
                 record()                          
   (none) ───────────────────────▶ PENDING/QUEUED ◀──────────────┐
                                    │    ▲  │                     │ transport failure,
                          claim     │    │  │ edit (qty, new hlc) │ attempts < 5
                          batch     ▼    │  └──────┐              │ (and run start reset)
                               PENDING/IN_FLIGHT ──┴──────────────┘
                                │        │        │
                  acked         │        │        │ transport failure, attempts == 5  → FAILED/FAILED (EXHAUSTED)
     (op deleted, same tx)      │        │ rejected                                   → FAILED/FAILED (REJECTED)
                                ▼        ▼ other 4xx on batch                         → FAILED/FAILED (REFUSED)
                          SYNCED/(no op)   FAILED/FAILED
                          read-only          │  retry(): new op_id, attempts=0, new hlc, keep seq
                                             └────────────────────────────▶ PENDING/QUEUED
```

## Transactions

Each of these is one `db.withTransaction {}` (Principle II).

| Operation | Reads / guards | Writes |
|-----------|----------------|--------|
| `record(draft)` | validate | tick HLC; insert `field_event(PENDING)`; insert `pending_op(QUEUED, attempts 0)` |
| `updateQuantity(id, q)` | event exists; status ≠ SYNCED; op.state ≠ IN_FLIGHT | update `field_event.quantity`; rewrite `pending_op.fields_json`, tick `hlc` (same `op_id`, same `seq`, status unchanged) |
| `delete(id)` | same guards as edit | delete `pending_op`; delete `field_event` |
| `retry(id)` | status = FAILED; op.state = FAILED | op: new `op_id`, `attempts = 0`, `state = QUEUED`, clear failure, tick `hlc`; event → PENDING |
| run start | none | `IN_FLIGHT → QUEUED`; `sync_run RUNNING → INTERRUPTED`; insert new `sync_run(RUNNING)`; trim log to 20 |
| claim batch | `state = QUEUED ORDER BY seq LIMIT n` | those rows → `IN_FLIGHT` |
| apply push outcome | ops in the claimed batch | acked: delete op + event SYNCED; rejected/refused/exhausted: op FAILED + event FAILED; retryable: `attempts += 1`, → QUEUED; update `sync_run` counters |
| run end | none | `sync_run` outcome + `finished_at_ms` |

`requestSync()` is called **after** the `record` and `retry` transactions commit. An edit doesn't
need a trigger. A PENDING event's op is already queued, and a FAILED event stays FAILED until the user
retries it explicitly.
