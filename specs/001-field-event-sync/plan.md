# Implementation Plan: Offline Field Event Capture & Sync

**Branch**: `001-field-event-sync` (git is currently on `main`, with no commits yet) | **Date**: 2026-09-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-field-event-sync/spec.md`

## Summary

Workers record `FieldEvent`s (worker, block, quantity, UTC timestamp) on an Android device with no
network. Each event is written to Room together with a `PendingOp` outbox row in one transaction,
so it survives a process kill. When connectivity appears, a single WorkManager run pushes the
outbox in `seq` order, in batches of 50, to `POST /sync/push`. Acked ops are deleted and their events
marked `synced` in one transaction. Rejected ops mark events `failed` for good. Transport failures
leave events `pending` and count one attempt; after 5 attempts the event becomes `failed`. Users can
list, filter, inspect, edit (quantity), delete, and manually retry events that are not yet synced.
Until the backend exists, everything runs against `FakeSyncServer`, an OkHttp interceptor.

Mapping of the requester's seven original requirements to this design:

| # | Requirement | Where it lands |
|---|-------------|----------------|
| 1 | FIFO queue for events | `pending_op` table, drained by `seq ASC` (see [data-model.md](data-model.md#pending_op)) |
| 2 | Database for field events | Room `PicktraceDatabase`, table `field_event` |
| 3 | CRUD in the repository | `FieldEventRepository` ([contracts/repository.md](contracts/repository.md)) |
| 4 | Repository pattern, sync on connectivity | Repository writes + `SyncScheduler.requestSync()`; `SyncEngine` in `:core:sync` |
| 5 | `FieldEvent` class | `:core:model` `FieldEvent`, `WorkerId`, `BlockId` |
| 6 | Status enum | `:core:model` `SyncStatus { PENDING, SYNCED, FAILED }` |
| 7 | Sync all pending on connectivity, batched | `SyncWorker` + `SyncEngine`, batch size 50 (configurable, clamped to 500) |

## Technical Context

**Language/Version**: Kotlin 2.x (exact latest stable looked up in implementation Phase 1), JVM target 17

**Primary Dependencies**: Jetpack Compose (Material 3, via BOM), Navigation Compose (type-safe routes), Hilt + KSP, Room (KSP, Gradle plugin for schema export), Retrofit + OkHttp + kotlinx.serialization converter, WorkManager + `androidx.hilt:hilt-work`. See [research.md §R1](research.md#r1-library-set-and-version-policy) for the approved list and version policy.

**Storage**: Room (SQLite), one database `picktrace.db`, schema v1 exported to `core/database/schemas/`

**Testing**: JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric (Room in-memory and file-backed, WorkManager `WorkManagerTestInitHelper`, Compose UI tests), all on the JVM via `./gradlew test`

**Target Platform**: Android, minSdk 26, targetSdk/compileSdk latest stable

**Project Type**: Mobile app (single Android app, multi-module Gradle build)

**Performance Goals**: sync run starts ≤10 s after connectivity with pending events while in the foreground; background is best effort (SC-002); 1,000 events delivered in ≤2 min (SC-003); no UI freeze >1 s while syncing 5,000 events (SC-007)

**Constraints**: offline-first; no sync state in memory; one run at a time; never load the whole outbox into memory; no `GlobalScope`/`runBlocking` in production code

**Scale/Scope**: one entity type, 3 screens (capture, list, detail/edit), up to ~5,000 local events after a multi-day outage

No NEEDS CLARIFICATION remains. The two library questions (navigation, JVM test runtime) were
answered before this plan was written. The open questions under
[Open Questions](#open-questions) are for the backend. They don't block design, because each one has
a documented interim behavior.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design (below).*

| Principle | How this plan complies | Status |
|-----------|------------------------|--------|
| I. Room is the single source of truth | Screens observe `Flow`s from Room DAOs through repositories. Sync progress (FR-020) goes to a Room table `sync_run`, not to an in-memory bus. In-flight marking, attempts, failure reasons, the HLC state, and the node id all live in Room. Scheduling lives in WorkManager. | PASS |
| II. Atomic local writes | Each of these runs in one `db.withTransaction {}`: record, edit, delete, retry, claiming a batch, and applying a push outcome (ack = delete op + mark synced). The pulled-page + cursor pair is N/A until the pull phase. | PASS |
| III. Client-owned identity and ordering | `id`/`opId` come from the hand-rolled `Uuid7`. Every op carries `schemaVersion = 1` and an HLC string. A CREATE's fields are all of its changed fields. Delivery goes by `pending_op.seq` ASC. The outbox row is deleted only on ack. The engine has a push-then-pull shape; pull is a no-op until its phase. | PASS |
| IV. Never clobber pending work | Synced events are read-only (repository refuses with `ReadOnlySynced`). In-flight events are refused with `InFlight` (FR-009a). Rebase is N/A until pull exists. | PASS |
| V. Ask, don't invent | Navigation Compose and Robolectric were approved and mirrored into `CLAUDE.md`. No endpoint or field was added. Behaviors the contract doesn't define are listed as open questions, each with a conservative interim client behavior. | PASS |
| VI. Test-backed phases | Five implementation phases, each ending on `./gradlew build test` output + summary + STOP. See [Implementation Phases](#implementation-phases). | PASS |

**Post-design re-check (after data-model, contracts, quickstart)**: PASS. No principle is bent.
Two design points deserve a reviewer's eye, but neither is a violation:
- `pending_op.state = IN_FLIGHT` is persisted sync state. It is reset at the start of every run, so a
  process kill mid-run can't strand an event as uneditable (Principle I, spec FR-009a).
- The edit-in-place of a CREATE op keeps its `opId`. That follows the `CLAUDE.md` decision, but it can
  diverge from the server if an earlier attempt of that op was delivered and its response was lost.
  Raised as Open Question 1.

## Project Structure

### Documentation (this feature)

```text
specs/001-field-event-sync/
├── plan.md              # This file
├── research.md          # Phase 0: decisions + rationale
├── data-model.md        # Phase 1: domain + Room schema + state machine
├── quickstart.md        # Phase 1: how to build, test and manually validate each story
├── contracts/
│   ├── sync-api.md          # HTTP contract as consumed by the client + response handling table
│   ├── repository.md        # :core:data / :core:sync public Kotlin interfaces
│   ├── fake-sync-server.md  # FakeSyncServer behavior and test controls
│   └── ui.md                # Screens, routes, UiState and user actions
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks), not created here
```

### Source Code (repository root)

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
build-logic/convention/src/main/kotlin/
├── AndroidApplicationConventionPlugin.kt
├── AndroidLibraryConventionPlugin.kt
├── AndroidFeatureConventionPlugin.kt      # library + compose + hilt + designsystem/data/model deps
├── AndroidComposeConventionPlugin.kt
├── AndroidRoomConventionPlugin.kt
├── HiltConventionPlugin.kt                # KSP only
├── JvmLibraryConventionPlugin.kt          # :core:model
└── AndroidTestConventionPlugin.kt         # Robolectric + JUnit + Turbine defaults

app/src/main/kotlin/com/jcgrdev/picktracechallenge/
├── PicktraceApplication.kt        # @HiltAndroidApp, Configuration.Provider (HiltWorkerFactory)
├── MainActivity.kt                # NavHost; registers ForegroundConnectivityTrigger on start/stop
├── navigation/PicktraceNavHost.kt # routes: Capture, EventList, EventDetail(id)
└── di/NetworkConfigModule.kt      # base URL from BuildConfig; installs FakeSyncServer in debug
app/src/debug/kotlin/.../di/FakeServerModule.kt
app/src/release/kotlin/.../di/FakeServerModule.kt   # binds no interceptor

core/model/src/main/kotlin/com/jcgrdev/picktracechallenge/core/model/
├── FieldEvent.kt   # FieldEvent, FieldEventDetail, FieldEventDraft, SyncStatus, FailureKind
├── Ids.kt          # WorkerId, BlockId value classes
├── Validation.kt   # FieldEventValidator + ValidationError
├── Uuid7.kt        # RFC 9562 UUIDv7 generator
└── Hlc.kt          # HLC value type, encode/parse, tick logic (pure)
core/model/src/test/kotlin/...     # Uuid7Test, HlcTest, FieldEventValidatorTest

core/database/src/main/kotlin/.../core/database/
├── PicktraceDatabase.kt
├── entity/ FieldEventEntity.kt, PendingOpEntity.kt, SyncStateEntity.kt, SyncRunEntity.kt
├── dao/    FieldEventDao.kt, PendingOpDao.kt, SyncStateDao.kt, SyncRunDao.kt
├── Converters.kt
└── di/DatabaseModule.kt
core/database/schemas/             # exported Room schema JSON (v1)
core/database/src/test/kotlin/...  # DAO tests (Robolectric, in-memory)

core/network/src/main/kotlin/.../core/network/
├── SyncApi.kt
├── dto/ PendingOpDto.kt, FieldEventFields.kt, PushDtos.kt, PullDtos.kt, SnapshotResponse.kt, Enums.kt
└── di/NetworkModule.kt            # OkHttp (with optional interceptor multibinding), Retrofit, Json
core/network/src/test/kotlin/...   # DTO round-trip serialization tests

core/sync/src/main/kotlin/.../core/sync/
├── SyncEngine.kt                  # claim batch -> push -> apply outcome, loop
├── PushOutcomeClassifier.kt       # HTTP/IO result -> Acked/Rejected/Transport/Refused/RateLimited
├── FieldEventCodec.kt             # FieldEvent <-> JsonObject (FieldEventFields)
├── HlcClock.kt                    # Room-backed HLC tick inside the caller's transaction
├── SyncConfig.kt                  # batchSize=50 (<=500), maxTransportAttempts=5
├── work/SyncWorker.kt             # @HiltWorker CoroutineWorker, getForegroundInfo for expedited
├── work/WorkManagerSyncScheduler.kt   # SyncScheduler impl, unique work, APPEND_OR_REPLACE
├── trigger/SyncTrigger.kt         # seam (connectivity now, FCM later)
├── trigger/ForegroundConnectivityTrigger.kt
└── di/SyncModule.kt
core/sync/src/test/kotlin/...      # engine tests vs FakeSyncServer, worker tests with TestDriver

core/data/src/main/kotlin/.../core/data/
├── FieldEventRepository.kt        # interface + OfflineFirstFieldEventRepository
├── SyncActivityRepository.kt      # observes sync_run
├── MutationResult.kt
└── di/DataModule.kt
core/data/src/test/kotlin/...      # repository tests: atomicity, read-only rules, storage full

core/designsystem/src/main/kotlin/.../core/designsystem/
├── theme/ (Theme.kt, Color.kt, Type.kt)
└── component/ StatusChip.kt, SyncBanner.kt

core/testing/src/main/kotlin/.../core/testing/
├── FakeSyncServer.kt              # OkHttp Interceptor implementing the contract in memory
├── MainDispatcherRule.kt
├── TestDatabases.kt               # in-memory and file-backed builders (for "relaunch" tests)
└── fixtures/FieldEventFixtures.kt

feature/capture/src/main/kotlin/.../feature/capture/
├── CaptureScreen.kt, CaptureViewModel.kt, CaptureUiState.kt, navigation/CaptureRoute.kt
feature/capture/src/test/kotlin/...  # ViewModel (Turbine) + Compose UI tests (Robolectric)

feature/events/src/main/kotlin/.../feature/events/
├── list/  EventListScreen.kt, EventListViewModel.kt, EventListUiState.kt
├── detail/ EventDetailScreen.kt, EventDetailViewModel.kt, EventDetailUiState.kt
└── navigation/EventsRoutes.kt
feature/events/src/test/kotlin/...
```

**Structure Decision**: a multi-module Android build following the module graph in `CLAUDE.md`
unchanged, with sources under `src/main/kotlin`. Tests stay module-local in `src/test` and run on
Robolectric. There's no `androidTest` source set in this feature.

## Implementation Phases

Each phase ends with the constitution's gate: `./gradlew build test` green with output shown,
summary, files touched, open questions, a Principles I–VI compliance statement, then STOP.

**Ordering: POC first (2026-09-25).** The sync service is proven before any UI is built. Phases 2–3
are the proof of concept: a headless outbox, then the engine (Stage A), then its real triggers
(Stage B). The capture screen and list move to Phase 4. Five gates, same as before; they map to
`tasks.md` as GATE 1 = T029, GATE 2 = T047, GATE 3 = T074, GATE 4 = T097, GATE 5 = T115.

| Phase | Scope | Stories | Exit evidence |
|-------|-------|---------|---------------|
| 1 | Version lookup + compatibility check; `build-logic`, version catalog, all modules compiling; `:core:model` (value classes, `FieldEvent`, `SyncStatus`, validator, `Uuid7`, `Hlc`); `:core:network` DTOs + `SyncApi` (the US1 codec needs `FieldEventFields`); `:core:sync` interfaces | foundation | `Uuid7Test` (version/variant bits, monotonic across 10k ids in the same ms), `HlcTest`, `FieldEventValidatorTest`, DTO round-trip test |
| 2 | **POC, outbox.** `:core:database` schema v1 + DAOs; `:core:sync` `HlcClock` + codec; `:core:data` record/observe. No UI | US1 (data layer) | DAO tests; "record, then reopen DB from the same file" survives; atomic record (entity + op, or neither); validation rejects |
| 3 | **POC, sync.** Stage A: `:core:testing` `FakeSyncServer`; `:core:sync` engine + outcome classifier; `sync_run` log; batching. Stage B: worker, scheduler, foreground trigger; debug wiring | US2, US3 | engine tests: order, batching (250 → 5 batches), partial reject, drop mid-batch, garbage body, 429, 5xx, re-delivery after kill ⇒ synced; scheduler tests: overlapping triggers ⇒ no double delivery |
| 4 | `:core:designsystem` theme; `:feature:capture`; minimal list in `:feature:events`; app shell + NavHost. Then list filter, detail, edit quantity, delete; read-only and in-flight refusals | US1 (UI), US4 | capture ViewModel + UI tests; repository + ViewModel + UI tests for every US4 scenario and FR-009a/b |
| 5 | Failure reason/attempts display, manual retry, exhaustion after 5, `SyncBanner` from `sync_run`; 5,000-event backlog check | US5, FR-020, SC-007 | retry issues a new `opId` and keeps `seq`; exhaustion test; backlog test asserts batches ≤50 and no full-table load |

## Open Questions

For the backend owner. None blocks implementation; the interim behavior is what gets built.

1. **Possibly-delivered ops.** A push can reach the server while its response is lost. The op stays
   `pending`. If the user then edits the quantity, the same `opId` goes out with new fields, and an
   idempotent server returns `acked` without applying them. The device would show `synced` with a
   value the server never saw. The same risk applies to deleting such an event locally, and to a
   manual retry (new `opId`, same `entityId`) after an exhausted event. The questions are: what does
   the server do with a CREATE whose `entityId` already exists under a different `opId`? And does it
   compare fields on a repeated `opId`?
   *Interim*: follow `CLAUDE.md` (rewrite in place, keep `opId`), and record the risk in the detail
   screen's code comments and in tests that pin the current behavior.
2. **Ops missing from both `acked` and `rejected`** in a 2xx push response. *Interim*: treat as a
   transport failure for those ops only (stay pending, +1 attempt).
3. **Non-retryable 4xx on the whole batch** (400, 401, 413…). The contract says "not retryable" but
   doesn't say whether that fails every op in the batch. *Interim*: every op in the batch becomes
   `failed` with kind `REFUSED` and reason `HTTP <code>`, and the run stops. The user can retry
   manually.
4. **Real base URL** for release builds. *Interim*: Gradle property `picktrace.syncBaseUrl`,
   defaulting to `https://sync.invalid/` (reserved TLD, obviously not real).

## Complexity Tracking

No constitution violations to justify.
