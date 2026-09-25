package com.jcgrdev.picktracechallenge.feature.capture

import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.data.MutationResult
import com.jcgrdev.picktracechallenge.core.data.RecordResult
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.FieldEventDetail
import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** Records every draft it is asked to store and answers with [nextResult]. */
class FakeFieldEventRepository : FieldEventRepository {
    val recorded = mutableListOf<FieldEventDraft>()
    var nextResult: RecordResult = RecordResult.Recorded(UUID(0, 1))

    override fun observeEvents(filter: SyncStatus?): Flow<List<FieldEvent>> = flowOf(emptyList())

    override suspend fun record(draft: FieldEventDraft): RecordResult {
        recorded += draft
        return nextResult
    }

    override fun observeEvent(id: UUID): Flow<FieldEventDetail?> = flowOf(null)
    override suspend fun updateQuantity(id: UUID, quantity: String) = MutationResult.NotFound
    override suspend fun delete(id: UUID) = MutationResult.NotFound
}
