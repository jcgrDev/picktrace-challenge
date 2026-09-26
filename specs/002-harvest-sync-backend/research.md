# Research: Harvest Sync Backend POC

**Date**: 2026-09-25. All versions looked up on this date from vendor release pages and Maven
Central; none from memory. Re-check on the day a dependency is added (Constitution, Technical
Constraints).

## Versions

| Dependency | Version | Source | Notes |
|------------|---------|--------|-------|
| Kotlin | 2.4.20 | root `gradle/libs.versions.toml` | Same as the app to keep one toolchain. |
| JDK | 17 | root README | Same as the app. Ktor 3.5 supports 17+. |
| Ktor | 3.5.2 (2026-08-04) | [ktor.io releases](https://ktor.io/docs/releases.html) | Server Netty engine, content negotiation, kotlinx.serialization JSON, status pages, call id, call logging, test host; CIO client for the load test. |
| HikariCP | 7.1.0 | [Maven Central](https://central.sonatype.com/artifact/com.zaxxer/HikariCP) | Pool size and acquisition timeout are the backpressure knobs. |
| PostgreSQL JDBC | 42.7.12 (2026-06-29) | [pgjdbc changelog](https://jdbc.postgresql.org/changelogs/2026-06-29-42.7.12-release/) | |
| Flyway | 13.8.0 (2026-09-24) | [Redgate release notes](https://documentation.red-gate.com/fd/release-notes-for-flyway-engine-179732572.html) | `flyway-core` plus `flyway-database-postgresql`. |
| Testcontainers | 2.0.5 | [java.testcontainers.org](https://java.testcontainers.org/) | `postgresql` module, JUnit Jupiter integration. |
| JUnit | 6.1.3 (2026-08-07) | [docs.junit.org](https://docs.junit.org/current/release-notes/index.html) | Packages remain `org.junit.jupiter`. |
| kotlinx.serialization | 1.11.0 | root catalog | Same as the app so DTO behaviour matches. |
| kotlinx.coroutines | 1.11.0 | root catalog | |
| PostgreSQL server | 18 (18.6, 2026-08-13) | [postgresql.org](https://www.postgresql.org/docs/release/) | 19 is beta as of 2026-09-24; excluded. Docker image `postgres:18`. |
| Logback | look up when added | | SLF4J binding for Ktor call logging. |

Coupling checks: Ktor 3.5 requires Kotlin 2.x and kotlinx.serialization 1.7+; Testcontainers 2.x
requires JUnit 5.10+ (JUnit 6 is fine); Flyway 13 needs the separate PostgreSQL database module.

## Decisions

### D1. Standalone Gradle build under `backend/`

- **Decision**: `backend/` has its own `settings.gradle.kts`, wrapper and version catalog, with
  subprojects `:server` and `:loadtest`.
- **Rationale**: the Android build uses AGP and Android convention plugins; the server needs the
  JVM plugin and Ktor. Separate builds keep both fast and independently deployable.
- **Alternatives**: include `backend` in the root `settings.gradle.kts` (couples every root
  `./gradlew test` to the server and Docker); separate repository (loses the shared contract
  documents and the single review flow the project uses).

### D2. Plain JDBC for the transaction passes

- **Decision**: parameterised JDBC statements executed on `Dispatchers.IO`, wrapped by a small
  transaction runner that owns begin, commit, rollback and retry.
- **Rationale**: the design depends on three exact behaviours: multi-row insert with "do nothing on
  conflict" that reports the rows actually inserted, rows supplied in a controlled order, and a
  fresh read after a blocked insert. Plain statements express these directly. An ORM adds a
  session cache, which is precisely the "second place that can disagree" the design forbids.
- **Alternatives**: Exposed (JetBrains DSL; `insertIgnore` exists but the returning-rows semantics
  and batch ordering are indirect); jOOQ (capable, but a code generator and licence considerations
  for a POC); R2DBC (async driver, but no benefit with a bounded pool and short transactions, and
  weaker tooling around pooling and migrations).

### D3. Flyway for schema

- **Decision**: one versioned migration `V1__harvest_sync.sql` applied at startup and by the test
  extension.
- **Rationale**: the v1 path (versions, void flags, retention) is a series of schema changes;
  starting with versioned migrations makes them routine. The migration test also asserts the unique
  indexes exist, which is the only application-level check of the core guarantee.
- **Alternatives**: a startup script with `create if not exists` (fine for one version, must be
  replaced at the first change); Liquibase (heavier, XML/YAML-oriented).

### D4. Load test as a Kotlin subproject

- **Decision**: `:loadtest` with a `main` that takes base URLs, device count, ops per device,
  duplicate rate and invalid rate, drives devices with coroutines using the Ktor CIO client, then
  connects to the database and prints the five invariants.
- **Rationale**: reuses the DTOs and canonical form, so the generator cannot drift from the server;
  invariant checks run in the same process against the same database.
- **Alternatives**: k6 or Gatling (another tool and language; invariants would need a separate
  step); a JUnit test (cannot target two live instances cleanly and mixes load with the unit suite).

### D5. Fingerprint canonical form with a version prefix

- **Decision**: canonical string `v1|<opType>|<entityId>|<len>:<workerId>|<len>:<blockId>|<quantity>|<timestamp>`,
  SHA-256, stored as 32 bytes. Full rules and vectors in `contracts/canonical-form.md`.
- **Rationale**: the `v1` prefix lets a future canonical form coexist (v1 records keep matching v1
  retries). Length-prefixed strings remove delimiter ambiguity. SHA-256 is universally available on
  the JVM and on Android without a library.
- **Alternatives**: canonical JSON (needs a canonicalisation spec such as RFC 8785 and a library on
  both sides); storing the raw fields and comparing column by column (works, but the fingerprint
  also documents the equality rule in one place and keeps the op log narrow).

### D6. Conflict detection via "insert, do nothing on conflict, report inserted rows"

- **Decision**: each pass is one multi-row insert with conflict-do-nothing that returns the inserted
  keys; the difference between supplied and returned keys is the conflict set, which is then read
  in a separate statement.
- **Rationale**: PostgreSQL blocks a conflicting insert until the other transaction ends, so the
  loser learns the truth without any application lock. READ COMMITTED gives the follow-up read a
  fresh snapshot that sees the winner's commit.
- **Alternatives**: advisory locks per key (adds a lock protocol on top of the one the index already
  provides); `SELECT ... FOR UPDATE` before insert (cannot lock a row that does not exist yet, so
  it does not close the race); upsert that overwrites (violates "never rewrite a stored record").

### D7. Backpressure at the pool

- **Decision**: Hikari `maximumPoolSize` 20, `connectionTimeout` 2 000 ms; a pool timeout maps to
  HTTP 429 with `Retry-After` drawn uniformly from 5 to 30 s. Ktor request timeout 10 s.
- **Rationale**: as argued in spec Section 8.7; the pool is the scarce resource and refusing fast
  beats queueing past the client's patience.
- **Alternatives**: a semaphore in front of the handler (duplicates what the pool already measures);
  Ktor's rate-limit plugin per device (useful in v1 with real identity, not a substitute for pool
  backpressure).

### D8. Local topology

- **Decision**: Docker Compose with `postgres:18` (durability settings asserted on, small
  `max_connections` left at default 100) and two `server` containers on ports 8081 and 8082; the
  load test takes both URLs and alternates.
- **Rationale**: proves horizontal scaling (spec G6) on a laptop without a load balancer.
- **Alternatives**: a single instance (does not test G6); a real load balancer container (adds a
  component for no additional proof).

## Resolved unknowns

All Technical Context fields are filled; no NEEDS CLARIFICATION remains. The single blocking item
is approval of the library list (Constitution Principle V), tracked as phase B0 in the plan.
