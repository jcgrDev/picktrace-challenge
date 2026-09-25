package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One outbox row as sent to the server. `opId` is the idempotency key. */
@Serializable
data class PendingOpDto(
    val opId: String,
    val entityType: EntityType,
    val entityId: String,
    val opType: OpType,
    val schemaVersion: Int,
    val hlc: String,
    /** Server version the op was based on; null for CREATE. */
    val baseVersion: Long? = null,
    /** Changed fields only; null for DELETE. `:core:sync` owns the typed codec per entity type. */
    val fields: JsonObject? = null,
)
