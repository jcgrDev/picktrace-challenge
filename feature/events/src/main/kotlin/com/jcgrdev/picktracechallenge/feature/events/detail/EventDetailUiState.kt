package com.jcgrdev.picktracechallenge.feature.events.detail

import com.jcgrdev.picktracechallenge.core.model.FieldEventDetail
import com.jcgrdev.picktracechallenge.core.model.ValidationError

data class EventDetailUiState(
    val detail: FieldEventDetail? = null,
    /** Non-null while editing the quantity. */
    val editingQuantity: String? = null,
    val errors: Set<ValidationError> = emptySet(),
    val message: UserMessage? = null,
    /** The event is gone (deleted here, or no longer exists); the screen pops back. */
    val deleted: Boolean = false,
)

sealed interface UserMessage {
    data object ReadOnlySynced : UserMessage
    data object InFlight : UserMessage
    data object StorageFull : UserMessage
}
