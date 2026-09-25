# Research: Offline Field Event Capture & Sync

Phase 0 output for [plan.md](plan.md). Each entry records a decision, why it was made, and what else
was considered. Decisions already fixed in `CLAUDE.md` are only restated here when the design needs
more detail than `CLAUDE.md` gives.

## R1. Library set and version policy

**Decision**: The approved set is the `CLAUDE.md` stack plus Navigation Compose and Robolectric, both
approved on 2026-09-25 and mirrored into `CLAUDE.md`. I treat the following as part of the approved
stack, because the approved items can't be used without them. None adds a capability of its own:
`androidx.core:core-ktx`, `androidx.activity:activity-compose`,
`androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`,
`androidx.hilt:hilt-lifecycle-viewmodel-compose` (home of `hiltViewModel()` since androidx.hilt 1.3; replaces `hilt-navigation-compose`, approved at GATE 1 on 2026-09-25),
`androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` (Hilt workers),
`kotlinx-coroutines-android`, `androidx.room:room-testing` (the migration-test helper; unused until v2),
`androidx.work:work-testing`, `androidx.test:core`, `androidx.test.ext:junit`,
`androidx.compose.ui:ui-test-junit4` / `ui-test-manifest`, and the Room Gradle plugin for schema
export. Versions are **not** chosen here. Implementation Phase 1 looks up the latest stable of each
artifact and checks these coupling points: KSP ↔ Kotlin, Hilt ≥ 2.51 on KSP, Room ≥ 2.7 on KSP,
Retrofit ≥ 2.11 with its bundled kotlinx.serialization converter, Robolectric's supported max SDK
≥ the chosen compileSdk (otherwise pin `@Config(sdk = …)` to the highest Robolectric supports). **Applied at Phase 3 (2026-09-25):** Robolectric 4.17 needs Java 21 to run SDK 36+, and this machine has JDK 17, so tests run on SDK 35 via `src/test/resources/robolectric.properties` in each module with Robolectric tests. compileSdk/targetSdk stay 37, AGP
↔ Gradle ↔ compileSdk.

**Rationale**: the constitution forbids quoting versions from memory. Version lookup belongs to the
phase that writes `libs.versions.toml`.

**Alternatives considered**: pinning versions in the plan was rejected because they'd be stale by
Phase 1 and unverifiable now. MockK was rejected because it's not approved; hand-written fakes are
enough. The Paging library was rejected because it's not approved; see R12.

## R2. The FIFO queue (outbox)

**Decision**: The queue is the Room table `pending_op`, with `seq INTEGER PRIMARY KEY AUTOINCREMENT`,
a unique `op_id`, and a unique `(entity_type, entity_id)`. In v1 each event has at most one op, its
CREATE. Delivery order is `seq ASC`. Edits and manual retries update the row in place, so they
**keep `seq`**. A retried event goes back to its original place in the recording order (FR-011,
SC-004), not to the back of the queue.

**Rationale**: `AUTOINCREMENT` is monotonic even if the wall clock moves backwards, which covers the
clock-skew edge case. A persisted queue survives process death (Principle I). An in-memory `ArrayDeque`
would not.

**Alternatives considered**: ordering by the UUIDv7 `id` was rejected because it's time-based and
breaks if the device clock is set backwards. Ordering by event `timestamp` is ruled out by the spec
(caller-supplied, may be skewed). A new op row per retry was rejected because it would move retried
events to the back and complicate the one-op-per-entity invariant.

## R3. Status storage and in-flight marking

**Decision**: `field_event.status` stores `PENDING | SYNCED | FAILED`, indexed, so the status filter
is a plain indexed query. `pending_op.state` stores `QUEUED | IN_FLIGHT | FAILED`, plus `attempts`,
`last_error`, and `failure_kind (REJECTED | EXHAUSTED | REFUSED)`. The two are only ever changed
together in one transaction. Invariants are in [data-model.md](data-model.md#invariants).

Claiming a batch (`SELECT … WHERE state='QUEUED' ORDER BY seq LIMIT n`, then `UPDATE … SET
state='IN_FLIGHT'`) is one transaction. At the **start of every run**, `IN_FLIGHT → QUEUED` for all
rows. That's safe because only one run exists at a time (R4), and it undoes the marking left behind by
a process kill.

**Rationale**: FR-009a requires in-flight events to be read-only, and Principle I forbids keeping that
in memory. SQLite serializes writers, so an edit transaction either runs before the claim (and the new
values are sent) or after it (and sees `IN_FLIGHT` and is refused).

**Alternatives considered**: deriving status purely from op existence was rejected because the status
filter would need a LEFT JOIN with CASE logic, and it's harder to read. A `run_id` column instead of a
state was rejected because it needs the same reset at run start and adds nothing.

## R4. One run at a time, and triggers

**Decision**:
- The single entry point is `SyncScheduler.requestSync(expedited: Boolean, delay: Duration = ZERO)`.
  It calls `enqueueUniqueWork("field-event-sync", APPEND_OR_REPLACE, request)` with
  `NetworkType.CONNECTED` and exponential backoff (base 30 s).
- The background path: `:core:data` calls `requestSync()` after every write that creates or re-queues
  an op. WorkManager holds the request until the network constraint is met, including across process
  death and reboot.
- The foreground path: `ForegroundConnectivityTrigger` (a `SyncTrigger`) registers
  `ConnectivityManager.registerDefaultNetworkCallback` in `MainActivity.onStart` and unregisters in
  `onStop`. `onAvailable` calls `requestSync(expedited = true)`. It uses the Activity lifecycle, so
  `lifecycle-process` isn't needed.
- On API < 31, expedited work runs as a foreground service. So `SyncWorker` overrides
  `getForegroundInfo()` with a low-importance "Syncing field events" notification channel, and the
  manifest declares `foregroundServiceType="dataSync"` on WorkManager's `SystemForegroundService`
  plus `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` (API 34). The request uses
  `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`.
- Flapping: each append adds at most one extra run to the chain, and a run that finds the outbox empty
  finishes in one query. That's the coalescing FR-015 asks for, with no in-memory flag.

**Rationale**: this is the `CLAUDE.md` decision, with the Android details it implies. Unique work
plus append means a parallel run can't happen (FR-015, edge case "connectivity flapping").

**Alternatives considered**: `KEEP` was rejected because a trigger during a run would be dropped, and
events recorded mid-run would wait for the next connectivity change. `REPLACE` was rejected because it
cancels the active run mid-batch.

## R5. Classifying push results, attempts, and backoff

**Decision**: `PushOutcomeClassifier` maps a push call to exactly one outcome. The full table is in
[contracts/sync-api.md](contracts/sync-api.md#client-handling-of-push-results). In short:
- 2xx with a parseable body: per op, `acked` → delete op, event `SYNCED`. `rejected` → op `FAILED`
  (`REJECTED`, server reason), event `FAILED`. Neither → transport handling for that op (Open
  Question 2).
- `IOException`, 5xx, unparseable 2xx body: **transport**. Every op in the batch gets `attempts += 1`.
  An op that reaches 5 becomes `FAILED` (`EXHAUSTED`, reason
  `"Could not reach server after 5 attempts: <last error>"`), and the rest go back to `QUEUED`. The run
  stops and the worker returns `Result.retry()`, so WorkManager backs off.
- 429: transport handling as above, then the run stops,
  `requestSync(delay = Retry-After ?: 30 s)` is called, and the worker returns `Result.success()`. The
  appended delayed request honors the server's `Retry-After`, which `Result.retry()` can't do.
- Other 4xx: every op in the batch becomes `FAILED` (`REFUSED`, `"HTTP <code>"`), and the run stops
  with `Result.success()` (Open Question 3).

`attempts` counts pushes of **that op**, so a batch-level transport failure costs each op in it one
attempt. The spec's "5 consecutive" is modeled by the counter never resetting except on manual retry.
Only a transport failure increments it, and an ack ends the op.

**Rationale**: this follows FR-013/014/017/019 and the contract's retryable set. Persisting attempts in
Room is what makes "5" survive process death.

**Alternatives considered**: reading WorkManager's `runAttemptCount` as the attempt counter was
rejected because it's per work request, not per op, and resets when a new request is appended.

## R6. UUIDv7

**Decision**: `Uuid7.next(): java.util.UUID` in `:core:model`, per RFC 9562 §5.7 with the monotonic
counter method (§6.2, method 1). It uses a 48-bit Unix ms timestamp and a 12-bit `rand_a` counter
seeded randomly each new millisecond and incremented within the same millisecond. `rand_b` is 62
random bits from `SecureRandom`. If the clock goes backwards or the counter overflows, the timestamp
is taken as `lastMs` (+1 on overflow). The generator is `@Synchronized`, with a `Clock` injectable for
tests. Its state is in-memory, which is fine because ids need uniqueness, not ordering, across
restarts (R2).

**Rationale**: `CLAUDE.md` decision 7. It's about 40 lines and fully unit-testable.

**Alternatives considered**: `kotlin.uuid.Uuid` has no v7 generator (and is experimental). A
third-party library isn't approved.

## R7. Hybrid logical clock

**Decision**: The string format agreed in the setup reply is
`"%019d-%04d-%s".format(wallMs, counter, nodeId)`, which sorts lexicographically. State
(`hlc_wall_ms`, `hlc_counter`, `node_id`) lives in the single-row `sync_state` table. `HlcClock.tick()`
must be called **inside** the caller's transaction. It reads the state, computes
`max(now, last.wall)` with counter bump, writes the state back, and returns the value. It's used for
every op create, edit, and retry. `node_id` is 16 lowercase hex chars from `SecureRandom`, generated
once through the database `onCreate` callback. The pure tick/merge math lives in `:core:model` `Hlc`.
The Room-bound clock lives in `:core:sync`, and `:core:data` reaches it through the dependency on
`:core:sync`.

**Rationale**: Principle I says no sync state in memory. Ticking inside the write transaction means
the HLC and the op it stamps can't tear (Principle II).

**Alternatives considered**: an in-memory HLC seeded from `max(hlc)` in `pending_op` at start was
rejected because acked ops are deleted, so the max can go backwards.

## R8. Fields codec and time

**Decision**: `pending_op.fields_json` stores the exact `JsonObject` that goes on the wire.
`FieldEventCodec` (`:core:sync`) encodes `FieldEventFields(workerId, blockId, quantity, timestamp)`.
`timestamp` is `Instant.toString()` (ISO-8601 UTC, e.g. `2025-06-10T08:32:00Z`). Timestamps are
truncated to milliseconds at capture and stored as `timestamp_ms INTEGER`. `schemaVersion = 1`. The
capture form stamps `Instant.now(clock)`, where `Clock` is injected. v1 has no date or time picker; the
spec's example is "as it happens" recording. `java.time` is available natively at minSdk 26, so no
desugaring is needed.

**Rationale**: storing the encoded payload means the engine sends exactly what was committed, and a
later schema change can't reinterpret old rows.

**Alternatives considered**: re-encoding from `field_event` at push time was rejected because it
couples the engine to the entity table and breaks the future multi-entity outbox.

## R9. FakeSyncServer and debug wiring

**Decision**: `FakeSyncServer : okhttp3.Interceptor` in `:core:testing` short-circuits
`/sync/push`, `/sync/pull`, and `/sync/snapshot` with in-memory state: an acked `opId` set for
idempotency, stored entities, and a cursor counter. It has a scriptable fault queue for tests. The
behavior is in [contracts/fake-sync-server.md](contracts/fake-sync-server.md). `:app` binds it into
the OkHttp interceptor set from `src/debug` DI. `src/release` binds nothing. The `:core:testing`
dependency is `debugImplementation`, as decided. Test-only helpers there (`MainDispatcherRule`) take
JUnit as a `compileOnly` dependency, and consuming test source sets bring their own, so JUnit doesn't
end up in the debug APK.

**Rationale**: `CLAUDE.md` decision 8. An interceptor exercises the real Retrofit + serialization path.

**Alternatives considered**: MockWebServer was rejected because it's not approved and redundant with
the interceptor.

## R10. Observable sync activity (FR-020)

**Decision**: The Room table `sync_run` has these columns: id, started_at_ms, finished_at_ms?, outcome
(`RUNNING | COMPLETED | TRANSPORT_ERROR | RATE_LIMITED | REFUSED | INTERRUPTED`), batches, acked,
rejected, last_error. The engine inserts a row at run start (first marking any `RUNNING` rows
`INTERRUPTED`, in the same transaction as the in-flight reset) and updates it after each batch and at
the end. Only the latest 20 rows are kept. The UI's `SyncBanner` observes the latest row. Per-event
outcomes are already observable as status changes on `field_event`.

**Rationale**: Principle I says the UI observes Room only. Logging each run to Room also makes
"interrupted" visible after a process kill, which WorkManager's `WorkInfo` doesn't show.

**Alternatives considered**: `WorkInfo` progress `Flow` was rejected because it's not Room, it's lost
when the chain advances, and it can't express "interrupted". An in-memory `SharedFlow` was rejected
because it's forbidden by Principle I.

## R11. Validation and write failures

**Decision**: `FieldEventValidator` in `:core:model` checks the raw form input. `workerId` and
`blockId` must be non-blank after trim. `quantity` must parse as an `Int` and be `≥ 1`. It returns a
list of `ValidationError`s, so the form can show every reason at once (FR-006). The repository
re-validates before writing. `SQLiteFullException` is caught in the repository and returned as
`StorageFull` (edge case "storage full"). Nothing is written, because the transaction rolls back.
Editing is limited to `quantity`, per FR-007 and US4.

**Rationale**: the spec explicitly names quantity as the editable field. Validating twice protects the
invariant regardless of caller.

**Alternatives considered**: throwing in the value class `init` was rejected because it turns user
input errors into exceptions. Editing worker, block, and timestamp was rejected as beyond FR-007.

## R12. Large backlogs and list performance

**Decision**: The engine never loads more than one batch. The claim query uses `LIMIT :batchSize`,
and each batch is its own transaction. The list screen observes a projection query (the columns the
row needs) with `ORDER BY id DESC`, since UUIDv7 gives recording order for display. The query takes an
optional status filter, and mapping runs with `flowOn(Dispatchers.Default)` and
`distinctUntilChanged()`. At 5,000 rows, a re-emission per acked batch is about 100 re-queries over a
full sync, each off the main thread. Phase 5 verifies SC-007 with a 5,000-event test that measures
main-thread work in the ViewModel. If it fails, the fallback is a windowed query (`LIMIT` grown on
scroll), which doesn't need Paging.

**Rationale**: meets SC-003/SC-007 without an unapproved library.

**Alternatives considered**: Paging 3 was rejected because it's not approved and there's no evidence
yet that it's needed.

## R13. Test strategy

**Decision**: Everything runs with `./gradlew test` on the JVM.
- `:core:model`: plain JUnit.
- DAOs and repository: Robolectric plus in-memory Room with `allowMainThreadQueries()` off, and
  `setQueryExecutor` / `setTransactionExecutor` on the test dispatcher.
- "Kill and relaunch" (SC-001, SC-005): a **file-backed** Room in a temp dir. Close the database
  without finishing the run, open a new instance on the same file, run the engine again, then assert
  that the `FakeSyncServer` delivery log has every `opId` exactly once as acked and no event is lost.
- Worker: `WorkManagerTestInitHelper` + `TestDriver` (constraints met, delays elapsed), with Hilt
  replaced by a test `WorkerFactory`.
- ViewModels: `MainDispatcherRule` + Turbine.
- UI: Compose `createComposeRule()` on Robolectric (`@GraphicsMode(NATIVE)`).

**Rationale**: every phase gate can show green output without an emulator (the user's choice on
2026-09-25).

**Alternatives considered**: instrumented tests were rejected by the user for the gate.
