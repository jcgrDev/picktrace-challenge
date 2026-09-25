package com.jcgrdev.picktracechallenge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jcgrdev.picktracechallenge.core.model.SyncStatus

@Entity(tableName = "field_event", indices = [Index("status")])
data class FieldEventEntity(
    /** UUIDv7 string. `ORDER BY id DESC` is recording order for display. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "block_id") val blockId: String,
    val quantity: Int,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    val status: SyncStatus,
)
