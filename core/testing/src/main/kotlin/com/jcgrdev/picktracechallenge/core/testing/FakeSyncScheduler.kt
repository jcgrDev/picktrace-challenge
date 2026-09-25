package com.jcgrdev.picktracechallenge.core.testing

import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import kotlin.time.Duration

/** Records every sync request instead of enqueuing work. */
class FakeSyncScheduler : SyncScheduler {

    data class Request(val expedited: Boolean = false, val delay: Duration = Duration.ZERO)

    private val _requests = mutableListOf<Request>()
    val requests: List<Request> get() = synchronized(_requests) { _requests.toList() }

    override fun requestSync(expedited: Boolean, delay: Duration) {
        synchronized(_requests) { _requests += Request(expedited, delay) }
    }
}
