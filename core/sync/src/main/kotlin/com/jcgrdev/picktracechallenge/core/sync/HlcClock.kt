package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.database.dao.SyncStateDao
import com.jcgrdev.picktracechallenge.core.model.Hlc
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed hybrid logical clock (research §R7). The last issued value lives in `sync_state`, so
 * ticks keep increasing across process death even if the wall clock moves backwards.
 */
@Singleton
class HlcClock @Inject constructor(
    private val syncStateDao: SyncStateDao,
    private val clock: Clock,
) {
    /**
     * Issues the next HLC. MUST be called inside the caller's `withTransaction`, so the persisted
     * clock and the op it stamps commit together (Principle II).
     */
    suspend fun tick(): Hlc {
        val state = syncStateDao.get()
        val next = Hlc.tick(Hlc(state.hlcWallMs, state.hlcCounter, state.nodeId), clock.millis())
        syncStateDao.update(state.copy(hlcWallMs = next.wallMs, hlcCounter = next.counter))
        return next
    }
}
