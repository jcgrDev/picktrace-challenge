package com.jcgrdev.picktracechallenge.core.data

import com.jcgrdev.picktracechallenge.core.model.ValidationError
import java.util.UUID

sealed interface RecordResult {
    data class Recorded(val id: UUID) : RecordResult
    data class Invalid(val errors: List<ValidationError>) : RecordResult
    data object StorageFull : RecordResult
}

sealed interface MutationResult {
    data object Success : MutationResult
    data object NotFound : MutationResult

    /** FR-009a: "Synced events are read-only". */
    data object ReadOnlySynced : MutationResult

    /** FR-009a: "Syncing now, try again when it finishes". */
    data object InFlight : MutationResult

    /** retry() on an event that is not FAILED. */
    data object NotFailed : MutationResult
    data class Invalid(val errors: List<ValidationError>) : MutationResult
    data object StorageFull : MutationResult
}
