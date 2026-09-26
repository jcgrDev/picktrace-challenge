# Contract: Configuration

All settings are environment variables read once at startup. Defaults are the spec's
assumptions. Nothing is read from the database.

| Variable | Default | Used for |
|----------|---------|----------|
| `SYNC_DB_URL` | `jdbc:postgresql://localhost:5432/harvest` | JDBC URL. |
| `SYNC_DB_USER` / `SYNC_DB_PASSWORD` | `harvest` / `harvest` | Credentials (POC only). |
| `SYNC_DB_POOL_SIZE` | `20` | Hikari maximum pool size. Backpressure knob. |
| `SYNC_DB_POOL_TIMEOUT_MS` | `2000` | Hikari connection timeout; expiry maps to 429 `BUSY`. |
| `SYNC_HTTP_PORT` | `8080` | Listen port. |
| `SYNC_REQUEST_TIMEOUT_MS` | `10000` | Whole-request timeout; must stay below the client's 30 s. |
| `SYNC_MAX_BATCH_SIZE` | `500` | Ops per request. |
| `SYNC_MAX_BODY_BYTES` | `2097152` | Request body cap. |
| `SYNC_MAX_QUANTITY` | `10000` | Validation upper bound. |
| `SYNC_FUTURE_TOLERANCE_SECONDS` | `900` | Timestamp may exceed server time by this much. |
| `SYNC_WINDOW_SECONDS` | `86400` | Late flag threshold. |
| `SYNC_DEADLOCK_RETRIES` | `3` | Attempts per batch on SQLSTATE 40P01 / 40001. |
| `SYNC_DEADLOCK_RETRY_DELAY_MS` | `10-100` | Uniform random delay between attempts. |
| `SYNC_RETRY_AFTER_SECONDS` | `5-30` | Uniform random `Retry-After` on 429 and 503. |
| `SYNC_MIGRATE_ON_START` | `true` | Run Flyway at boot. Tests run it explicitly. |

Database settings the deploy must keep at their defaults (asserted by `SchemaTest` on the test
container): `synchronous_commit = on`, `fsync = on`, `full_page_writes = on`. Neither table is
unlogged.
