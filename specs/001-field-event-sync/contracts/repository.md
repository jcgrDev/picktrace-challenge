# Contract: Module interfaces (`:core:data`, `:core:sync`)

These are the Kotlin surfaces that feature modules and `:app` depend on. Implementations are internal
to their modules and bound with Hilt. Domain types are defined in
[data-model.md](../data-model.md#domain-coremodel-pure-kotlin).

## `:core:data`

```kotlin
interface FieldEventRepository {
    /** Newest first (recording order). `null` filter = all statuses. Backed by a Room Flow. */
    fun observeEvents(filter: SyncStatus? = null): Flow<List<FieldEvent>>

    /** Emits null if the event does not exist or was deleted. */
    fun observeEvent(id: UUID): Flow<FieldEventDetail?>

    /** Validates, then inserts event + CREATE op atomically, then requestSync(). */
    suspend fun record(draft: FieldEventDraft): RecordResult

    /** Quantity only (FR-007). Rewrites the CREATE op in place. */
    suspend fun updateQuantity(id: UUID, quantity: String): MutationResult

    /** Removes event and op together; the event is never sent (FR-009). */
    suspend fun delete(id: UUID): MutationResult

    /** FAILED -> PENDING with a new opId and attempts = 0, same queue position; then requestSync(). */
    suspend fun retry(id: UUID): MutationResult
}

sealed interface RecordResult {
    data class Recorded(val id: UUID) : RecordResult
    data class Invalid(val errors: List<ValidationError>) : RecordResult
    data object StorageFull : RecordResult
}

sealed interface MutationResult {
    data object Success : MutationResult
    data object NotFound : MutationResult
    data object ReadOnlySynced : MutationResult        // FR-009a: "Synced events are read-only"
    data object InFlight : MutationResult              // FR-009a: "Syncing now, try again when it finishes"
    data object NotFailed : MutationResult             // retry() on a non-FAILED event
    data class Invalid(val errors: List<ValidationError>) : MutationResult
    data object StorageFull : MutationResult
}

interface SyncActivityRepository {
    /** Latest sync_run row mapped to a UI-friendly summary; null before the first run. */
    fun observeLatestRun(): Flow<SyncRunSummary?>
}

data class SyncRunSummary(
    val state: RunState,          // RUNNING, COMPLETED, TRANSPORT_ERROR, RATE_LIMITED, REFUSED, INTERRUPTED
    val startedAt: Instant, val finishedAt: Instant?,
    val batches: Int, val acked: Int, val rejected: Int, val lastError: String?,
)
```

The guard checks run inside the write transaction, so their outcome can't race the sync engine
(research R3). Refusals leave the database byte-for-byte unchanged.

## `:core:sync`

```kotlin
interface SyncScheduler {
    /** Enqueue unique work "field-event-sync" (APPEND_OR_REPLACE, NetworkType.CONNECTED). */
    fun requestSync(expedited: Boolean = false, delay: Duration = Duration.ZERO)
}

/** Seam for anything that should cause a sync: connectivity today, FCM later. */
interface SyncTrigger {
    fun start()
    fun stop()
}

data class SyncConfig(
    val batchSize: Int = 50,             // clamped to 1..500
    val maxTransportAttempts: Int = 5,
    val defaultRetryAfter: Duration = 30.seconds,
)

/** Internal to :core:sync; exposed for tests via @VisibleForTesting constructor. */
class SyncEngine {
    suspend fun run(): RunResult          // COMPLETED | Retry | RetryAfter(delay) | Refused
}
```

`SyncEngine` never calls `FieldEventRepository`. It reads and writes the DAOs and calls `SyncApi`
directly, which keeps the dependency direction `:core:data → :core:sync` acyclic.
