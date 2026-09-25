# Contract: `FakeSyncServer` (`:core:testing`)

An `okhttp3.Interceptor` that implements the [sync API](sync-api.md) in memory. It never calls
`chain.proceed()` for `/sync/*` paths, and any other path gets a 404. It exists because the backend
doesn't yet (Principle V). Its behavior must stay inside the contract. Anything marked *fake-only*
is a test or demo convenience and must not be read as backend behavior.

## Default behavior

| Endpoint | Behavior |
|----------|----------|
| `POST /sync/push` | For each op in order: if `opId` was already acked, echo it in `acked`. Otherwise, if a reject rule matches, add it to `rejected` with the rule's reason. Otherwise store the entity (`version` += 1, cursor += 1), remember the `opId`, and add it to `acked`. A body with more than 500 ops or bad JSON gets a `400`. |
| `GET /sync/pull` | Changes with cursor > `cursor`, up to `limit`; `nextCursor`, `hasMore`. `410` if `cursor` < `expiredBefore` (default 0, so never). |
| `GET /sync/snapshot` | Stored entities paged 100 per page; the last page carries the current cursor. |

State (acked `opId`s, entities, cursor, delivery log) lives in the instance. A new instance starts
empty. Tests share one instance across two engine instances to simulate an app relaunch, so a
duplicate `opId` is observable.

## Test controls

```kotlin
class FakeSyncServer(private val json: Json) : Interceptor {
    val deliveryLog: List<PendingOpDto>                       // every op processed, in order, incl. repeats
    val ackedOpIds: Set<String>
    val pushes: List<List<String>>                            // opIds of every push received, faulted or not (batch shape)
    fun storedEntity(entityId: String): ChangeDto?            // latest stored state; version == times stored

    fun rejectWhen(reason: String, predicate: (PendingOpDto) -> Boolean)
    fun enqueueFault(fault: Fault)                            // consumed by the next push, FIFO
    fun reset()

    sealed interface Fault {
        data object Pass : Fault                              // no fault; lets a test target a later push
        data object DropConnection : Fault                    // throws IOException before processing
        data object DropAfterProcessing : Fault               // processes (acks stored), then throws IOException
        data class Status(val code: Int, val retryAfter: String? = null) : Fault
        data object GarbageBody : Fault                       // 200 with a non-JSON body
        data class PartialBody(val omitOpIds: Set<String>) : Fault  // 200, omits ops from both lists (OQ2)
    }
}
```

`DropAfterProcessing` is the "server accepted, the app never heard back" case (edge case "duplicate
delivery", SC-005).

## Debug-build configuration (*fake-only*)

In debug `:app`, one reject rule is installed so US5 can be exercised by hand: any op whose
`quantity` is 1000 or more is rejected with reason `"Rejected by FakeSyncServer: quantity >= 1000"`.
Because quantity is the editable field, a user can fix the event, retry it, and watch it sync.
Fault injection has no UI in v1. Automated tests cover the transport paths.
