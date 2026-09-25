package com.jcgrdev.picktracechallenge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single row (id = 0): HLC node id and last issued HLC. The pull cursor joins it in the pull phase. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "hlc_wall_ms") val hlcWallMs: Long,
    @ColumnInfo(name = "hlc_counter") val hlcCounter: Int,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
