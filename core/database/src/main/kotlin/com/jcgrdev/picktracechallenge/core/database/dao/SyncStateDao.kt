package com.jcgrdev.picktracechallenge.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.jcgrdev.picktracechallenge.core.database.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    /** The row is seeded by the database's onCreate callback, so it always exists. */
    @Query("SELECT * FROM sync_state WHERE id = ${SyncStateEntity.SINGLETON_ID}")
    suspend fun get(): SyncStateEntity

    @Update
    suspend fun update(state: SyncStateEntity)
}
