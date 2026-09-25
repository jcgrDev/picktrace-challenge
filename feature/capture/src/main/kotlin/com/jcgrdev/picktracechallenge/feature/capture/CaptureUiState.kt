package com.jcgrdev.picktracechallenge.feature.capture

import com.jcgrdev.picktracechallenge.core.model.ValidationError
import java.time.Instant

data class CaptureUiState(
    val workerId: String = "",
    val blockId: String = "",
    val quantity: String = "",
    /** When the event will be recorded as having happened; shown read-only. */
    val timestamp: Instant,
    val errors: Set<ValidationError> = emptySet(),
    val saving: Boolean = false,
    val message: UserMessage? = null,
)

sealed interface UserMessage {
    data object Saved : UserMessage
    data object StorageFull : UserMessage
}
