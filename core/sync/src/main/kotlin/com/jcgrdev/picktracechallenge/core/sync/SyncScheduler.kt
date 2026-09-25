package com.jcgrdev.picktracechallenge.core.sync

import kotlin.time.Duration

/**
 * The single way to ask for a sync run. The WorkManager implementation enqueues unique work
 * "field-event-sync" with APPEND_OR_REPLACE and NetworkType.CONNECTED, so runs never overlap.
 */
interface SyncScheduler {
    fun requestSync(expedited: Boolean = false, delay: Duration = Duration.ZERO)
}
