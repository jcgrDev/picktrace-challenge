package com.jcgrdev.picktracechallenge.feature.events.list

import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import java.time.Instant

data class EventListUiState(
    /** Null means every status. */
    val filter: SyncStatus? = null,
    val events: List<EventRow> = emptyList(),
    val loading: Boolean = true,
)

data class EventRow(
    val id: String,
    val workerId: String,
    val blockId: String,
    val quantity: Int,
    val timestamp: Instant,
    val status: SyncStatus,
)
