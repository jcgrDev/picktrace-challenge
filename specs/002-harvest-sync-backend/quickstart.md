# Quickstart: Harvest Sync Backend POC

How to run the server and prove the guarantee end to end. Everything below is what phase B5
must make true; until then, only the steps for completed phases work.

## Prerequisites

- JDK 17 and Docker (Docker Compose v2) on the machine.
- Approval of the backend library list (plan phase B0).

## 1. Start PostgreSQL and two server instances

```bash
cd backend
docker compose up -d postgres
./gradlew :server:run            # instance 1 on 8080, migrates on start
# in a second shell, or use `docker compose up` for both instances on 8081/8082
SYNC_HTTP_PORT=8081 ./gradlew :server:run
curl -s localhost:8080/health    # {"status":"ok"}
```

## 2. Push a batch by hand

Save the two-op example from `contracts/sync-push-api.md` as `batch.json`, then:

```bash
curl -s -X POST localhost:8080/v1/sync/push \
  -H 'Content-Type: application/json' -H 'X-Device-Id: tablet-0042' \
  --data @batch.json
```

Expected: one opId in `acked`, one in `rejected` with `INVALID_RECORD`.

Resend the same file unchanged: identical response. Send it to the other instance: identical
response. Edit the first op's `quantity` and resend: `OP_CONTENT_MISMATCH`. Change that op's
`opId` only: `ID_CONFLICT`. Restore the original quantity with the new opId: `acked`.

## 3. Run the test suite

```bash
cd backend
./gradlew test
```

Runs, against a throwaway PostgreSQL container:

| Group | Proves |
|-------|--------|
| `canonical` | Every vector in `contracts/canonical-form.md` |
| `validation`, `planning` | Rules and in-request duplicate handling |
| `api` | Whole-batch errors, response ordering, unknown-key tolerance |
| `persistence` correctness | Spec Section 11 correctness table |
| `persistence` race | Same batch × 50 in parallel; overlapping batches; pathological opId; deadlock retry counter = 0 for well-formed runs |
| `persistence` crash | Connection cut before commit → nothing stored; after commit → stored and re-acked |
| `persistence` schema | Unique indexes present; durability settings on |

## 4. Load test with deliberate duplicates

```bash
cd backend
docker compose up -d                      # postgres + server-a (8081) + server-b (8082)
./gradlew :loadtest:run --args="--urls http://localhost:8081,http://localhost:8082 \
  --devices 300 --ops-per-device 50-500 --spread-seconds 60 \
  --duplicate-rate 0.20 --invalid-rate 0.05"
```

The run prints the five invariants from `data-model.md` and the operational numbers:

```
I1 every op_log row references a record ........ PASS
I2 every record has an op_log row .............. PASS
I3 op fingerprint = record fingerprint ......... PASS
I4 stored ids = distinct valid ids sent ........ PASS (n=…)
I5 acked ⇒ op row, rejected ⇒ no op row ........ PASS
p99 latency … ms | 429 responses … | deadlock retries … | max tx … ms
```

Backpressure run: restart the servers with `SYNC_DB_POOL_SIZE=5` and repeat. Expect some 429s,
every device fully synced by the end, and no request slower than 10 s.

## 5. Clean up

```bash
cd backend && docker compose down -v
```
