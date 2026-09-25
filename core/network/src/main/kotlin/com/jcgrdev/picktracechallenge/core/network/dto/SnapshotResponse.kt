package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable

/** `GET /sync/snapshot?page=`. Declared for the contract; unused until the pull phase. */
@Serializable
data class SnapshotResponse(
    /** Never tombstones. */
    val entities: List<ChangeDto>,
    val page: Int,
    val hasMore: Boolean,
    /** Resume pull from here once [hasMore] is false. */
    val cursor: Long,
)
