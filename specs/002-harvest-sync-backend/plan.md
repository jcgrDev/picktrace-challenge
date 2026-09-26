# Implementation Plan: Harvest Sync Backend POC

**Branch**: `main` (feature directory `specs/002-harvest-sync-backend`, no feature branch by
user choice) | **Date**: 2026-09-25 | **Spec**: [spec.md](spec.md)

**Input**: Backend design document at `specs/002-harvest-sync-backend/spec.md`

## Summary

Build the server half of the sync contract the Android app already speaks: `POST /v1/sync/push`
accepting up to 500 CREATE ops, storing each harvest record exactly once in PostgreSQL, and
acknowledging only what has committed. Correctness under concurrency comes from unique indexes on
record id and op id, one short READ COMMITTED transaction per batch with rows supplied in a fixed
key order, a bounded retry on deadlock, and acks built from what the database reports back. The
POC is proven by correctness tests, race tests against a real PostgreSQL, and a load test of
hundreds of simulated devices with deliberate duplicates, followed by invariant checks.

The server lives in a standalone Gradle build under `backend/` so the Android build is untouched.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (same as the app's version catalog), JDK 17 (same as the app).

**Primary Dependencies** (looked up 2026-09-25, latest stable; see [research.md](research.md);
**all require approval under Constitution Principle V before any code is written**):
Ktor server 3.5.2 (Netty engine, content negotiation with kotlinx.serialization JSON, status pages,
call id, call logging), HikariCP 7.1.0, PostgreSQL JDBC 42.7.12, Flyway 13.8.0 (core +
postgresql module), kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0, Logback (version to be
looked up when added).

**Storage**: PostgreSQL 18 (latest stable major; 19 is in beta and excluded by the constitution).
Local via Docker Compose; tests via Testcontainers.

**Testing**: JUnit Jupiter 6.1.3, Ktor server test host for handler tests without a socket,
Testcontainers 2.0.5 (postgresql module) for every database test, a `loadtest` subproject using
the Ktor CIO client and coroutines to simulate devices. No in-memory database anywhere.

**Target Platform**: Linux JVM 17 server, one or more instances behind a load balancer; locally two
instances on different ports via Docker Compose.

**Project Type**: web-service (single endpoint plus health), standalone Gradle build in `backend/`.

**Performance Goals**: p99 under 2 s for 500-op batches with 300 devices syncing within a 60 s
window on two instances; batch transaction under 100 ms at p99 on a modest database; zero
deadlock retries in well-formed runs.

**Constraints**: nothing external inside a transaction; pool of 20 per instance with a 2 s
acquisition timeout answered by 429; server request timeout 10 s, under the client's 30 s; no
`GlobalScope`, no `runBlocking` in production code (JDBC calls run on `Dispatchers.IO` inside the
request coroutine); `synchronous_commit`, `fsync`, `full_page_writes` on.

**Scale/Scope**: hundreds of devices, batches of 50 to 500, tens of thousands of records per day.
One entity type, one write endpoint, two tables, no read endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution was written for the Android app. Where a principle addresses the client, this
plan states what the server must do so the client can keep honouring it.

| Principle | Applies how | Status |
|-----------|-------------|--------|
| I. Room is the single source of truth | Client-side. Server counterpart: PostgreSQL is the single source of truth, no cache or in-memory dedup set, acks derive only from committed rows. | Pass |
| II. Atomic local writes | Client-side. Server counterpart: record row and op row are written in one transaction; no state exists with one and not the other. | Pass |
| III. Client-owned identity and ordering | Server accepts client UUIDv7 `opId` and `entityId` as primary keys, stores `schemaVersion` and `hlc`, is idempotent by `opId` so the client can retry blindly, and never asks the client to change an opId on transport failure. | Pass |
| IV. Never clobber pending work | Client-side. Server never rewrites a stored record; a different content under the same id is rejected, not merged. | Pass |
| V. Ask, don't invent | Endpoint path and envelope are the ones in `CLAUDE.md` (`/sync/push` under a `/v1` base URL). Two additions need recording: the optional `detail` field on rejections and the reason codes `OP_CONTENT_MISMATCH`, `ID_CONFLICT`, `INVALID_RECORD`, `UNSUPPORTED_OP_KIND`. Every backend library above is outside the `CLAUDE.md` list. | **Pending approval**: libraries and the two contract additions must be approved and mirrored into `CLAUDE.md` before implementation starts. No gate failure, because the plan adds nothing on its own. |
| VI. Test-backed phases | Five implementation phases below, each ending with build and test output, a summary, files touched, open questions, and a STOP. | Pass |
| Technical constraint: KSP only, no kapt | No annotation processing in the backend. kotlinx.serialization is a compiler plugin, Ktor uses none. | Pass |
| Technical constraint: no `GlobalScope` / `runBlocking` | Ktor handlers are suspend functions; JDBC work runs under `withContext(Dispatchers.IO)`. The `loadtest` subproject is a test tool, so its `main` may use `runBlocking`; that is stated here so it is not a surprise. | Pass with note |
| Technical constraint: latest stable, no alpha or beta | All versions looked up 2026-09-25; PostgreSQL 19 beta excluded. | Pass |
| Technical constraint: FCM and pull engine deferred | Server implements push only; pull and snapshot are not built. | Pass |

**Post-design re-check (after Phase 1)**: the data model and contracts add no library, no
endpoint, and no field beyond the two additions already listed under Principle V. Gate status
unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/002-harvest-sync-backend/
├── spec.md              # Backend design document (approved input)
├── plan.md              # This file
├── research.md          # Phase 0: versions, library choices, alternatives
├── data-model.md        # Phase 1: tables, keys, indexes, migrations, config
├── quickstart.md        # Phase 1: run and validate end to end
├── contracts/
│   ├── sync-push-api.md     # HTTP contract with examples and error catalogue
│   ├── canonical-form.md    # Fingerprint spec and shared test vectors
│   └── configuration.md     # Environment variables and defaults
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks), not created here
```

### Source Code (repository root)

```text
backend/                          # standalone Gradle build, own settings and wrapper
├── settings.gradle.kts           # includes :server and :loadtest
├── build.gradle.kts
├── gradle/libs.versions.toml     # backend catalog (versions from research.md)
├── docker-compose.yml            # postgres:18 + two server instances
├── server/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/jcgrdev/picktrace/sync/
│       │   ├── Application.kt            # Ktor module wiring, plugins, routes
│       │   ├── config/                   # typed config loaded from environment
│       │   ├── api/                      # route, request/response DTOs, error mapping
│       │   ├── domain/                   # Op, Record, Outcome, reason codes
│       │   ├── canonical/                # canonical form, fingerprint
│       │   ├── validation/               # per-op validation rules
│       │   ├── planning/                 # duplicate collapse, same-record resolution, sorting
│       │   └── persistence/              # pool, migrations, transaction runner, three passes
│       ├── main/resources/
│       │   ├── db/migration/             # Flyway versioned migrations (V1 schema)
│       │   └── logback.xml
│       └── test/kotlin/com/jcgrdev/picktrace/sync/
│           ├── canonical/                # test vectors from contracts/canonical-form.md
│           ├── validation/
│           ├── planning/
│           ├── api/                      # Ktor test host: whole-batch errors, response shape
│           ├── persistence/              # Testcontainers: correctness, race, crash injection
│           └── support/                  # PostgresExtension, request builders
└── loadtest/
    ├── build.gradle.kts
    └── src/main/kotlin/com/jcgrdev/picktrace/loadtest/
        ├── Main.kt                       # arguments: base URLs, devices, ops, duplicate rate
        ├── DeviceSimulator.kt
        └── Invariants.kt                 # queries the database and prints pass/fail
```

**Structure Decision**: a standalone Gradle build in `backend/`, not a module of the Android
build. Justification: the Android build carries AGP 9.4 and Android-only convention plugins; a
JVM server needs neither, and mixing them would slow `./gradlew test` at the root and couple two
deploy artifacts with different lifecycles. The backend has its own version catalog because its
libraries do not overlap with the app's except Kotlin, coroutines and serialization, whose
versions are copied from the root catalog to stay aligned. Package prefix
`com.jcgrdev.picktrace.sync` mirrors the app's package convention.

## Implementation Phases

Each phase ends with the constitution's phase gate: build passes, tests pass with output shown,
summary, files touched, open questions, compliance statement, STOP for approval.

| Phase | Scope | Exit evidence |
|-------|-------|---------------|
| B0 Approval | Approve libraries and the two contract additions; mirror into `CLAUDE.md`. | `CLAUDE.md` diff. No code. |
| B1 Skeleton | Gradle build, catalog, Ktor app with health route, config loading, Hikari pool, Flyway V1 migration, Docker Compose, Testcontainers extension. | `./gradlew test` green with one migration test proving the schema and its unique indexes exist. |
| B2 Pure core | Canonical form, fingerprint, validation, batch planning (duplicate collapse, same-record resolution, sort, request-order map). | Unit tests including every shared test vector. |
| B3 Persistence and endpoint | Transaction runner with the three passes and conflict reads, deadlock retry, outcome assembly, `POST /v1/sync/push`, whole-batch error mapping. | Correctness scenarios from spec Section 11 green against Testcontainers. |
| B4 Concurrency | Race tests (same batch parallel, overlapping batches, pathological opId), crash injection, backpressure (pool timeout to 429, `Retry-After`), request timeout. | Race tests green with deadlock counter asserted; crash tests prove no ack without a row. |
| B5 Load | `loadtest` subproject, Compose with two instances, invariant checker, undersized-pool run. | Load run output with the five invariants and the operational numbers from spec Section 11. |

## Open Questions

Carried from the design document (spec Section 14) plus planning-specific ones. Each has a
temporary answer so work can proceed.

| # | Question | Temporary answer |
|---|----------|------------------|
| 1 | Approve the backend library list? | Assumed yes for planning; B0 blocks on the actual answer. |
| 2 | Should `CLAUDE.md` gain a "Backend" section (stack, module, contract additions)? | Yes, in B0, kept short and pointing at this feature directory. |
| 3 | Where does the schema live: Flyway migrations or a startup script? | Flyway. It is the standard, versioned, and makes v1 schema changes routine. |
| 4 | Plain JDBC or an ORM/DSL for the three passes? | Plain JDBC with parameterised statements. The passes need `ON CONFLICT ... RETURNING` semantics and exact row order; a DSL adds abstraction without adding safety here. See research.md. |
| 5 | Should the load test be an external tool? | No. A Kotlin subproject reuses the DTOs and can check invariants against the database in the same run. |
| 6 | Quantity unit and maximum? | Unitless, max 10 000, configurable. |

## Complexity Tracking

No constitution violations. Two justified deviations from "simplest possible":

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|------------|-------------------------------------|
| Separate `loadtest` subproject | Keeps load-generation dependencies (Ktor client) out of the server artifact and lets the run be started with arguments. | A test in `server` would run under JUnit's lifecycle and could not easily target two live instances. |
| Flyway for one migration | Versioned schema from day one so v1 changes (versions, voids) are ordinary migrations. | A startup script is fine for one table set but has to be replaced the first time the schema changes. |
