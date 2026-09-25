package com.jcgrdev.picktracechallenge.core.data

import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import com.jcgrdev.picktracechallenge.core.model.Uuid7
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.sync.FieldEventCodec
import com.jcgrdev.picktracechallenge.core.sync.HlcClock
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import java.time.Clock

/** Builds the production repository over a test database, with fakes at the edges. */
internal fun repositoryFor(
    db: PicktraceDatabase,
    scheduler: FakeSyncScheduler = FakeSyncScheduler(),
    ids: IdGenerator = Uuid7(),
    clock: Clock = Clock.systemUTC(),
) = OfflineFirstFieldEventRepository(
    db = db,
    hlcClock = HlcClock(db.syncStateDao(), clock),
    codec = FieldEventCodec(SyncJson),
    ids = ids,
    syncScheduler = scheduler,
)
