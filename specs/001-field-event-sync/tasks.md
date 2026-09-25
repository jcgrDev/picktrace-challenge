---
description: "Task list for Offline Field Event Capture & Sync"
---

# Tasks: Offline Field Event Capture & Sync

**Input**: Design documents from `specs/001-field-event-sync/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: These are required. Constitution Principle VI says every phase ends green with test
output shown, and [quickstart.md](quickstart.md#automated-validation-every-phase-gate) names the test
for each scenario. Within a story, write the tests first and confirm they fail before implementing.

**Organization**: Tasks are grouped by user story. The five **approval gates** from
[plan.md](plan.md#implementation-phases) are marked `GATE` tasks. At each one, run
`./gradlew build test` and show its output, then write the summary, the files touched, open questions,
and a Principles I–VI compliance statement, then STOP and wait for approval.

**Ordering (POC-first, 2026-09-25)**: the sync service is proven before any UI. Stage A is the
headless engine (US1 data layer → US2 engine → US3 batching), Stage B adds the real triggers
(WorkManager, connectivity), and the US1 screens come after GATE 3. Task IDs are unchanged from
the original story-order list, so IDs are no longer sequential in file order; follow file order.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from [spec.md](spec.md)

## Path Conventions

- `{pkg}` = `com/jcgrdev/picktracechallenge`. Kotlin sources live under `<module>/src/main/kotlin/{pkg}/<module path>/`, and tests under `<module>/src/test/kotlin/{pkg}/<module path>/`.
  Example: `core/model/src/main/kotlin/{pkg}/core/model/Uuid7.kt`.
- Kotlin package = `com.jcgrdev.picktracechallenge.<module path with dots>`, e.g. `…core.model`, `…feature.events.detail`.
- Android-dependent tests use Robolectric (`@RunWith(AndroidJUnit4::class)`); nothing goes in `androidTest`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: a multi-module Gradle build that compiles, with no feature code yet.

- [X] T001 Look up the **latest stable** version of every artifact listed in research.md §R1 (Kotlin, AGP, Gradle, KSP, Compose BOM, Navigation Compose, Hilt, androidx.hilt, Room, Retrofit, OkHttp, kotlinx.serialization, kotlinx.coroutines, WorkManager, Turbine, Robolectric, JUnit 4, androidx.test, core-ktx, activity-compose, lifecycle). Use primary sources (Maven Central / Google Maven / release notes), not memory. Check the R1 coupling points (KSP↔Kotlin, Hilt ≥2.51, Room ≥2.7, Retrofit ≥2.11 bundled converter, Robolectric max SDK vs compileSdk, AGP↔Gradle↔compileSdk). Write the results to `gradle/libs.versions.toml` (versions, libraries, plugins, and bundles `testing-unit` and `testing-android`), and keep the lookup table for the phase summary.
- [X] T002 Create the Gradle wrapper for the chosen Gradle version, `settings.gradle.kts` (pluginManagement `includeBuild("build-logic")`, google/mavenCentral, `TYPESAFE_PROJECT_ACCESSORS`, include `:app :core:model :core:database :core:network :core:sync :core:data :core:designsystem :core:testing :feature:capture :feature:events`), root `build.gradle.kts` (all plugins `apply false`), and `gradle.properties` (`android.useAndroidX=true`, `org.gradle.configuration-cache=true`, `picktrace.syncBaseUrl=https://sync.invalid/`)
- [X] T003 Create `build-logic/settings.gradle.kts` (uses the root version catalog) and `build-logic/convention/build.gradle.kts` (compileOnly AGP/Kotlin/KSP/Room/Hilt Gradle plugins; `gradlePlugin { plugins { … } }` registering ids `picktrace.jvm.library`, `picktrace.android.library`, `picktrace.android.application`, `picktrace.android.compose`, `picktrace.hilt`, `picktrace.android.room`, `picktrace.android.test`, `picktrace.android.feature`)
- [X] T004 [P] Implement `build-logic/convention/src/main/kotlin/JvmLibraryConventionPlugin.kt`: kotlin-jvm plugin, JVM toolchain 17, `testing-unit` bundle on testImplementation
- [X] T005 [P] Implement `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`: com.android.library + kotlin-android, compileSdk/targetSdk from T001, minSdk 26, Java/Kotlin 17, `testOptions.unitTests.isIncludeAndroidResources = true`, namespace `com.jcgrdev.picktracechallenge.` + the project path with `:` replaced by `.`
- [X] T006 [P] Implement `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`: com.android.application, same SDK/JVM settings, `buildFeatures.buildConfig = true`
- [X] T007 [P] Implement `build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`: Compose compiler Gradle plugin, `buildFeatures.compose = true`, Compose BOM platform on implementation and testImplementation, material3, ui-tooling-preview, debug ui-tooling, `ui-test-junit4` + `ui-test-manifest` for tests
- [X] T008 [P] Implement `build-logic/convention/src/main/kotlin/HiltConventionPlugin.kt`: KSP + Hilt plugins, `hilt-android` + `ksp(hilt-compiler)`. No kapt anywhere.
- [X] T009 [P] Implement `build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt`: KSP + `androidx.room` plugin, `room { schemaDirectory("$projectDir/schemas") }`, `api`(room-runtime, room-ktx) (consumers call `withTransaction` and see `RoomDatabase`), `ksp(room-compiler)`, testImplementation room-testing
- [X] T010 [P] Implement `build-logic/convention/src/main/kotlin/AndroidTestConventionPlugin.kt`: testImplementation of the `testing-unit` bundle (JUnit 4, coroutines-test, Turbine) + `testing-android` (Robolectric, androidx.test core, androidx.test.ext junit)
- [X] T011 Implement `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`, which applies library + compose + hilt + test and adds `:core:data`, `:core:model`, `:core:designsystem`, lifecycle-runtime-compose, lifecycle-viewmodel-compose, navigation-compose, hilt-lifecycle-viewmodel-compose, plus the kotlinx-serialization plugin for type-safe routes. Depends on T005, T007, T008, T010.
- [X] T012 Create one `build.gradle.kts` per module, with dependencies exactly per the CLAUDE.md graph: `core/model` (jvm.library), `core/network` (android.library + hilt + test + serialization plugin; `api`(retrofit, okhttp, kotlinx-serialization-json) (`SyncApi` returns `retrofit2.Response`, DTOs expose `JsonObject`, the interceptor set is `Set<okhttp3.Interceptor>`), `implementation`(retrofit kotlinx-serialization converter), `api(:core:model)`), `core/database` (android.library + room + hilt + test), `core/sync` (android.library + hilt + test; work-runtime-ktx, hilt-work, `ksp(androidx.hilt:hilt-compiler)`, work-testing; `:core:database :core:network :core:model`), `core/data` (android.library + hilt + test; `:core:database :core:sync :core:model`), `core/designsystem` (android.library + compose), `core/testing` (android.library + kotlinx-serialization plugin; `:core:model :core:database :core:network :core:sync`; `compileOnly` JUnit 4 + coroutines-test per research §R9), `feature/capture` and `feature/events` (android.feature), and an empty `AndroidManifest.xml` for each Android library under `src/main/`
- [X] T013 Create the `:app` skeleton: `app/build.gradle.kts` (application + compose + hilt + test; `buildConfigField("String","SYNC_BASE_URL", "\"${providers.gradleProperty("picktrace.syncBaseUrl").get()}\"")`; implementation of the two features, `:core:data :core:sync :core:network :core:designsystem`; `debugImplementation(project(":core:testing"))`; applicationId `com.jcgrdev.picktracechallenge`), `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/{pkg}/PicktraceApplication.kt` (`@HiltAndroidApp`), `app/src/main/kotlin/{pkg}/MainActivity.kt` (`@AndroidEntryPoint`, `setContent {}` placeholder)
- [X] T014 Run `./gradlew build test` and fix until green with zero tests. Checkpoint only, not a gate.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: pure domain, wire DTOs, and sync seams. Every story depends on these. (The theme, T077, moved to Phase 7.)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first)

- [X] T015 [P] Write `core/model/src/test/kotlin/{pkg}/core/model/Uuid7Test.kt` with a fixed `Clock`. Cover: version nibble = 7, variant bits = `10`, top 48 bits = epoch ms, 10,000 ids in the same ms strictly increasing, a clock set backwards still gives increasing ids, and counter overflow rolls the timestamp forward by 1 ms.
- [X] T016 [P] Write `core/model/src/test/kotlin/{pkg}/core/model/HlcTest.kt`. Cover: `encode()` matches `^\d{19}-\d{4}-[0-9a-f]{16}$`, string order equals `(wallMs, counter)` order, and `tick`'s three cases (now > last → counter 0; now == last → counter+1; now < last → keep last wall, counter+1). Also `parse(encode(x)) == x`.
- [X] T017 [P] Write `core/model/src/test/kotlin/{pkg}/core/model/FieldEventValidatorTest.kt` covering every row of the data-model.md validation table, several errors reported together, trimming of the ids, and `validateQuantity` alone
- [X] T018 [P] Write `core/network/src/test/kotlin/{pkg}/core/network/SyncDtoSerializationTest.kt`. Cover: the push request/response JSON examples from contracts/sync-api.md decode and re-encode equal, a null `baseVersion` is omitted (`explicitNulls = false`), unknown keys are ignored, and `PullResponse` / `SnapshotResponse` round-trip.

### Implementation

- [X] T019 [P] Create `core/model/src/main/kotlin/{pkg}/core/model/Ids.kt` with `@JvmInline value class WorkerId(val value: String)` and `BlockId(val value: String)`
- [X] T020 [P] Create `core/model/src/main/kotlin/{pkg}/core/model/FieldEvent.kt` with `SyncStatus`, `FailureKind`, `FieldEvent`, `FieldEventDetail` (with `canEdit` / `canRetry`), `FieldEventDraft` (incl. `timestamp`), and `IdGenerator`, exactly as in data-model.md
- [X] T021 [P] Create `core/model/src/main/kotlin/{pkg}/core/model/Validation.kt` with `enum class ValidationError { WorkerIdMissing, BlockIdMissing, QuantityNotANumber, QuantityNotPositive }`, sealed `ValidationResult` (`Valid(workerId: WorkerId, blockId: BlockId, quantity: Int)` / `Invalid(errors)`), and `object FieldEventValidator { fun validate(draft): ValidationResult; fun validateQuantity(raw: String): QuantityResult }`, where `sealed interface QuantityResult { data class Valid(val quantity: Int); data class Invalid(val error: ValidationError) }`
- [X] T022 [P] Create `core/model/src/main/kotlin/{pkg}/core/model/Uuid7.kt`: `class Uuid7(clock: Clock = Clock.systemUTC(), random: SecureRandom = SecureRandom()) : IdGenerator`, per research §R6 (RFC 9562 method 1, 12-bit counter, `@Synchronized`)
- [X] T023 [P] Create `core/model/src/main/kotlin/{pkg}/core/model/Hlc.kt`: `data class Hlc(wallMs, counter, nodeId)` with `encode()`, `companion parse()`, and `fun tick(last: Hlc, nowMs: Long): Hlc`, per research §R7
- [X] T024 [P] Create the DTOs in `core/network/src/main/kotlin/{pkg}/core/network/dto/`: `Enums.kt` (OpType, EntityType), `PendingOpDto.kt`, `FieldEventFields.kt`, `PushDtos.kt` (PushRequest, RejectedOp, PushResponse), `PullDtos.kt` (ChangeDto, PullResponse), `SnapshotResponse.kt`, all field-for-field as in contracts/sync-api.md
- [X] T025 [P] Create `core/network/src/main/kotlin/{pkg}/core/network/SyncApi.kt`, with every method returning `retrofit2.Response<T>` (contracts/sync-api.md)
- [X] T026 Create `core/network/src/main/kotlin/{pkg}/core/network/di/NetworkModule.kt`. It holds the qualifiers `@SyncBaseUrl` (String) and `@SyncInterceptors`, `@Multibinds abstract fun syncInterceptors(): Set<Interceptor>` (so the set may be empty), a `Json { ignoreUnknownKeys = true; explicitNulls = false }` singleton, and OkHttpClient with every `@SyncInterceptors` interceptor added as an application interceptor. It also provides Retrofit (base URL from `@SyncBaseUrl`, kotlinx-serialization converter) and `SyncApi`. Depends on T024, T025.
- [X] T027 [P] Create in `core/sync/src/main/kotlin/{pkg}/core/sync/`: `SyncScheduler.kt` (interface, contracts/repository.md), `trigger/SyncTrigger.kt` (interface), and `SyncConfig.kt` (data class with defaults 50 / 5 / 30 s; `val effectiveBatchSize = batchSize.coerceIn(1, 500)`)
- [X] T028 [P] Create `core/testing/src/main/kotlin/{pkg}/core/testing/MainDispatcherRule.kt` (a `TestWatcher` that sets Main to a `StandardTestDispatcher`) and `fixtures/FieldEventFixtures.kt` (`sampleDraft()` → w_001 / block_42 / "3" / `2025-06-10T08:32:00Z`, plus a `SequentialIdGenerator` fake for `IdGenerator`)
- [X] T029 **GATE 1** (plan Phase 1). Run `./gradlew build test` and show the output (T015–T018 green), the version lookup table from T001, the summary, files touched, open questions, and a Principles I–VI statement. STOP for approval.

**Checkpoint**: foundation ready.

---

## Phase 3: User Story 1 (data layer) - Capture a field event while offline (Priority: P1) 🎯 POC Stage A

**Goal**: record an event with no network. It's stored with a v7 id and PENDING status, plus its CREATE op in the same transaction, and it survives a relaunch. No UI yet: this phase builds the outbox the sync engine consumes. The capture screen and list are in Phase 7.

**Independent Test**: automated only at this stage. Record three events on a file-backed DB, reopen it, and all three are PENDING with the correct data (T036). Automated: T030–T036.

### Tests for User Story 1 (write first) ⚠️

- [X] T030 [P] [US1] Write `core/database/src/test/kotlin/{pkg}/core/database/FieldEventDaoTest.kt`. Cover: insert and read back, `observeEvents(null)` ordered by `id DESC`, `observeEvents(PENDING)` filters, and Turbine emissions on insert.
- [X] T031 [P] [US1] Write `core/database/src/test/kotlin/{pkg}/core/database/PendingOpDaoTest.kt`. Cover: insert assigns increasing `seq`, a duplicate `op_id` violates the unique index, and a second op for the same `(entity_type, entity_id)` violates the unique index.
- [X] T032 [P] [US1] Write `core/sync/src/test/kotlin/{pkg}/core/sync/HlcClockTest.kt`. Cover: `sync_state` row exists with a 16-hex `node_id` after first open, each `tick()` is strictly greater than the previous, `tick()` persists (reopen a file-backed DB and the next tick is still greater), and `node_id` stays unchanged across reopen.
- [X] T033 [P] [US1] Write `core/sync/src/test/kotlin/{pkg}/core/sync/FieldEventCodecTest.kt`. `encode(FieldEvent)` gives exactly `{"workerId","blockId","quantity","timestamp"}` with an ISO-8601 `Z` timestamp, and decode is its inverse.
- [X] T034 [P] [US1] Write `core/data/src/test/kotlin/{pkg}/core/data/OfflineFirstFieldEventRepositoryTest.kt`. Cover: `record(sampleDraft())` returns `Recorded(id)` with a version-7 id, the event is PENDING with trimmed ids and timestamp truncated to ms, exactly one op row exists (`QUEUED`, `attempts = 0`, `op_type = CREATE`, `schema_version = 1`, `fields_json` = codec output, `hlc` parses), and `FakeSyncScheduler.requests` has 1 entry. Also: an invalid draft returns `Invalid` with all errors and writes no rows or scheduler calls. Storage full: on a file-backed DB, run `PRAGMA max_page_count` at the current page count and record until `StorageFull`. That returns `StorageFull`, leaves no partial rows, and triggers no scheduler call.
- [X] T035 [P] [US1] Write `core/data/src/test/kotlin/{pkg}/core/data/RecordAtomicityTest.kt`. Inject an `IdGenerator` that returns an `opId` already present in `pending_op`, so the op insert fails after the entity insert. `record()` then throws or fails, `field_event` has no row for the new id (Principle II), and the scheduler isn't called.
- [X] T036 [P] [US1] Write `core/data/src/test/kotlin/{pkg}/core/data/RelaunchPersistenceTest.kt`. Record 3 events on a file-backed DB, close it, open a new `PicktraceDatabase` + repository on the same file, and check that `observeEvents()` has the same 3 events with identical fields and PENDING (SC-001).

### Implementation for User Story 1

- [X] T037 [P] [US1] Create the entities in `core/database/src/main/kotlin/{pkg}/core/database/entity/`: `FieldEventEntity.kt`, `PendingOpEntity.kt` (with `OpState { QUEUED, IN_FLIGHT, FAILED }` and indices `unique(entity_type, entity_id)`, `unique(op_id)`, `(state, seq)`), `SyncStateEntity.kt`, `SyncRunEntity.kt` (with `RunOutcome` enum). Columns exactly as in data-model.md, with snake_case `@ColumnInfo` names.
- [X] T038 [P] [US1] Create `core/database/src/main/kotlin/{pkg}/core/database/Converters.kt` for the enums ↔ TEXT (`name`). `SyncStatus` / `FailureKind` come from `:core:model`.
- [X] T039 [US1] Create DAOs in `core/database/src/main/kotlin/{pkg}/core/database/dao/`. `FieldEventDao.kt` has `insert`, `getById`, and `observeEvents(status: String?)` (`SELECT … WHERE :status IS NULL OR status = :status ORDER BY id DESC`). `PendingOpDao.kt` has `insert` and `getByEntityId`. `SyncStateDao.kt` has `get()` and `update(entity)`. Depends on T037, T038.
- [X] T040 [US1] Create `core/database/src/main/kotlin/{pkg}/core/database/PicktraceDatabase.kt` (version 1, `exportSchema = true`, all four entities, converters). Its `onCreate` callback inserts `sync_state(id=0, node_id=<16 lowercase hex from SecureRandom>, hlc_wall_ms=0, hlc_counter=0)`. Also create `di/DatabaseModule.kt` (singleton `Room.databaseBuilder(context, …, "picktrace.db")` + DAO providers). Build once, and commit the generated `core/database/schemas/…/1.json`.
- [X] T041 [US1] Create `core/testing/src/main/kotlin/{pkg}/core/testing/TestDatabases.kt` (`inMemory(context)` and `fileBacked(context, name)`, both with the query and transaction executors set to a supplied test dispatcher's executor) and `FakeSyncScheduler.kt` (`val requests: List<Request>` recording `expedited` and `delay`)
- [X] T042 [P] [US1] Create `core/sync/src/main/kotlin/{pkg}/core/sync/HlcClock.kt`. `@Singleton class HlcClock @Inject constructor(dao: SyncStateDao, clock: Clock)` with `suspend fun tick(): Hlc`. KDoc: MUST be called inside the caller's `withTransaction`. It reads the state, calls `Hlc.tick`, writes the state, and returns.
- [X] T043 [P] [US1] Create `core/sync/src/main/kotlin/{pkg}/core/sync/FieldEventCodec.kt`, which maps `FieldEvent` ↔ `FieldEventFields` ↔ `JsonObject` using the injected `Json`, plus `const val FIELD_EVENT_SCHEMA_VERSION = 1`
- [X] T044 [US1] Create `core/sync/src/main/kotlin/{pkg}/core/sync/di/SyncModule.kt`. It provides `SyncConfig()`, `Clock.systemUTC()`, and `IdGenerator` = `Uuid7()` singleton, and binds the **interim** `internal class DeferredSyncScheduler : SyncScheduler` (no-op, KDoc "replaced by WorkManagerSyncScheduler in T070") in `core/sync/src/main/kotlin/{pkg}/core/sync/DeferredSyncScheduler.kt`
- [X] T045 [P] [US1] Create `core/data/src/main/kotlin/{pkg}/core/data/MutationResult.kt` with `RecordResult` (US1 needs only this now; `MutationResult` is added in T089), per contracts/repository.md
- [X] T046 [US1] Create `core/data/src/main/kotlin/{pkg}/core/data/FieldEventRepository.kt`: the interface with `observeEvents(filter)` and `record(draft)` only, and `internal class OfflineFirstFieldEventRepository` (the other methods are added in T090–T092 and T105). `record` validates, then in `db.withTransaction { hlc = hlcClock.tick(); insert event PENDING; insert op QUEUED with codec fields, new opId from IdGenerator }`. It catches `SQLiteFullException` → `StorageFull`, calls `syncScheduler.requestSync()` **after** commit, and maps entities to `FieldEvent` with `flowOn(Dispatchers.Default)` + `distinctUntilChanged()`. Also create `di/DataModule.kt` (`@Binds`). Depends on T039–T045.
- [X] T047 [US1] **GATE 2** (POC Stage A, part 1: US1 data layer). Run `./gradlew build test` and show the output (T030–T036 green, all prior tests still green), then the summary, files touched, open questions, and the Principles statement (I: repository exposes Room Flows; II: T035 evidence). No manual step yet, since there is no UI. STOP for approval.

**Checkpoint**: the outbox exists. Events and their CREATE ops are recorded atomically and survive a reopen.

---

## Phase 4: User Story 2 (engine) - Pending events sync automatically when connectivity returns (Priority: P2) 🎯 POC Stage A

**Goal**: a headless `SyncEngine` delivers the outbox in `seq` order against `FakeSyncServer`. Acked events become SYNCED and rejected ones FAILED. Transport problems leave events PENDING, and a re-delivered event becomes SYNCED. Triggering (WorkManager, connectivity) is Phase 6.

**Independent Test**: record 5 events, run the engine directly against `FakeSyncServer`. All 5 become synced, and `deliveryLog` holds them in recording order, each `opId` acked once. Automated: T048–T053.

### Tests for User Story 2 engine (write first) ⚠️

- [X] T048 [P] [US2] Write `core/testing/src/test/kotlin/{pkg}/core/testing/FakeSyncServerTest.kt`, driving the fake through a real Retrofit `SyncApi` with the interceptor installed. Cover: an ack stores the entity, a repeated `opId` is acked again without a second store, `rejectWhen` puts the op in `rejected` with its reason, >500 ops or bad JSON → 400, each `Fault` behaves as in contracts/fake-sync-server.md and is consumed FIFO, and pull/snapshot follow the contract.
- [X] T049 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/PushOutcomeClassifierTest.kt`, one test per row of the contracts/sync-api.md "Client handling of push results" table. Include `Retry-After` as delta-seconds, as an HTTP-date, and missing/garbage → 30 s.
- [X] T050 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncEngineTest.kt` (real Room + real Retrofit + FakeSyncServer). Cover: acked → event SYNCED and op row gone in the same transaction (assert via a single `observe` emission, never SYNCED with op present), rejected → FAILED with `failure_kind = REJECTED` and the server reason, an empty outbox → `COMPLETED` with zero pushes, and invariants 1–5 from data-model.md hold after the run.
- [X] T051 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncEngineOrderTest.kt`. Record events with timestamps 08:35, 08:30, 08:32 in that recording order. `deliveryLog` must follow recording (`seq`) order, not timestamp (SC-004, clock-skew edge case).
- [X] T052 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncEngineTransportTest.kt`. Cover: `DropConnection` on the 2nd of 3 batches leaves batch 1 SYNCED and batches 2 and 3 PENDING, none FAILED, `attempts` = 1 only for batch 2 ops, and `RunResult.Retry` (US2-5, FR-014). `Status(503)` and `GarbageBody` → same shape. `Status(429, "7")` → `RunResult.RetryAfter(7 s)`. `PartialBody` → omitted ops PENDING with +1 attempt, others resolved (OQ2). `Status(400)` → batch FAILED/REFUSED `"HTTP 400"`, `RunResult.Refused` (OQ3). 5 consecutive transport failures on the same op across 5 runs → FAILED/EXHAUSTED, with a reason that starts with `"Could not reach server after 5 attempts"`.
- [X] T053 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncEngineRelaunchTest.kt`. Use one `FakeSyncServer` instance with the `DropAfterProcessing` fault on a file-backed DB. Run engine #1: events stay PENDING. Then do an "app kill" (leave an op IN_FLIGHT and a `sync_run` RUNNING by cancelling the run coroutine mid-push). Open a new DB and engine on the same file and run: all ops are acked, events are SYNCED, the fake stored each entity once, and the old `sync_run` is `INTERRUPTED` (SC-005, FR-016, duplicate-delivery edge case).

### Implementation for User Story 2 engine

- [X] T054 [P] [US2] Create `core/testing/src/main/kotlin/{pkg}/core/testing/FakeSyncServer.kt` exactly per contracts/fake-sync-server.md: in-memory maps guarded by a lock, `deliveryLog`, `ackedOpIds`, `rejectWhen`, `enqueueFault`, `reset`. It short-circuits `/sync/*` and returns 404 for other paths.
- [X] T055 [US2] Extend `core/database/src/main/kotlin/{pkg}/core/database/dao/PendingOpDao.kt` with `resetInFlight()`, `queuedBatch(limit)` (`WHERE state='QUEUED' ORDER BY seq LIMIT :limit`), `setState(seqs, state)`, `getByOpIds(ids)`, `deleteByOpIds(ids)`, `incrementAttempts(seqs)`, and `markFailed(seq, kind, error)`, plus `FieldEventDao.setStatus(ids, status)`
- [X] T056 [P] [US2] Create `core/database/src/main/kotlin/{pkg}/core/database/dao/SyncRunDao.kt` with `insert`, `update`, `markRunningAsInterrupted(nowMs)`, `trimTo(20)`, and `observeLatest()`, and add it to `PicktraceDatabase` + `DatabaseModule`
- [X] T057 [P] [US2] Create `core/sync/src/main/kotlin/{pkg}/core/sync/PushOutcomeClassifier.kt`: `sealed interface PushOutcome { Delivered(acked, rejected, missing); Transport(error); RateLimited(retryAfter); Refused(code) }` and `fun classify(result: Result<Response<PushResponse>>, sentOpIds, now): PushOutcome`, per contracts/sync-api.md
- [X] T058 [US2] Create `core/sync/src/main/kotlin/{pkg}/core/sync/SyncEngine.kt`, per data-model.md "Transactions". **Run start** (one tx): `resetInFlight`, `markRunningAsInterrupted`, insert a `sync_run(RUNNING)`, `trimTo(20)`. **Loop** (claim tx): `queuedBatch(config.effectiveBatchSize)` → `IN_FLIGHT`, and break if empty. Otherwise map rows to `PendingOpDto` (decode `fields_json` to `JsonObject`), then `runCatching { api.push(...) }` → classify. **Apply tx**: acked → delete ops + events SYNCED; rejected → `markFailed(REJECTED, reason)` + events FAILED; transport (whole batch or `missing`) → `incrementAttempts`, ops at `maxTransportAttempts` → `markFailed(EXHAUSTED, "Could not reach server after N attempts: <error>")` + events FAILED, the rest QUEUED; refused → `markFailed(REFUSED, "HTTP <code>")` + events FAILED. Update the `sync_run` counters in the same tx, and stop the loop on Transport/RateLimited/Refused. **Run end** (tx): outcome + `finished_at_ms`. Return `RunResult`. `CancellationException` is rethrown, never swallowed. Depends on T055–T057.

**Checkpoint**: the engine syncs the outbox correctly when invoked directly.

---

## Phase 5: User Story 3 - Large backlogs sync in batches (Priority: P3) 🎯 POC Stage A

**Goal**: a large backlog goes out in consecutive batches of at most `batchSize`, each event with its own outcome.

**Independent Test**: record 250 events and run the engine. Delivery should be 5 pushes of 50, in order, every event SYNCED. Automated: T059.

### Tests for User Story 3 (write first) ⚠️

- [X] T059 [P] [US3] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncEngineBatchTest.kt`. Cover: 250 events → exactly 5 pushes of 50, each internally in `seq` order, all SYNCED, `sync_run.batches = 5` (US3-1). A batch where `rejectWhen` matches 3 of 50 → 47 SYNCED and 3 FAILED, independently (US3-2). `DropConnection` on batch 3 → batches 3–5 PENDING (US3-3). `SyncConfig(batchSize = 10)` → 25 pushes, and `batchSize = 10_000` → pushes of ≤ 500 (clamp).

### Implementation for User Story 3

- [X] T060 [US3] Verify, and fix if needed, that `SyncEngine` (T058) reads `config.effectiveBatchSize` on every claim and updates the `sync_run.batches` / `acked` / `rejected` counters per batch inside the apply transaction. Also make `SyncModule` provide `SyncConfig` as an overridable binding (a `@Provides` in its own `SyncConfigModule`) in `core/sync/src/main/kotlin/{pkg}/core/sync/di/SyncConfigModule.kt`. Tests build `SyncEngine` with an explicit `SyncConfig` and don't use a Hilt test component, since `hilt-android-testing` isn't approved.
- [X] T061 Stage A checkpoint: `./gradlew build test` green with T048–T053 and T059 (no gate; Stage A and Stage B share GATE 3)

**Checkpoint**: POC Stage A complete. The headless sync service is proven by tests: order, batching, partial reject, transport faults, re-delivery after a kill.

---

## Phase 6: User Story 2 (triggers) - Sync starts on its own (Priority: P2) 🎯 POC Stage B

**Goal**: connectivity starts one sync run at a time via WorkManager unique work and a foreground connectivity callback. Overlapping triggers never deliver an op twice. Replaces the interim `DeferredSyncScheduler`.

**Independent Test**: automated via WorkManager `TestDriver` (T062) and `ShadowConnectivityManager` (T068). On-device demo needs the Phase 7 UI. Automated: T062, T063, T068, T069.

### Tests for User Story 2 triggers (write first) ⚠️

- [X] T062 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/work/SyncSchedulerTest.kt` (Robolectric, `WorkManagerTestInitHelper`, `TestDriver`). Cover: `requestSync()` enqueues unique work `field-event-sync` with `NetworkType.CONNECTED`, which doesn't run until `setAllConstraintsMet`. A second `requestSync()` while the first is RUNNING appends (two WorkInfos in the chain, never two RUNNING at once). With a real engine behind a test `WorkerFactory`, no `opId` appears twice in `deliveryLog` (US2-6, FR-015). `requestSync(delay = 7.seconds)` sets the initial delay.
- [X] T063 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/work/SyncWorkerTest.kt` (`TestListenableWorkerBuilder` + a fake engine). Mappings: `COMPLETED` → `success`, `Retry` → `retry`, `RetryAfter(d)` → `success` + `FakeSyncScheduler` got `delay = d`, `Refused` → `success`. `getForegroundInfo()` returns a notification on the `sync` channel.

### Implementation for User Story 2 triggers

- [X] T064 [US2] Create `core/sync/src/main/kotlin/{pkg}/core/sync/work/SyncWorker.kt`: `@HiltWorker class SyncWorker @AssistedInject constructor(..., engine: SyncEngine, scheduler: SyncScheduler) : CoroutineWorker`, mapping `RunResult` as in T063. It overrides `getForegroundInfo()` (creates a low-importance channel `sync`, notification "Syncing field events", `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29+).
- [X] T065 [US2] Create `core/sync/src/main/kotlin/{pkg}/core/sync/work/WorkManagerSyncScheduler.kt`: `OneTimeWorkRequestBuilder<SyncWorker>`, `Constraints(NetworkType.CONNECTED)`, `setBackoffCriteria(EXPONENTIAL, 30 s)`, `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)` when `expedited`, `setInitialDelay(delay)` when > 0, and `enqueueUniqueWork("field-event-sync", APPEND_OR_REPLACE, …)`
- [X] T066 [US2] Update `core/sync/src/main/AndroidManifest.xml`: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions, `<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="dataSync" tools:node="merge"/>`, and `ACCESS_NETWORK_STATE`
- [X] T067 [P] [US2] Create `core/sync/src/main/kotlin/{pkg}/core/sync/trigger/ForegroundConnectivityTrigger.kt`: `SyncTrigger` whose `start()` registers `registerDefaultNetworkCallback`, with `onAvailable` → `scheduler.requestSync(expedited = true)`. `stop()` unregisters, and start/stop are idempotent.
- [X] T068 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/trigger/ForegroundConnectivityTriggerTest.kt` (Robolectric `ShadowConnectivityManager`). Cover: after `start()`, a network-available callback → one expedited request; after `stop()`, none; a double `start()` registers once; `onAvailable` issues `requestSync(expedited = true, delay = ZERO)` synchronously, with no debounce. SC-002 foreground bound: the rest of the 10 s is WorkManager's expedited dispatch, checked manually in quickstart step 2.
- [X] T069 [P] [US2] Write `core/sync/src/test/kotlin/{pkg}/core/sync/SyncRunLogTest.kt`. A normal run writes RUNNING → COMPLETED with counters, a run left RUNNING becomes INTERRUPTED when the next run starts, and only 20 rows are retained.
- [X] T070 [US2] Update `core/sync/src/main/kotlin/{pkg}/core/sync/di/SyncModule.kt` to bind `WorkManagerSyncScheduler` and provide `ForegroundConnectivityTrigger` as `SyncTrigger`, and **delete** `DeferredSyncScheduler.kt`
- [X] T071 [US2] Update `app/src/main/kotlin/{pkg}/PicktraceApplication.kt` to implement `Configuration.Provider` with the injected `HiltWorkerFactory`. In `app/src/main/AndroidManifest.xml`, remove `androidx.work.WorkManagerInitializer` from `androidx.startup.InitializationProvider` (`tools:node="remove"`).
- [X] T072 [US2] Update `app/src/main/kotlin/{pkg}/MainActivity.kt`: inject `SyncTrigger`, calling `start()` in `onStart` and `stop()` in `onStop`
- [X] T073 [P] [US2] Create `app/src/main/kotlin/{pkg}/di/NetworkConfigModule.kt` (`@Provides @SyncBaseUrl = BuildConfig.SYNC_BASE_URL`), plus `app/src/debug/kotlin/{pkg}/di/FakeServerModule.kt` (`@Provides @IntoSet @SyncInterceptors` a singleton `FakeSyncServer`, with the *fake-only* rule `rejectWhen("Rejected by FakeSyncServer: quantity >= 1000") { quantity >= 1000 }`) and `app/src/release/kotlin/{pkg}/di/FakeServerModule.kt` (an empty `@Module` so both variants compile)
- [X] T074 **GATE 3** (POC Stage A + B: US2 + US3). Run `./gradlew build test` and show the output (T048–T053, T062–T063, T068, T069, T059 green, all prior green). Then the summary, files touched, open questions (restate OQ1–OQ4 from plan.md), and the Principles statement (II: ack + delete in one tx; III: seq order + delete only on ack; I: IN_FLIGHT reset + `sync_run` in Room). No manual step yet (no UI until Phase 7). STOP for approval.

**Checkpoint**: POC complete. Recording and reconnecting syncs automatically, in batches, exactly once.

---

## Phase 7: User Story 1 (UI) - Capture screen and minimal event list (Priority: P1)

**Goal**: put a face on the proven sync service. A worker records events on the capture screen and sees them, with status, in a minimal list.

**Independent Test**: with networking disabled, record three events, kill and relaunch the app. All three are listed as pending with the correct data; enable networking and they turn synced (quickstart manual steps 1 and 2). Automated: T075, T076.

### Tests for User Story 1 UI (write first) ⚠️

- [X] T075 [P] [US1] Write `feature/capture/src/test/kotlin/{pkg}/feature/capture/CaptureViewModelTest.kt` with `MainDispatcherRule`, Turbine, and a fake repository. Cover: field changes update state, save with invalid input sets `errors` and doesn't call record, a successful save clears quantity, keeps the ids, refreshes the timestamp from the fixed `Clock`, and sets `message = Saved`, and `StorageFull` sets that message.
- [X] T076 [P] [US1] Write `feature/capture/src/test/kotlin/{pkg}/feature/capture/CaptureScreenTest.kt` (Robolectric Compose rule). The three fields are shown, the per-field error text appears for invalid input, and Save calls `onSave`.

### Implementation for User Story 1 UI

- [X] T077 [P] Create `core/designsystem/src/main/kotlin/{pkg}/core/designsystem/theme/Color.kt`, `Type.kt`, `Theme.kt` (`PicktraceTheme` with M3 light/dark schemes) and `component/StatusChip.kt` (`StatusChip(label: String, tone: StatusTone)`, where `enum class StatusTone { Neutral, Success, Error }` is local to designsystem, so the module keeps zero internal deps per the CLAUDE.md graph. Feature modules map `SyncStatus` → label + tone. Color is never the only signal, since the label is always shown.)
- [X] T078 [P] [US1] Create `feature/capture/src/main/kotlin/{pkg}/feature/capture/CaptureUiState.kt` (per contracts/ui.md, with `sealed interface UserMessage { Saved; StorageFull }`) and `CaptureViewModel.kt` (`@HiltViewModel`, injects repository + `Clock`, one `MutableStateFlow` exposed as `StateFlow`, `viewModelScope` only)
- [X] T079 [US1] Create `feature/capture/src/main/kotlin/{pkg}/feature/capture/CaptureScreen.kt` (stateless `CaptureScreen(state, callbacks)` + `CaptureRoute` composable with `hiltViewModel()`; the quantity field uses the number keyboard, errors show as supportingText, Snackbar for messages, the timestamp is shown read-only) and `navigation/CaptureNavigation.kt` (`@Serializable object CaptureRoute`, `NavGraphBuilder.captureScreen(onBack)`, `NavController.navigateToCapture()`). Depends on T078.
- [X] T080 [US1] Create a minimal list in `feature/events/src/main/kotlin/{pkg}/feature/events/list/`: `EventListUiState.kt` (`events`, `loading`; `filter` and `sync` are added in US4/US5), `EventListViewModel.kt` (`observeEvents(null)` → `EventRow`s, `stateIn(WhileSubscribed(5_000))`), and `EventListScreen.kt` (LazyColumn rows with worker, block, quantity, local-formatted timestamp, `StatusChip` via a `SyncStatus` → label/tone mapper in `feature/events/src/main/kotlin/{pkg}/feature/events/StatusUi.kt`, and a FAB → capture). Also create `feature/events/src/main/kotlin/{pkg}/feature/events/navigation/EventsNavigation.kt` (`@Serializable object EventListRoute`, `NavGraphBuilder.eventListScreen(onAdd)`).
- [X] T081 [US1] Create `app/src/main/kotlin/{pkg}/navigation/PicktraceNavHost.kt` (start `EventListRoute`, with the capture and list destinations), and update `app/src/main/kotlin/{pkg}/MainActivity.kt` to `setContent { PicktraceTheme { PicktraceNavHost() } }`, keeping T072's `SyncTrigger` start/stop
- [X] T082 [US1] Checkpoint: `./gradlew build test` green with T075, T076 (no gate; Phase 7 and US4 share GATE 4)

**Checkpoint**: US1 works end to end as an offline logbook that syncs itself.

---

## Phase 8: User Story 4 - Review and manage recorded events (Priority: P4)

**Goal**: filter the list, see detail, edit the quantity of a not-yet-synced event, and delete one. Synced and in-flight events are refused and left unchanged.

**Independent Test**: record several events, filter to pending, edit one quantity, delete another. The list should reflect it and the edited value should be what's sent. A synced event can't be edited or deleted. Automated: T083–T087.

### Tests for User Story 4 (write first) ⚠️

- [X] T083 [P] [US4] Write `core/data/src/test/kotlin/{pkg}/core/data/EditDeleteTest.kt`. `updateQuantity` on PENDING → `Success`: the event's quantity changes, status stays PENDING, and the op keeps its `op_id` and `seq`, with `fields_json.quantity` updated and a larger `hlc`. After a sync against FakeSyncServer, `deliveryLog` shows the new quantity (US4-3, FR-008). `updateQuantity` on FAILED → `Success` and it stays FAILED (FR-009b). `delete` on PENDING → both rows gone, and a following sync pushes nothing for it (US4-4, FR-009). An invalid quantity → `Invalid`, unchanged. An unknown id → `NotFound`.
- [X] T084 [P] [US4] Write `core/data/src/test/kotlin/{pkg}/core/data/ReadOnlyRulesTest.kt`. For a SYNCED event: `updateQuantity` and `delete` → `ReadOnlySynced`. For an op set to IN_FLIGHT: both → `InFlight`. In every refused case, a full snapshot of the `field_event` + `pending_op` rows before and after is equal (US4-5, FR-009a).
- [X] T085 [P] [US4] Write `feature/events/src/test/kotlin/{pkg}/feature/events/list/EventListViewModelTest.kt`: newest first, `onFilterChange(PENDING|SYNCED|FAILED|null)` shows only matching rows (US4-1, US4-2)
- [X] T086 [P] [US4] Write `feature/events/src/test/kotlin/{pkg}/feature/events/detail/EventDetailViewModelTest.kt` (`SavedStateHandle` with the route id). Cover: the detail loads, edit mode start/cancel/save, a `ReadOnlySynced` result → `message = ReadOnlySynced` with no state corruption, a delete `Success` → `deleted = true`, and the event disappearing → `deleted = true`.
- [X] T087 [P] [US4] Write `feature/events/src/test/kotlin/{pkg}/feature/events/detail/EventDetailScreenTest.kt` (Robolectric Compose), checking the action-enablement table in contracts/ui.md for PENDING / in-flight / SYNCED. Delete shows a confirmation `AlertDialog`, and the "Synced events are read-only" message is displayed.

### Implementation for User Story 4

- [X] T088 [US4] Add to `core/database/src/main/kotlin/{pkg}/core/database/dao/FieldEventDao.kt`: `observeDetail(id)` (LEFT JOIN `pending_op` into a `FieldEventWithOp` POJO in `core/database/src/main/kotlin/{pkg}/core/database/model/FieldEventWithOp.kt`), `updateQuantity(id, q)`, and `deleteById(id)`. Add to `PendingOpDao.kt`: `rewriteFields(seq, fieldsJson, hlc)` and `deleteByEntityId(id)`.
- [X] T089 [P] [US4] Extend `core/data/src/main/kotlin/{pkg}/core/data/MutationResult.kt` with the full `MutationResult` from contracts/repository.md
- [X] T090 [US4] Add `observeEvent(id): Flow<FieldEventDetail?>` to `FieldEventRepository` + impl in `core/data/src/main/kotlin/{pkg}/core/data/FieldEventRepository.kt`, mapping `FieldEventWithOp` → `FieldEventDetail` (`inFlight = op?.state == IN_FLIGHT`, `attempts = op?.attempts ?: 0`)
- [X] T091 [US4] Add `updateQuantity(id, quantity: String)` in the same file. It validates the quantity, then inside **one** `withTransaction` it loads the event + op, applies the guards in the order NotFound → ReadOnlySynced → InFlight, updates the entity, re-encodes the fields with the codec, ticks the HLC, and calls `rewriteFields` (same `op_id`/`seq`). It catches `SQLiteFullException` → `StorageFull`. Add a KDoc pointing at plan.md Open Question 1.
- [X] T092 [US4] Add `delete(id)` to `FieldEventRepository` + impl in `core/data/src/main/kotlin/{pkg}/core/data/FieldEventRepository.kt`. It uses the same guards in one transaction, then `deleteByEntityId` + `deleteById`.
- [X] T093 [US4] Update the list in `feature/events/src/main/kotlin/{pkg}/feature/events/list/`: add `filter` to `EventListUiState`, `onFilterChange` in `EventListViewModel` (`flatMapLatest` over the filter), and a filter chip row (All/Pending/Synced/Failed) + row click → detail in `EventListScreen`
- [X] T094 [P] [US4] Create `feature/events/src/main/kotlin/{pkg}/feature/events/detail/EventDetailUiState.kt` (per contracts/ui.md, `UserMessage` sealed with ReadOnlySynced / InFlight / Deleted / StorageFull) and `EventDetailViewModel.kt` (`@HiltViewModel`, `savedStateHandle.toRoute<EventDetailRoute>()`)
- [X] T095 [US4] Create `feature/events/src/main/kotlin/{pkg}/feature/events/detail/EventDetailScreen.kt`. It shows the fields, a `StatusChip`, and attempts / last error when present. Edit quantity is inline, Delete has a confirm `AlertDialog`, actions are enabled per the contracts/ui.md table, and there's a Snackbar for messages. Put string resources in `feature/events/src/main/res/values/strings.xml`, including `"Synced events are read-only"` and `"Syncing now, try again when it finishes"`. Depends on T094.
- [X] T096 [US4] Add `@Serializable data class EventDetailRoute(val id: String)` + `NavGraphBuilder.eventDetailScreen(onBack)` + `NavController.navigateToEventDetail(id)` in `feature/events/src/main/kotlin/{pkg}/feature/events/navigation/EventsNavigation.kt`, and wire it in `app/src/main/kotlin/{pkg}/navigation/PicktraceNavHost.kt`
- [ ] T097 [US4] **GATE 4** (Phase 7 US1 UI + Phase 8 US4). Run `./gradlew build test` and show the output (T075, T076, T083–T087 green, all prior green), then the summary, files touched, open questions, and the Principles statement (I: UI reads Room Flows only; IV: T084 evidence). Optionally do manual steps 1 and 2 from quickstart.md. STOP for approval.

**Checkpoint**: events can be reviewed and managed. Read-only rules are enforced.

---

## Phase 9: User Story 5 - Failed events are visible and can be retried (Priority: P5)

**Goal**: failed events show their reason and attempts. A manual retry resubmits with a new `opId` in the original queue position. Sync activity is visible.

**Independent Test**: make the fake reject one event. It should show as failed with a reason. Fix it and retry, and it should become synced. Automated: T098–T102.

### Tests for User Story 5 (write first) ⚠️

- [ ] T098 [P] [US5] Write `core/data/src/test/kotlin/{pkg}/core/data/RetryTest.kt`. `retry` on FAILED/REJECTED → `Success`: the event is PENDING, and the op has a **new** `op_id`, `attempts = 0`, `failure_kind`/`last_error` null, the same `seq`, a larger `hlc`, and `requestSync` called once (US5-2, FR-018). After retry + sync, `deliveryLog` has both opIds and the event is SYNCED. `retry` on PENDING or SYNCED → `NotFailed`. `retry` on FAILED/EXHAUSTED works the same way.
- [ ] T099 [P] [US5] Write `core/sync/src/test/kotlin/{pkg}/core/sync/NoAutoRetryTest.kt`. A FAILED op (REJECTED or EXHAUSTED) isn't claimed by later runs, and `deliveryLog` doesn't grow for it (US5-4, FR-019). A PENDING op with 2 attempts gets a 3rd attempt after one more transport failure (US5-3).
- [ ] T100 [P] [US5] Write `core/data/src/test/kotlin/{pkg}/core/data/SyncActivityRepositoryTest.kt`: null before any run, and maps `sync_run` rows to `SyncRunSummary` for every `RunOutcome`
- [ ] T101 [P] [US5] Extend `feature/events/src/test/kotlin/{pkg}/feature/events/detail/EventDetailScreenTest.kt`: a FAILED detail shows the failure reason, attempts, and kind label, the Retry button is enabled only for FAILED, and a click calls `onRetry` (US5-1)
- [ ] T102 [P] [US5] Write `core/designsystem/src/test/kotlin/{pkg}/core/designsystem/component/SyncBannerTest.kt` (Robolectric Compose; apply `picktrace.android.test` in `core/designsystem/build.gradle.kts`). It checks the text for RUNNING ("Syncing… n sent"), TRANSPORT_ERROR ("Last sync failed: <error>"), INTERRUPTED ("Sync interrupted"), and COMPLETED with nothing shown (FR-020).

### Implementation for User Story 5

- [ ] T103 [US5] Add to `core/database/src/main/kotlin/{pkg}/core/database/dao/PendingOpDao.kt`: `requeueWithNewOpId(seq, newOpId, hlc)` (sets `state = QUEUED`, `attempts = 0`, and clears failure fields)
- [ ] T104 [P] [US5] Create `core/data/src/main/kotlin/{pkg}/core/data/SyncActivityRepository.kt` (interface + impl over `SyncRunDao.observeLatest()`, with `SyncRunSummary` and `RunState` per contracts/repository.md) and bind it in `core/data/src/main/kotlin/{pkg}/core/data/di/DataModule.kt`
- [ ] T105 [US5] Add `retry(id)` to `FieldEventRepository` + impl in `core/data/src/main/kotlin/{pkg}/core/data/FieldEventRepository.kt`. In one transaction: guard for NotFound → NotFailed, tick the HLC, `requeueWithNewOpId(seq, idGenerator.next(), hlc)`, and set the event to PENDING. After commit, call `requestSync(expedited = true)`.
- [ ] T106 [P] [US5] Create `core/designsystem/src/main/kotlin/{pkg}/core/designsystem/component/SyncBanner.kt`: a stateless `SyncBanner(state: BannerState?)`, where `BannerState` is a designsystem-local sealed type, so designsystem doesn't depend on `:core:data`
- [ ] T107 [US5] Update `feature/events/src/main/kotlin/{pkg}/feature/events/list/EventListViewModel.kt` + `EventListUiState.kt` + `EventListScreen.kt` to `combine` the events Flow with `SyncActivityRepository.observeLatestRun()`, map it to `BannerState`, and show `SyncBanner` above the list
- [ ] T108 [US5] Update `feature/events/src/main/kotlin/{pkg}/feature/events/detail/EventDetailViewModel.kt`, `EventDetailUiState.kt`, and `EventDetailScreen.kt`: a failure section (reason, attempts, kind label strings in `strings.xml`), a Retry button → `onRetry`, and a `Retried` message
- [ ] T109 [US5] Checkpoint: `./gradlew build test` green (GATE 5 comes after Polish, per plan Phase 5)

**Checkpoint**: all five stories work independently.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T110 [P] Write `core/sync/src/test/kotlin/{pkg}/core/sync/BacklogTest.kt` (SC-003, SC-007, very-large-backlog edge case). Seed 5,000 events and sync them against FakeSyncServer. Check that every push has ≤ 50 ops, all events are SYNCED, and each `opId` is acked exactly once. Also assert via a Room `QueryCallback` that no `pending_op` SELECT executes without `LIMIT`. Separately, run `EventListViewModel` over 5,000 rows under a `StandardTestDispatcher` for Main and assert that the mapping runs on `Dispatchers.Default`, not Main, with an injected dispatcher. If this fails, apply the windowed-query fallback from research §R12.
- [ ] T111 [P] Write `core/data/src/test/kotlin/{pkg}/core/data/PossiblyDeliveredEditTest.kt`, which pins the current OQ1 behavior. Use `DropAfterProcessing`, then edit the quantity, then sync. The event ends SYNCED while the fake still holds the old quantity. Add a comment that it documents the known divergence and must change when the backend answers OQ1.
- [ ] T112 [P] Check that no `GlobalScope`, `runBlocking`, or `kapt` appears in production code: `grep -rnE "GlobalScope|runBlocking|kapt" --include=*.kt --include=*.kts app core feature build-logic` returns nothing outside `src/test`. Record the output for the gate.
- [ ] T113 **Optional (not a gate requirement):** run the manual validation in quickstart.md (steps 1–5) on an emulator if one is available, and record the results and any deviations for the gate
- [ ] T114 Update `CLAUDE.md` "Open decisions" with OQ1–OQ4 from plan.md, and add a short "How to run" section pointing to quickstart.md
- [ ] T115 **GATE 5** (plan Phase 5). Run `./gradlew build test` and show the output (every test green), the T112 grep output, and the T113 results if T113 was run (otherwise state that it was skipped). Then the summary, files touched, open questions, and a Principles I–VI compliance statement. STOP for approval.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. T003 → T004–T010 → T011 → T012 → T013 → T014. Setup still creates every module (including the empty feature modules) so the graph compiles from day one.
- **Foundational (Phase 2)**: depends on Setup and blocks every story. T026 depends on T024–T025.
- **US1 data layer (Phase 3)**: depends on Foundational.
- **US2 engine (Phase 4)**: depends on Phase 3 (database, repository `record`, `HlcClock`: T037–T046).
- **US3 (Phase 5)**: depends on US2's `SyncEngine` (T058).
- **US2 triggers (Phase 6)**: depends on T058. It replaces the interim scheduler (T070 removes T044's `DeferredSyncScheduler`).
- **US1 UI (Phase 7)**: depends on Phase 3 (repository) and T072 (`MainActivity` already starts the `SyncTrigger`; T081 keeps that).
- **US4 (Phase 8)**: depends on Phase 7's list (T080) and on US2's engine for the SYNCED/IN_FLIGHT fixtures and "after sync" asserts in T083/T084.
- **US5 (Phase 9)**: depends on US2 (failures only come from sync) and on US4's detail screen (T094–T096).
- **Polish (Phase 10)**: depends on all stories.

### POC stages

- **Stage A, headless sync service**: Phases 1–5. Proven by JVM tests only (T030–T036, T048–T053, T059).
- **Stage B, real triggers**: Phase 6. WorkManager + connectivity, proven by T062, T063, T068, T069.
- **POC done** = GATE 3. Everything after it (UI, edit/delete, retry, polish) builds on a proven sync core.

### Gates (constitution approval stops)

GATE 1 = T029 · GATE 2 = T047 (US1 data layer) · GATE 3 = T074 (POC: US2 engine + US3 + US2 triggers) · GATE 4 = T097 (US1 UI + US4) · GATE 5 = T115 (US5 + Polish).
No task after a gate starts before the user approves.

### Within Each User Story

Tests first, and they must fail. Then entities/DAOs → repository → engine/worker → ViewModel → screen → navigation wiring → gate.

### Parallel Opportunities

- Setup: T004–T010 (seven convention plugins, separate files).
- Foundational: all four tests T015–T018, then T019–T025 and T027, T028 (separate files; T026 waits for T024/T025).
- US1 data layer: tests T030–T036 together, then T037/T038, T042/T043, and T045 in parallel.
- US2 engine: tests T048–T053, then T054, T056, T057.
- US2 triggers: tests T062, T063, then T067, T068, T069, T073.
- US1 UI: tests T075, T076 together, then T077 ∥ T078.
- US4: tests T083–T087 together, T089 ∥ T094.
- US5: tests T098–T102, T104 ∥ T106.
- Polish: T110–T112.

---

## Parallel Example: User Story 1 (data layer)

```bash
# All US1 data-layer tests together (they fail until implementation lands):
Task: "FieldEventDaoTest in core/database/src/test/kotlin/{pkg}/core/database/FieldEventDaoTest.kt"
Task: "PendingOpDaoTest in core/database/src/test/kotlin/{pkg}/core/database/PendingOpDaoTest.kt"
Task: "HlcClockTest in core/sync/src/test/kotlin/{pkg}/core/sync/HlcClockTest.kt"
Task: "FieldEventCodecTest in core/sync/src/test/kotlin/{pkg}/core/sync/FieldEventCodecTest.kt"
Task: "OfflineFirstFieldEventRepositoryTest in core/data/src/test/kotlin/{pkg}/core/data/OfflineFirstFieldEventRepositoryTest.kt"

# Independent implementation files:
Task: "Entities in core/database/src/main/kotlin/{pkg}/core/database/entity/"
Task: "HlcClock in core/sync/src/main/kotlin/{pkg}/core/sync/HlcClock.kt"
Task: "FieldEventCodec in core/sync/src/main/kotlin/{pkg}/core/sync/FieldEventCodec.kt"
Task: "MutationResult in core/data/src/main/kotlin/{pkg}/core/data/MutationResult.kt"
```

## Parallel Example: User Story 2 (engine)

```bash
Task: "FakeSyncServer in core/testing/src/main/kotlin/{pkg}/core/testing/FakeSyncServer.kt"
Task: "SyncRunDao in core/database/src/main/kotlin/{pkg}/core/database/dao/SyncRunDao.kt"
Task: "PushOutcomeClassifier in core/sync/src/main/kotlin/{pkg}/core/sync/PushOutcomeClassifier.kt"
```

---

## Implementation Strategy

### POC First (sync service before UI)

1. Phase 1 Setup, then Phase 2 Foundational, then GATE 1.
2. Phase 3 US1 data layer, then GATE 2: atomic, durable outbox, no UI.
3. Phases 4–6, then GATE 3: headless engine (Stage A), batching, then WorkManager + connectivity
   triggers (Stage B). **This is the POC.** Stop and validate with the engine and scheduler tests.

### Incremental Delivery (after the POC)

1. Phase 7 US1 UI + Phase 8 US4, then GATE 4: capture screen, list with filter, detail, edit and
   delete with read-only enforcement. Manual quickstart steps 1 and 2 become possible here.
2. Phase 9 US5 + Phase 10 Polish, then GATE 5: failure visibility, manual retry, sync banner, backlog proof.

Each gate leaves the project building with every earlier test still green (Principle VI).

---

## Notes

- A `[P]` task touches a file no other incomplete task touches.
- Never use kapt, `GlobalScope`, or `runBlocking` in production code. Ask before any library outside CLAUDE.md.
- No endpoint, field, or status code beyond contracts/sync-api.md. If one seems necessary, stop and ask (Principle V).
- Commit only when the user asks.
