package com.jcgrdev.picktracechallenge.core.data

import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.sync.PushOutcomeClassifier
import com.jcgrdev.picktracechallenge.core.sync.RunResult
import com.jcgrdev.picktracechallenge.core.sync.SyncConfig
import com.jcgrdev.picktracechallenge.core.sync.SyncEngine
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.syncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/** Runs one real sync against [server], so repository tests can reach SYNCED and FAILED honestly. */
internal suspend fun PicktraceDatabase.syncWith(server: FakeSyncServer): RunResult =
    SyncEngine(this, server.syncApi(), SyncJson, PushOutcomeClassifier(SyncConfig()), SyncConfig(), Clock.systemUTC()).run()

/** Every row of both tables, for "the refusal changed nothing" assertions. */
internal suspend fun PicktraceDatabase.snapshot(): List<String> = withContext(Dispatchers.IO) {
    listOf("field_event", "pending_op").flatMap { table ->
        query("SELECT * FROM $table ORDER BY 1", null).use { c ->
            buildList {
                while (c.moveToNext()) add("$table:" + (0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" })
            }
        }
    }
}
