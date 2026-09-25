package com.jcgrdev.picktracechallenge.core.data

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.database.model.FieldEventWithOp
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.model.BlockId
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.FieldEventDetail
import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.FieldEventValidator
import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import com.jcgrdev.picktracechallenge.core.model.QuantityResult
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.ValidationResult
import com.jcgrdev.picktracechallenge.core.model.WorkerId
import com.jcgrdev.picktracechallenge.core.sync.FIELD_EVENT_ENTITY_TYPE
import com.jcgrdev.picktracechallenge.core.sync.FIELD_EVENT_SCHEMA_VERSION
import com.jcgrdev.picktracechallenge.core.sync.FieldEventCodec
import com.jcgrdev.picktracechallenge.core.sync.HlcClock
import com.jcgrdev.picktracechallenge.core.sync.OP_TYPE_CREATE
import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

interface FieldEventRepository {
    /** Newest first (recording order). A null [filter] means every status. Backed by a Room Flow. */
    fun observeEvents(filter: SyncStatus? = null): Flow<List<FieldEvent>>

    /** Validates, then writes the event and its CREATE op atomically, then requests a sync. */
    suspend fun record(draft: FieldEventDraft): RecordResult

    /** Emits null if the event does not exist or was deleted. */
    fun observeEvent(id: UUID): Flow<FieldEventDetail?>

    /** Quantity only (FR-007). Rewrites the CREATE op in place. Refused for synced and in-flight events. */
    suspend fun updateQuantity(id: UUID, quantity: String): MutationResult

    /** Removes the event and its op together, so it is never sent (FR-009). Same refusals as edit. */
    suspend fun delete(id: UUID): MutationResult
}

internal class OfflineFirstFieldEventRepository @Inject constructor(
    private val db: PicktraceDatabase,
    private val hlcClock: HlcClock,
    private val codec: FieldEventCodec,
    private val ids: IdGenerator,
    private val syncScheduler: SyncScheduler,
) : FieldEventRepository {

    private val fieldEventDao = db.fieldEventDao()
    private val pendingOpDao = db.pendingOpDao()

    override fun observeEvents(filter: SyncStatus?): Flow<List<FieldEvent>> =
        fieldEventDao.observeEvents(filter)
            .map { rows -> rows.map { it.toModel() } }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()

    override suspend fun record(draft: FieldEventDraft): RecordResult {
        val valid = when (val result = FieldEventValidator.validate(draft)) {
            is ValidationResult.Invalid -> return RecordResult.Invalid(result.errors)
            is ValidationResult.Valid -> result
        }
        val event = FieldEvent(
            id = ids.next(),
            workerId = valid.workerId,
            blockId = valid.blockId,
            quantity = valid.quantity,
            timestamp = draft.timestamp.truncatedTo(ChronoUnit.MILLIS),
            status = SyncStatus.PENDING,
        )
        try {
            db.withTransaction {
                val hlc = hlcClock.tick()
                fieldEventDao.insert(event.toEntity())
                pendingOpDao.insert(
                    PendingOpEntity(
                        opId = ids.next().toString(),
                        entityType = FIELD_EVENT_ENTITY_TYPE,
                        entityId = event.id.toString(),
                        opType = OP_TYPE_CREATE,
                        schemaVersion = FIELD_EVENT_SCHEMA_VERSION,
                        hlc = hlc.encode(),
                        fieldsJson = codec.encodeToString(event),
                        state = OpState.QUEUED,
                    ),
                )
            }
        } catch (e: SQLiteException) {
            if (e.isStorageFailure()) return RecordResult.StorageFull
            throw e
        }
        // After commit: a scheduling failure can never undo or tear the local write.
        syncScheduler.requestSync()
        return RecordResult.Recorded(event.id)
    }

    override fun observeEvent(id: UUID): Flow<FieldEventDetail?> =
        fieldEventDao.observeDetail(id.toString())
            .map { it?.toDetail() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()

    /**
     * Keeps the op's `op_id`, per CLAUDE.md "Edits before sync". If an earlier attempt of this op
     * reached the server and only the response was lost, the server may ack the old fields while the
     * device shows the new ones: plan.md Open Question 1.
     */
    override suspend fun updateQuantity(id: UUID, quantity: String): MutationResult {
        val newQuantity = when (val result = FieldEventValidator.validateQuantity(quantity)) {
            is QuantityResult.Invalid -> return MutationResult.Invalid(listOf(result.error))
            is QuantityResult.Valid -> result.quantity
        }
        return mutate(id) { current ->
            val op = checkNotNull(current.op) { "Unsynced event ${current.event.id} has no op" }
            val edited = current.event.toModel().copy(quantity = newQuantity)
            fieldEventDao.updateQuantity(current.event.id, newQuantity)
            pendingOpDao.rewriteFields(op.seq, codec.encodeToString(edited), hlcClock.tick().encode())
        }
    }

    override suspend fun delete(id: UUID): MutationResult = mutate(id) { current ->
        pendingOpDao.deleteByEntityId(FIELD_EVENT_ENTITY_TYPE, current.event.id)
        fieldEventDao.deleteById(current.event.id)
    }

    /**
     * Runs [change] in one transaction after the read-only guards, which are checked inside the same
     * transaction so a sync claiming the batch cannot slip in between check and write (research §R3).
     */
    private suspend fun mutate(id: UUID, change: suspend (FieldEventWithOp) -> Unit): MutationResult = try {
        db.withTransaction {
            val current = fieldEventDao.getDetail(id.toString()) ?: return@withTransaction MutationResult.NotFound
            when {
                current.event.status == SyncStatus.SYNCED -> MutationResult.ReadOnlySynced
                current.op?.state == OpState.IN_FLIGHT -> MutationResult.InFlight
                else -> {
                    change(current)
                    MutationResult.Success
                }
            }
        }
    } catch (e: SQLiteException) {
        if (e.isStorageFailure()) MutationResult.StorageFull else throw e
    }
}

/**
 * SQLITE_FULL makes SQLite roll the whole transaction back by itself. Room's endTransaction then
 * issues ROLLBACK, which fails with "cannot rollback - no transaction is active" and replaces the
 * original SQLiteFullException (seen in OfflineFirstFieldEventRepositoryTest). Only errors that abort
 * the whole transaction (full disk, I/O) produce that message; statement-level errors such as
 * constraint violations leave the transaction open and propagate unchanged. Either way nothing was
 * written.
 */
private fun SQLiteException.isStorageFailure(): Boolean =
    this is SQLiteFullException || message.orEmpty().contains("no transaction is active")

private fun FieldEvent.toEntity() = FieldEventEntity(
    id = id.toString(),
    workerId = workerId.value,
    blockId = blockId.value,
    quantity = quantity,
    timestampMs = timestamp.toEpochMilli(),
    status = status,
)

private fun FieldEventWithOp.toDetail() = FieldEventDetail(
    event = event.toModel(),
    attempts = op?.attempts ?: 0,
    failureKind = op?.failureKind,
    failureReason = if (event.status == SyncStatus.FAILED) op?.lastError else null,
    inFlight = op?.state == OpState.IN_FLIGHT,
)

private fun FieldEventEntity.toModel() = FieldEvent(
    id = UUID.fromString(id),
    workerId = WorkerId(workerId),
    blockId = BlockId(blockId),
    quantity = quantity,
    timestamp = Instant.ofEpochMilli(timestampMs),
    status = status,
)
