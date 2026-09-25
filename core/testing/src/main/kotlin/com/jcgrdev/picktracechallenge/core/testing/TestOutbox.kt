package com.jcgrdev.picktracechallenge.core.testing

import androidx.room.withTransaction
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.model.BlockId
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.Uuid7
import com.jcgrdev.picktracechallenge.core.model.WorkerId
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.sync.FIELD_EVENT_ENTITY_TYPE
import com.jcgrdev.picktracechallenge.core.sync.FIELD_EVENT_SCHEMA_VERSION
import com.jcgrdev.picktracechallenge.core.sync.FieldEventCodec
import com.jcgrdev.picktracechallenge.core.sync.HlcClock
import com.jcgrdev.picktracechallenge.core.sync.OP_TYPE_CREATE
import java.time.Clock
import java.time.Instant

/**
 * Seeds the outbox for `:core:sync` tests, which cannot use `:core:data` (it depends on sync).
 * Writes exactly what `OfflineFirstFieldEventRepository.record` writes, in one transaction.
 */
object TestOutbox {

    private val ids: IdGenerator = Uuid7()

    suspend fun record(
        db: PicktraceDatabase,
        workerId: String = "w_001",
        blockId: String = "block_42",
        quantity: Int = 3,
        timestamp: Instant = Instant.parse("2025-06-10T08:32:00Z"),
        clock: Clock = Clock.systemUTC(),
    ): FieldEvent {
        val event = FieldEvent(ids.next(), WorkerId(workerId), BlockId(blockId), quantity, timestamp, SyncStatus.PENDING)
        db.withTransaction {
            val hlc = HlcClock(db.syncStateDao(), clock).tick()
            db.fieldEventDao().insert(
                FieldEventEntity(event.id.toString(), workerId, blockId, quantity, timestamp.toEpochMilli(), SyncStatus.PENDING),
            )
            db.pendingOpDao().insert(
                PendingOpEntity(
                    opId = ids.next().toString(),
                    entityType = FIELD_EVENT_ENTITY_TYPE,
                    entityId = event.id.toString(),
                    opType = OP_TYPE_CREATE,
                    schemaVersion = FIELD_EVENT_SCHEMA_VERSION,
                    hlc = hlc.encode(),
                    fieldsJson = FieldEventCodec(SyncJson).encodeToString(event),
                    state = OpState.QUEUED,
                ),
            )
        }
        return event
    }

    suspend fun recordMany(db: PicktraceDatabase, count: Int, quantity: (Int) -> Int = { it + 1 }): List<FieldEvent> =
        List(count) { record(db, quantity = quantity(it)) }
}
