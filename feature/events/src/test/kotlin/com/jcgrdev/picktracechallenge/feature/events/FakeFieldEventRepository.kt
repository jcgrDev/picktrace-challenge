package com.jcgrdev.picktracechallenge.feature.events

import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.data.MutationResult
import com.jcgrdev.picktracechallenge.core.data.RecordResult
import com.jcgrdev.picktracechallenge.core.model.BlockId
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.FieldEventDetail
import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.WorkerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/** In-memory stand-in: events are held newest first, mutations answer with programmable results. */
class FakeFieldEventRepository : FieldEventRepository {
    val events = MutableStateFlow<List<FieldEvent>>(emptyList())
    val details = MutableStateFlow<Map<UUID, FieldEventDetail>>(emptyMap())
    var updateResult: MutationResult = MutationResult.Success
    var deleteResult: MutationResult = MutationResult.Success
    val updates = mutableListOf<Pair<UUID, String>>()
    val deletes = mutableListOf<UUID>()

    override fun observeEvents(filter: SyncStatus?): Flow<List<FieldEvent>> =
        events.map { list -> list.filter { filter == null || it.status == filter } }

    override suspend fun record(draft: FieldEventDraft): RecordResult = error("not used")

    override fun observeEvent(id: UUID): Flow<FieldEventDetail?> = details.map { it[id] }

    override suspend fun updateQuantity(id: UUID, quantity: String): MutationResult {
        updates += id to quantity
        return updateResult
    }

    override suspend fun delete(id: UUID): MutationResult {
        deletes += id
        return deleteResult
    }
}

fun event(n: Long, status: SyncStatus = SyncStatus.PENDING, quantity: Int = n.toInt()) = FieldEvent(
    id = UUID(0, n),
    workerId = WorkerId("w_00$n"),
    blockId = BlockId("block_$n"),
    quantity = quantity,
    timestamp = Instant.parse("2025-06-10T08:32:00Z").plusSeconds(n),
    status = status,
)

fun detail(event: FieldEvent, attempts: Int = 0, inFlight: Boolean = false) = FieldEventDetail(
    event = event,
    attempts = attempts,
    failureKind = null,
    failureReason = null,
    inFlight = inFlight,
)
