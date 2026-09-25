package com.jcgrdev.picktracechallenge.core.sync

import kotlin.time.Duration

/** What one engine run asks of its caller (the worker maps it to a WorkManager `Result`). */
sealed interface RunResult {
    /** The outbox was drained as far as possible; nothing to retry. */
    data object Completed : RunResult

    /** A transport problem stopped the run; retry with backoff. */
    data object Retry : RunResult

    /** The server asked us to wait (429 + Retry-After). */
    data class RetryAfter(val delay: Duration) : RunResult

    /** The server refused a batch with a non-retryable 4xx; those events are FAILED. */
    data object Refused : RunResult
}
