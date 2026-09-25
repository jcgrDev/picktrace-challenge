package com.jcgrdev.picktracechallenge.core.model

import java.time.Instant
import java.util.UUID

enum class SyncStatus { PENDING, SYNCED, FAILED }

enum class FailureKind {
    /** The server rejected the op; permanent until the user retries. */
    REJECTED,

    /** Transport attempts ran out (default 5). */
    EXHAUSTED,

    /** The server refused the whole batch with a non-retryable 4xx. */
    REFUSED,
}

data class FieldEvent(
    val id: UUID,
    val workerId: WorkerId,
    val blockId: BlockId,
    val quantity: Int,
    val timestamp: Instant,
    val status: SyncStatus,
)

data class FieldEventDetail(
    val event: FieldEvent,
    val attempts: Int,
    val failureKind: FailureKind?,
    val failureReason: String?,
    val inFlight: Boolean,
) {
    val canEdit: Boolean get() = event.status != SyncStatus.SYNCED && !inFlight
    val canRetry: Boolean get() = event.status == SyncStatus.FAILED
}

/** Raw form input; validated by [FieldEventValidator]. The timestamp is truncated to ms on record. */
data class FieldEventDraft(
    val workerId: String,
    val blockId: String,
    val quantity: String,
    val timestamp: Instant,
)

/** Source of entity and op ids. [Uuid7] in production; tests inject fakes. */
fun interface IdGenerator {
    fun next(): UUID
}
