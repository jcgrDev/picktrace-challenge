package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable

/** Typed payload for FIELD_EVENT ops, carried in [PendingOpDto.fields]. */
@Serializable
data class FieldEventFields(
    val workerId: String,
    val blockId: String,
    val quantity: Int,
    /** ISO-8601 UTC, e.g. `2025-06-10T08:32:00Z`. */
    val timestamp: String,
)
