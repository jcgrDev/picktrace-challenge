package com.jcgrdev.picktracechallenge.core.network.dto

import kotlinx.serialization.Serializable

/** `POST /sync/push`, at most [MAX_OPS] ops. */
@Serializable
data class PushRequest(val ops: List<PendingOpDto>) {
    companion object {
        const val MAX_OPS = 500
    }
}

@Serializable
data class RejectedOp(
    val opId: String,
    val reason: String,
    val serverVersion: Long? = null,
)

@Serializable
data class PushResponse(
    val acked: List<String>,
    val rejected: List<RejectedOp>,
)
