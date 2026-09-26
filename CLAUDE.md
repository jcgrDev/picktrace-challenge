# picktrace-challenge

Offline-first Android app for recording field events (a worker handled a quantity at a block)
while disconnected, and syncing them to a backend when connectivity returns.
Greenfield. Built in approved phases with Spec Kit (`specs/`, `.specify/`).

## Identity

- App name: **picktrace-challenge**
- Package: `com.jcgrdev.picktracechallenge` (proposed; change before Phase 1 if wrong)
- Feature spec: `specs/001-field-event-sync/spec.md`
- Session transcript: `interview-session.md` (refreshed with `/export interview-session.md`; committed)

## Domain (first iteration)

- Single synced entity: **FieldEvent** `{ id: UUIDv7, workerId: WorkerId, blockId: BlockId, quantity: Int, timestamp: Instant (UTC), status: pending | synced | failed }`
- `WorkerId` and `BlockId` are Kotlin value classes in `:core:model` (distinct types, never interchangeable).
- Worker and Block master data are out of scope; the ids are opaque strings entered by the user.
- Feature modules:
  - `:feature:capture` records a new event (form, validation, save-offline)
  - `:feature:events` lists events, filters by status, shows detail with failure reason and attempts, manual retry, edit/delete of pending or failed events

## Stack (decided)

- Kotlin 2.x, Jetpack Compose (Material 3) via Compose BOM
- Clean Architecture + MVVM, unidirectional data flow: immutable `UiState` exposed as `StateFlow`
- DI: Hilt. Annotation processing: KSP only (no kapt)
- Persistence: Room
- Networking: Retrofit + OkHttp + kotlinx.serialization
- Background: WorkManager with Hilt workers
- Push: Firebase Cloud Messaging is **deferred** to a later phase. Keep a `SyncTrigger` seam.
- Navigation: Navigation Compose with type-safe routes (approved 2026-09-25 at `/speckit-plan`)
- Testing: JUnit, kotlinx-coroutines-test, Turbine, in-memory Room, WorkManager testing, Compose UI tests,
  Robolectric so Room/WorkManager/Compose tests run on the JVM (approved 2026-09-25 at `/speckit-plan`)
- Build: Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), convention plugins in `build-logic`
- minSdk 26, targetSdk/compileSdk latest stable
- Latest **stable** versions only, looked up at Phase 1 (never from memory). Coupling points to check:
  KSP matches Kotlin; Hilt 2.51+ for KSP; Room 2.7+ for KSP; Retrofit 2.11+ for the bundled
  kotlinx.serialization converter.

## Modules and dependency graph

```
:app ──> :feature:capture, :feature:events, :core:data, :core:sync, :core:network, :core:designsystem
         :core:testing (debugImplementation only, for FakeSyncServer)
:feature:capture, :feature:events ──> :core:data, :core:model, :core:designsystem
:core:data ──> :core:database, :core:sync, :core:model
:core:sync ──> :core:database, :core:network, :core:model
:core:network ──> :core:model
:core:database ──> :core:model
:core:designsystem ──> (nothing internal)
:core:model ──> (nothing)
:core:testing ──> :core:model, :core:database, :core:network, :core:sync
```

- `:core:model` pure Kotlin domain models, value classes, `Uuid7` generator (hand-rolled, RFC 9562)
- `:core:database` Room DB, entities (incl. `PendingOp`), DAOs, migrations
- `:core:network` Retrofit `SyncApi`, DTOs
- `:core:sync` `SyncEngine`, HLC, workers, `SyncScheduler`, `SyncTrigger`
- `:core:data` repositories (entity write + `PendingOp` insert in one transaction, then `requestSync()`)
- `:core:designsystem` theme, shared components
- `:core:testing` fakes, test rules, `FakeSyncServer` (OkHttp `Interceptor` implementing the contract in memory)
- `:feature:capture`, `:feature:events` screens + ViewModels (see "Domain")

## Backend contract (not implemented yet; build against `FakeSyncServer`)

- `POST /sync/push` body `{ "ops": [PendingOp] }` (<= 500) -> `{ "acked": [opId], "rejected": [{ "opId", "reason", "serverVersion"? }] }`. Idempotent by `opId`.
- `GET /sync/pull?cursor=&limit=` -> `{ "changes": [Change], "nextCursor": Long, "hasMore": Boolean }`. Deletes are tombstones. `410 Gone` = cursor expired.
- `GET /sync/snapshot?page=` -> `{ "entities": [Change], "page": Int, "hasMore": Boolean, "cursor": Long }` (confirmed shape).
- `429` + `Retry-After` and `5xx` are retryable; other `4xx` are not.
- Real base URL comes from `BuildConfig`. Debug builds install `FakeSyncServer`.
- DTOs as agreed in the setup reply (see `interview-session.md`): `PendingOpDto`, `FieldEventFields`,
  `PushRequest`, `PushResponse`, `RejectedOp`, `ChangeDto`, `PullResponse`, `SnapshotResponse`, `SyncApi`.
  `fields` travels as `JsonObject`; `:core:sync` owns the typed codec per entity type.

## Sync semantics (decided)

- **Push-only first.** Pull and snapshot DTOs exist from Phase 1; the pull/snapshot engine, cursor
  expiry handling and rebase are a later phase.
- **Status mapping**: `pending` = op row exists; `synced` = acked (op row deleted in the same
  transaction as the status update); `failed` = rejected, or transport attempts exhausted (op row
  kept with reason and attempt count).
- **Rejected vs retry**: a per-op `rejected` is permanent -> `failed`. Manual retry issues a **new op
  with a new opId**. Transport failures (no network, 429, 5xx) keep the op `pending` and increment
  attempts; after 5 transport attempts the event becomes `failed` until manually retried.
- **Edits before sync**: editing a pending event rewrites the existing CREATE op's fields in place;
  deleting a pending event removes entity and op together. No UPDATE/DELETE op is sent in v1.
  Synced events are read-only locally (spec FR-009a).
- **Triggers**: WorkManager unique work with `NetworkType.CONNECTED` for background, plus a
  foreground `ConnectivityManager` callback that enqueues an expedited run. Only one run at a time;
  a trigger during a run means "run again after" (`ExistingWorkPolicy.APPEND_OR_REPLACE`).
- **HLC node id**: one random id per install, stored in Room.
- **Delivery order**: by local recording sequence, oldest first, batches of 50 (configurable).

## Backend (harvest sync POC, approved 2026-09-25)

- Design: `specs/002-harvest-sync-backend/spec.md`; plan, data model and contracts beside it.
- Lives in `backend/` as a **standalone Gradle build** (subprojects `:server`, `:loadtest`), not
  part of the Android build. Package `com.jcgrdev.picktrace.sync`.
- Stack: Kotlin 2.4.20, JDK 17, Ktor 3.5.2 (Netty, content negotiation, status pages, call id,
  call logging, test host; CIO client in `:loadtest`), HikariCP 7.1.0, PostgreSQL JDBC 42.7.12
  with plain parameterised JDBC, Flyway 13.8.0, kotlinx.serialization 1.11.0, coroutines 1.11.0,
  Logback (look up version when added), JUnit Jupiter 6.1.3, Testcontainers 2.0.5. PostgreSQL 18.
  Versions looked up 2026-09-25; re-check when adding.
- Contract additions on top of the client contract above (approved 2026-09-25):
  - full path `POST /v1/sync/push` (client base URL ends in `/v1`);
  - `rejected[]` entries may carry an optional `detail` string; clients ignore unknown keys;
  - per-op reasons: `INVALID_RECORD`, `OP_CONTENT_MISMATCH`, `ID_CONFLICT`, `UNSUPPORTED_OP_KIND`
    (all permanent); whole-batch: 400 `MALFORMED_REQUEST`, 401 `MISSING_DEVICE_ID`,
    413 `BATCH_TOO_LARGE`, 415, 429 `BUSY` + `Retry-After`, 503 `DATABASE_UNAVAILABLE` + `Retry-After`, 500.
- Rules: PostgreSQL is the single source of truth (no cache, no in-memory dedup); record and op
  row written in one READ COMMITTED transaction, rows supplied sorted by (entityId, opId); acks
  built only from committed rows; nothing external inside a transaction; no `GlobalScope` or
  `runBlocking` in `:server` (`:loadtest` main may block).

## Non-negotiable principles (every phase)

1. Room is the single source of truth; the UI observes Room `Flow`s only.
2. Entity change + `PendingOp` insert always happen in one `db.withTransaction {}`.
3. Ops carry changed fields only, with client-generated UUIDv7 `opId`/`entityId`, `schemaVersion`, and an HLC timestamp.
4. Push before pull; delete outbox rows only after ack.
5. Apply a pulled page and save its cursor in ONE transaction.
6. Never overwrite fields that still have pending local ops (rebase).
7. No sync state in memory. Outbox/cursor live in Room; scheduling lives in WorkManager.

## Working rules

- Ask before adding any library not listed above.
- No `GlobalScope`, no `runBlocking` in production code.
- Ask instead of inventing endpoints or fields.
- Keep the project building and all tests passing at the end of every phase.
- End of every phase: summarize changes, list files touched, list open questions, then STOP and wait for approval.
- Evidence before claims: show the build/test output that backs any "it works".

## Open decisions

- None. Setup questions 1 to 12 answered on 2026-09-25 (see `interview-session.md`).
