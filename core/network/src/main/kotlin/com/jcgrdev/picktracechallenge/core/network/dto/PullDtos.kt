package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChangeDto(
    val entityType: EntityType,
    val entityId: String,
    /** Server version, monotonic per entity. */
    val version: Long,
    val hlc: String,
    /** Tombstone. */
    val deleted: Boolean = false,
    /** Full state when not deleted. */
    val fields: JsonObject? = null,
)

/** `GET /sync/pull?cursor=&limit=`. Declared for the contract; unused until the pull phase. */
@Serializable
data class PullResponse(
    val changes: List<ChangeDto>,
    val nextCursor: Long,
    val hasMore: Boolean,
)
