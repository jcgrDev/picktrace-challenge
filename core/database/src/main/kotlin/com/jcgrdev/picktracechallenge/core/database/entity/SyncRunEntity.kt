package com.jcgrdev.picktracechallenge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RunOutcome { RUNNING, COMPLETED, TRANSPORT_ERROR, RATE_LIMITED, REFUSED, INTERRUPTED }

/** FR-020 log of sync runs; the engine keeps the latest 20 rows. */
@Entity(tableName = "sync_run")
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "finished_at_ms") val finishedAtMs: Long? = null,
    val outcome: RunOutcome,
    val batches: Int = 0,
    val acked: Int = 0,
    val rejected: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
