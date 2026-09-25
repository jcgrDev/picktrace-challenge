package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.database.entity.SyncRunEntity
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.network.SyncApi
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.syncApi
import kotlinx.coroutines.flow.first
import java.time.Clock

internal fun engineFor(
    db: PicktraceDatabase,
    api: SyncApi,
    config: SyncConfig = SyncConfig(),
    clock: Clock = Clock.systemUTC(),
) = SyncEngine(db, api, SyncJson, PushOutcomeClassifier(config), config, clock)

internal fun engineFor(db: PicktraceDatabase, server: FakeSyncServer, config: SyncConfig = SyncConfig()) =
    engineFor(db, server.syncApi(), config)

internal suspend fun PicktraceDatabase.statuses(): Map<String, SyncStatus> =
    fieldEventDao().observeEvents(null).first().associate { it.id to it.status }

internal suspend fun PicktraceDatabase.ops(): List<PendingOpEntity> = pendingOpDao().getAll()

internal suspend fun PicktraceDatabase.runs(): List<SyncRunEntity> = syncRunDao().getAll()
