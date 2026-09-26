# Tasks: Harvest Sync Backend POC

**Input**: Design documents from `specs/002-harvest-sync-backend/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
**Tests**: Included. The spec's Section 11 and Constitution Principle VI require test evidence at
every gate; test tasks precede the implementation they prove.
**Organization**: The spec is a design document with goals G1 to G7 instead of user stories. The
stories below are the delivery increments that prove those goals, in the order the plan's phases
B1 to B5 gate them. Each story is independently demonstrable against a real PostgreSQL.

| Story | Proves | Plan phase |
|-------|--------|------------|
| US1 A batch is stored exactly once and acked only after commit | G1, G2, G3, G5 | B2 + B3 |
| US2 Retries and conflicts resolve deterministically | G4, decision 5 and 6, late flag | B3 |
| US3 Correct under concurrency and overload | G6, G7, spec Section 8 | B4 |
| US4 Load proof with invariants | Section 11 load test | B5 |

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on unfinished tasks)
- **[Story]**: US1 to US4 as above
- Paths are repository-relative. `SRC` = `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync`,
  `TEST` = `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync`,
  `LOAD` = `backend/loadtest/src/main/kotlin/com/jcgrdev/picktrace/loadtest`. Every task spells the
  full path; the abbreviations are only for this legend.

## Constitution gates

Phases 1 and 2 together form plan phase B1; each later story is one plan phase. At the end of each
plan phase: `cd backend && ./gradlew test` output shown, summary, files touched, open questions,
compliance statement for Principles I to VI, then STOP for approval.

---

## Phase 1: Setup (plan B1, part 1)

**Purpose**: A standalone Gradle build under `backend/` that compiles, with the approved libraries
pinned in a catalog and a local PostgreSQL.

- [ ] T001 Create `backend/settings.gradle.kts` including `:server` and `:loadtest`, `backend/build.gradle.kts` with shared Kotlin JVM 17 toolchain, and copy the Gradle wrapper from the repository root into `backend/` (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)
- [ ] T002 Create `backend/gradle/libs.versions.toml` with the versions from `specs/002-harvest-sync-backend/research.md` (Kotlin 2.4.20, Ktor 3.5.2, HikariCP 7.1.0, pgjdbc 42.7.12, Flyway 13.8.0, kotlinx.serialization 1.11.0, coroutines 1.11.0, JUnit 6.1.3, Testcontainers 2.0.5) and look up the current stable Logback version on the day, recording the date in a header comment
- [ ] T003 Create `backend/server/build.gradle.kts` applying Kotlin JVM, kotlinx.serialization plugin and the Ktor application plugin, with main class `com.jcgrdev.picktrace.sync.ApplicationKt`, dependencies from the catalog (server-netty, content-negotiation, serialization-kotlinx-json, status-pages, call-id, call-logging, HikariCP, pgjdbc, flyway-core, flyway-database-postgresql, logback) and test dependencies (junit-jupiter, ktor-server-test-host, testcontainers, testcontainers-postgresql, testcontainers-junit-jupiter, kotlinx-coroutines-test), JUnit Platform enabled
- [ ] T004 [P] Create `backend/loadtest/build.gradle.kts` applying Kotlin JVM and the application plugin with main class `com.jcgrdev.picktrace.loadtest.MainKt`, depending on `:server` (for DTOs and canonical form), ktor-client-cio, ktor-client-content-negotiation, serialization-kotlinx-json, pgjdbc, coroutines
- [ ] T005 [P] Create `backend/docker-compose.yml` with service `postgres` (image `postgres:18`, database `harvest`, user and password `harvest`, port 5432, healthcheck) and services `server-a` and `server-b` built from `backend/server/Dockerfile` on host ports 8081 and 8082, environment per `specs/002-harvest-sync-backend/contracts/configuration.md`, depending on the postgres healthcheck
- [ ] T006 [P] Create `backend/server/Dockerfile` (JDK 17 runtime, runs the Gradle-installed distribution) and `backend/server/src/main/resources/logback.xml` (console appender, INFO root, call-id in the pattern)
- [ ] T007 [P] Create `backend/.gitignore` (`.gradle/`, `build/`, `*.log`) and `backend/README.md` pointing at `specs/002-harvest-sync-backend/quickstart.md`
- [ ] T008 Verify `cd backend && ./gradlew build` succeeds with empty source sets (placeholder `Application.kt` and `Main.kt` printing nothing) and record the output for the B1 gate

---

## Phase 2: Foundational (plan B1, part 2)

**Purpose**: Configuration, pool, schema migration, test container support and a running Ktor
app with a health route. Blocks every story.

- [ ] T009 Implement typed configuration in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/config/SyncConfig.kt`: one data class with every variable and default from `specs/002-harvest-sync-backend/contracts/configuration.md`, a `fromEnvironment()` loader, and range parsing for `SYNC_DEADLOCK_RETRY_DELAY_MS` and `SYNC_RETRY_AFTER_SECONDS`
- [ ] T010 [P] Write Flyway migration `backend/server/src/main/resources/db/migration/V1__harvest_sync.sql` creating `harvest_record` and `op_log` exactly as in `specs/002-harvest-sync-backend/data-model.md` (columns, primary keys, check constraints, `harvest_record_harvested_at_idx`, `op_log_entity_id_idx`, no foreign key)
- [ ] T011 [P] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/DataSourceFactory.kt` building a HikariCP `DataSource` from `SyncConfig` (pool size, connection timeout, `autoCommit` false, default transaction isolation READ COMMITTED) and `Migrations.kt` in the same package running Flyway against a `DataSource`
- [ ] T012 [P] Implement the Testcontainers JUnit extension in `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/support/PostgresExtension.kt`: one `postgres:18` container per JVM, migrations applied once, a `dataSource()` accessor, and a `truncateAll()` helper for test isolation
- [ ] T013 Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/SchemaTest.kt`: asserts both unique indexes exist by name, a duplicate `id` insert and a duplicate `op_id` insert each fail with SQLSTATE 23505, and `synchronous_commit`, `fsync`, `full_page_writes` report `on`
- [ ] T014 Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/Application.kt`: `main` loads `SyncConfig`, builds the `DataSource`, runs migrations when `SYNC_MIGRATE_ON_START` is true, and starts Netty on `SYNC_HTTP_PORT`; a `fun Application.syncModule(config, dataSource)` installs ContentNegotiation (kotlinx JSON with `ignoreUnknownKeys`), CallId, CallLogging, StatusPages and the routes, so tests can call `syncModule` on the test host
- [ ] T015 [P] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/HealthRoute.kt` (`GET /health`: borrow and return a pooled connection, 200 `{"status":"ok"}` or 503) and `ErrorResponse.kt` with the `{ "error", "message" }` DTO
- [ ] T016 [P] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/ErrorMapping.kt`: StatusPages rules mapping `BadRequestException`/serialization errors to 400 `MALFORMED_REQUEST`, a `MissingDeviceIdException` to 401, a `BatchTooLargeException` to 413, unsupported media type to 415, a `PoolExhaustedException` to 429 `BUSY` with random `Retry-After`, a `DatabaseUnavailableException` to 503 with `Retry-After`, everything else to 500 `INTERNAL`, each with the body from `specs/002-harvest-sync-backend/contracts/sync-push-api.md`
- [ ] T017 Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/api/HealthRouteTest.kt` using the Ktor test host and `PostgresExtension`: `/health` returns 200 with the pool up
- [ ] T018 B1 gate: run `cd backend && ./gradlew test`, `docker compose up -d postgres` plus `./gradlew :server:run` and `curl localhost:8080/health`; write the phase summary (changes, files touched, open questions, compliance I to VI) and STOP

**Checkpoint**: schema exists with its unique indexes, the app boots, tests run against a real
PostgreSQL.

---

## Phase 3: US1 — A batch is stored exactly once and acked only after commit (plan B2 + B3) 🎯 MVP

**Goal**: `POST /v1/sync/push` accepts a batch, validates each op, stores valid records and their
op rows in one transaction, and returns `acked`/`rejected` built only from committed rows. Proves
G1, G2, G3 and G5 for the single-request case.

**Independent Test**: send the two-op example from `contracts/sync-push-api.md`, get one ack and
one `INVALID_RECORD`; resend it, get the identical response with no new rows; cut the connection
before commit in a test, get nothing stored.

### Tests for US1

- [ ] T019 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/canonical/CanonicalFormTest.kt` with all seven vectors from `specs/002-harvest-sync-backend/contracts/canonical-form.md`, asserting canonical strings and hex fingerprints, plus V1 = V2 = V7
- [ ] T020 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/validation/OpValidatorTest.kt` covering every rule and boundary in the validation table of `contracts/sync-push-api.md` (quantity 0, 1, max, max+1, non-integer; timestamp at tolerance edge ± 1 s; empty and 129-char ids; missing `fields`; wrong `opType`/`entityType`) and the exact `detail` strings
- [ ] T021 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/planning/BatchPlannerTest.kt`: request-order index preserved, work list sorted by (`entityId`, `opId`), identical duplicate opIds collapse to one, differing duplicate opIds become `OP_CONTENT_MISMATCH` without entering the work list, same-`entityId` ops resolve first-wins with `ID_CONFLICT` for a differing later one
- [ ] T022 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/api/SyncPushRouteTest.kt` (Ktor test host + `PostgresExtension`): 400 for non-JSON and for missing `ops`, 401 for missing header, 413 for 501 ops, 415 for wrong content type, 200 with the two-op example producing one ack and one `INVALID_RECORD`, unknown top-level and envelope keys ignored, every distinct opId exactly once in request order
- [ ] T023 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/CorrectnessTest.kt`: fresh batch of 50 stores 50 records and 50 op rows with `late` false and matching fingerprints; identical resend returns identical acks and row counts unchanged; mixed batch stores only valid rows; `received_at` identical for all rows of one batch; every `op_log.entity_id` has a record
- [ ] T024 [P] [US1] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/CrashInjectionTest.kt` using a wrapping `DataSource` that throws on `commit` or closes the connection after pass 3: nothing stored and the request fails with 503; and a variant that fails after commit but before the response is built: rows stored, the immediate resend acks everything

### Implementation for US1

- [ ] T025 [P] [US1] Create domain types in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/domain/`: `IncomingOp.kt`, `PlannedOp.kt`, `Outcome.kt` (sealed `Ack`/`Rejected(reason, detail?)`), `RejectReason.kt` enum (`INVALID_RECORD`, `OP_CONTENT_MISMATCH`, `ID_CONFLICT`, `UNSUPPORTED_OP_KIND`), `WorkList.kt`, `RequestClock.kt` as described in `specs/002-harvest-sync-backend/data-model.md`
- [ ] T026 [P] [US1] Create wire DTOs in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/SyncDtos.kt`: `PushRequest`, `PendingOpDto` (mirroring the client's fields, `fields` as `JsonObject?`, `baseVersion` nullable), `PushResponse`, `RejectedOpDto` (`opId`, `reason`, `detail?`, `serverVersion?` always null)
- [ ] T027 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/canonical/CanonicalForm.kt` (builds the `v1|...` string per `contracts/canonical-form.md`, including UUID lower-casing, UTF-8 length prefixes, instant parsing with any offset and millisecond truncation) and `Fingerprint.kt` (SHA-256 to 32 bytes, hex helper)
- [ ] T028 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/validation/OpValidator.kt`: returns either a validated typed record (`workerId`, `blockId`, `quantity`, `harvestedAt`) or a `Rejected` outcome with the exact `detail` strings from the contract, using `SyncConfig.maxQuantity` and `futureTolerance` and the `RequestClock`
- [ ] T029 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/planning/BatchPlanner.kt`: steps 3 to 8 of spec Section 6 (fingerprint, collapse duplicates, validate, resolve same-record conflicts, sort, keep first-occurrence index), producing `WorkList` plus pre-transaction outcomes
- [ ] T030 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/SyncPasses.kt`: pass 1 read known ops by opId list; pass 2 multi-row insert into `harvest_record` with conflict-do-nothing returning inserted ids, then read fingerprints of conflicting ids; pass 3 the same for `op_log`, then read and compare, deleting a record this transaction inserted whose op turned out to mismatch; rows always supplied in `WorkList` order; every statement parameterised
- [ ] T031 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/TransactionRunner.kt`: borrows a connection under `withContext(Dispatchers.IO)` (translating Hikari's acquisition timeout into `PoolExhaustedException` and connection failure into `DatabaseUnavailableException`), runs the three passes with `autoCommit` false, commits, returns outcomes only after commit; on failure rolls back and rethrows; retry loop for SQLSTATE 40P01/40001 up to `SyncConfig.deadlockRetries` with random delay, discarding partial outcomes; exposes counters for retries and transaction duration
- [ ] T032 [US1] Implement `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/SyncPushRoute.kt`: `POST /v1/sync/push` reads the header (401 if missing), enforces body and batch size (413), decodes `PushRequest` (400 on failure or unparseable `opId`), captures `RequestClock`, runs `BatchPlanner`, skips the transaction when the work list is empty, calls `TransactionRunner`, merges outcomes ordered by first occurrence, responds 200 `PushResponse`
- [ ] T033 [US1] Register the route in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/Application.kt` and add the `late` computation (`receivedAt - harvestedAt > syncWindow`) to the record row construction in `SyncPasses.kt`
- [ ] T034 [US1] Run `cd backend && ./gradlew test`; make T019 to T024 pass; write the B2/B3 gate summary and STOP

**Checkpoint**: a single device can sync a batch, resend it, and never get an ack without a row.

---

## Phase 4: US2 — Retries and conflicts resolve deterministically (plan B3)

**Goal**: every retry case in spec Section 7 and every per-op rejection resolves as specified,
including decision 6 (new opId for an existing identical record acks and logs a second op row)
and the late flag.

**Independent Test**: from a stored batch, resend one op with a changed quantity and get
`OP_CONTENT_MISMATCH`; change only its opId and get `ID_CONFLICT`; restore the quantity with the
new opId and get an ack with two op rows for that record.

### Tests for US2

- [ ] T035 [P] [US2] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/IdempotencyTest.kt` covering the seven rows of the retry table in spec Section 7 against `PostgresExtension`, asserting the response and the exact row counts and fingerprints after each step
- [ ] T036 [P] [US2] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/InRequestDuplicatesTest.kt` for the four rows of the "duplicate opIds within one request" table in spec Section 6, end to end through the route
- [ ] T037 [P] [US2] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/LateFlagTest.kt`: harvest time `window − 1 s` before `received_at` stores `late` false, `window + 1 s` stores `late` true, a future timestamp inside tolerance stores `late` false, and a record resent days later keeps its original `late` value

### Implementation for US2

- [ ] T038 [US2] Fix any behaviour T035 to T037 expose in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/SyncPasses.kt` and `planning/BatchPlanner.kt` (expected: none beyond `detail` wording; record deviations in the gate summary)
- [ ] T039 [US2] Add the rejection `detail` strings for `OP_CONTENT_MISMATCH` (`opId already stored with different content`), `ID_CONFLICT` (`record exists with different content`) and `UNSUPPORTED_OP_KIND` in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/domain/RejectReason.kt` and assert them in T035
- [ ] T040 [US2] Run `cd backend && ./gradlew test`; write the B3 gate summary and STOP

**Checkpoint**: US1 and US2 together cover the full single-request contract.

---

## Phase 5: US3 — Correct under concurrency and overload (plan B4)

**Goal**: hundreds of overlapping requests across two instances produce no duplicate, no ack
without a row, no deadlock in well-formed traffic, and a fast 429 when the pool is exhausted.
Proves G6 and G7 and every point of spec Section 8.

**Independent Test**: fire the same 500-op batch from 50 coroutines at two app instances sharing
one container; every response has identical acks, 500 rows in each table, deadlock retry counter
zero.

### Tests for US3

- [ ] T041 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/SameBatchRaceTest.kt`: two `syncModule` instances on the test host over one `PostgresExtension`; 50 parallel sends of an identical 500-op batch alternating instances; assert identical `acked` lists, exactly 500 rows per table, retry counter 0
- [ ] T042 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/OverlappingBatchRaceTest.kt`: 20 batches sharing a random 30 % of record ids with identical content, each batch in shuffled request order, sent in parallel; assert each id stored once, each request acks all its ops, retry counter 0
- [ ] T043 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/PathologicalOpIdRaceTest.kt`: same opId with two different entityIds sent in parallel repeatedly; assert exactly one ack and one `OP_CONTENT_MISMATCH` per pair, no record without an op row, and that the retry path (if triggered) still yields these results
- [ ] T044 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/persistence/DeadlockRetryTest.kt`: a `SyncPasses` test double that throws SQLSTATE 40P01 on the first N attempts; assert the runner retries up to the configured budget, discards partial outcomes, and returns 503 `DATABASE_UNAVAILABLE` with `Retry-After` once exhausted
- [ ] T045 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/api/BackpressureTest.kt`: pool size 1 and connection timeout 200 ms; hold the only connection in a test thread; assert `/v1/sync/push` returns 429 `BUSY` with `Retry-After` between 5 and 30 within well under one second, and that `/health` returns 503 meanwhile
- [ ] T046 [P] [US3] Write `backend/server/src/test/kotlin/com/jcgrdev/picktrace/sync/api/RequestTimeoutTest.kt`: a `SyncPasses` double that sleeps beyond `SYNC_REQUEST_TIMEOUT_MS`; assert the request ends with 503 and the transaction was rolled back (no rows)

### Implementation for US3

- [ ] T047 [US3] Add request timeout handling in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/SyncPushRoute.kt` (`withTimeout(SyncConfig.requestTimeoutMs)` around the runner, cancellation rolls back and maps to 503) and make `TransactionRunner.kt` cancellation-safe (rollback and close in `finally`)
- [ ] T048 [US3] Add metrics in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/SyncMetrics.kt`: counters for deadlock retries, 429s, 503s; histogram-style min/max/p99 for transaction duration; exposed as JSON at `GET /metrics` in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/api/MetricsRoute.kt` for the load test to read
- [ ] T049 [US3] Verify multi-row inserts in `backend/server/src/main/kotlin/com/jcgrdev/picktrace/sync/persistence/SyncPasses.kt` are single statements with rows in `WorkList` order (not per-row statements) so the lock-order argument in spec Section 8.3 holds; document the statement shape in a comment referencing the spec section
- [ ] T050 [US3] Run `cd backend && ./gradlew test` (race tests may take tens of seconds); write the B4 gate summary including the retry counter and max transaction time observed, and STOP

**Checkpoint**: concurrency guarantees are demonstrated in-process across two app instances.

---

## Phase 6: US4 — Load proof with invariants (plan B5)

**Goal**: a repeatable run of 300 simulated devices with deliberate duplicates and invalid ops
against two containerised instances, followed by the five invariants from `data-model.md` and the
operational numbers from spec Section 11.

**Independent Test**: `./gradlew :loadtest:run` with the arguments in quickstart Section 4 prints
five PASS lines and the numbers; a second run with `SYNC_DB_POOL_SIZE=5` still passes with some
429s and every device fully synced.

### Implementation for US4

- [ ] T051 [P] [US4] Implement `backend/loadtest/src/main/kotlin/com/jcgrdev/picktrace/loadtest/Args.kt`: parse `--urls`, `--devices`, `--ops-per-device` (range), `--spread-seconds`, `--duplicate-rate`, `--invalid-rate`, `--db-url`, `--db-user`, `--db-password`, `--seed`
- [ ] T052 [P] [US4] Implement `backend/loadtest/src/main/kotlin/com/jcgrdev/picktrace/loadtest/DeviceSimulator.kt`: one coroutine per device with a random start delay inside the spread, batches of 50 in recording order, a `duplicate-rate` share of batches resent (half immediately in parallel, half after the response), an `invalid-rate` share of ops made invalid, exponential backoff honouring `Retry-After` on 429/503, one in-flight request per device; records every response in memory keyed by opId
- [ ] T053 [P] [US4] Implement `backend/loadtest/src/main/kotlin/com/jcgrdev/picktrace/loadtest/Invariants.kt`: connect with pgjdbc and check I1 to I5 from `specs/002-harvest-sync-backend/data-model.md` against the generator's sent set and recorded responses; read `/metrics` from every instance; print the report format shown in `specs/002-harvest-sync-backend/quickstart.md`
- [ ] T054 [US4] Implement `backend/loadtest/src/main/kotlin/com/jcgrdev/picktrace/loadtest/Main.kt` (a `runBlocking` main is permitted here per plan.md): wait for `/health` on every URL, run the simulator, run the invariants, exit non-zero if any invariant fails
- [ ] T055 [US4] Add the `--pool-size` note and the two run commands to `backend/README.md`; verify `docker compose up -d` brings up `server-a` and `server-b` healthy
- [ ] T056 [US4] Execute the standard load run and the undersized-pool run from `specs/002-harvest-sync-backend/quickstart.md` Section 4; save both outputs to `specs/002-harvest-sync-backend/evidence/load-run-standard.txt` and `evidence/load-run-pool5.txt`
- [ ] T057 [US4] Write the B5 gate summary with the two outputs, the p99 latency, 429 count, deadlock retries and max transaction time, and STOP

**Checkpoint**: G1 to G7 demonstrated under load on two instances.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T058 [P] Update the "The backend design" section of `README.md` with build and run commands and a link to `specs/002-harvest-sync-backend/quickstart.md`
- [ ] T059 [P] Walk through every step of `specs/002-harvest-sync-backend/quickstart.md` on a clean checkout and fix any drift in the document
- [ ] T060 [P] Confirm `specs/002-harvest-sync-backend/contracts/sync-push-api.md` and `specs/001-field-event-sync/contracts/sync-api.md` agree field by field; record any difference as an open question in `CLAUDE.md`
- [ ] T061 Review `backend/server` for `runBlocking`, `GlobalScope`, external calls inside `TransactionRunner`, and unparameterised SQL; fix and note in the final summary
- [ ] T062 Final summary: `cd backend && ./gradlew test` output, files touched across all phases, remaining open questions from spec Section 14, compliance statement I to VI

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: starts immediately. T001 → T002 → T003; T004 to T007 parallel after T002; T008 last.
- **Foundational (Phase 2)**: after Phase 1. T009 first; T010, T011, T012 parallel; T013 after T010 to T012; T014 after T009 and T011; T015, T016 parallel after T014; T017 after T015; T018 last. **Blocks all stories.**
- **US1 (Phase 3)**: after Phase 2. Tests T019 to T024 written first in parallel; T025, T026 parallel; T027 → T028 → T029 → T030 → T031 → T032 → T033 → T034.
- **US2 (Phase 4)**: after US1. T035 to T037 parallel; T038 → T039 → T040.
- **US3 (Phase 5)**: after US1 (US2 recommended first so `detail` strings are final). T041 to T046 parallel; T047 → T048 → T049 → T050.
- **US4 (Phase 6)**: after US3 (needs `/metrics`). T051 to T053 parallel; T054 → T055 → T056 → T057.
- **Polish (Phase 7)**: after US4. T058 to T060 parallel; T061 → T062.

### Story Dependencies

- US1 is the MVP and depends only on the foundation.
- US2 adds tests over US1's implementation; no new components.
- US3 adds timeout, metrics and race tests over US1; depends on US1, benefits from US2.
- US4 depends on US3's metrics endpoint and on Docker Compose from Setup.

### Parallel Opportunities

- Phase 1: T004, T005, T006, T007 at once.
- Phase 2: T010, T011, T012 at once; T015 and T016 at once.
- US1: all six test files T019 to T024 at once; T025 and T026 at once.
- US2: T035 to T037 at once.
- US3: T041 to T046 at once.
- US4: T051 to T053 at once.
- Polish: T058 to T060 at once.

### Parallel example, US1 tests

```
Write CanonicalFormTest.kt      (T019)
Write OpValidatorTest.kt        (T020)
Write BatchPlannerTest.kt       (T021)
Write SyncPushRouteTest.kt      (T022)
Write CorrectnessTest.kt        (T023)
Write CrashInjectionTest.kt     (T024)
```
All six touch different files and depend only on the contracts and Phase 2.

## Implementation Strategy

### MVP first (Phases 1 to 3)

1. Setup and foundation: build, schema, pool, health.
2. US1: the endpoint with the three passes and ack-after-commit.
3. STOP at the B2/B3 gate. At this point a device can sync and resend safely, which is the core
   property, proven for the single-request case.

### Incremental delivery

- US2 hardens the single-request contract with no new components.
- US3 proves the concurrency claims in-process and adds backpressure.
- US4 proves them with real containers and load, producing the evidence files.
- Each story ends at a constitution gate; nothing proceeds without approval.

## Format validation

All 62 tasks start with `- [ ]`, carry a sequential id T001 to T062, a `[P]` marker only where
files and dependencies allow, a story label on every task in Phases 3 to 6 and none elsewhere,
and at least one repository-relative file path.
