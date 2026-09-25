package com.jcgrdev.picktracechallenge.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SyncConfig(
    val batchSize: Int = 50,
    val maxTransportAttempts: Int = 5,
    val defaultRetryAfter: Duration = 30.seconds,
) {
    /** Clamped to the contract's push limit of 500 ops. */
    val effectiveBatchSize: Int get() = batchSize.coerceIn(1, MAX_PUSH_OPS)

    private companion object {
        const val MAX_PUSH_OPS = 500
    }
}
