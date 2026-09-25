package com.jcgrdev.picktracechallenge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jcgrdev.picktracechallenge.core.database.entity.SyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRunDao {

    @Insert
    suspend fun insert(run: SyncRunEntity): Long

    @Update
    suspend fun update(run: SyncRunEntity)

    @Query("SELECT * FROM sync_run WHERE id = :id")
    suspend fun getById(id: Long): SyncRunEntity

    /** A run still RUNNING when a new one starts was killed. */
    @Query("UPDATE sync_run SET outcome = 'INTERRUPTED', finished_at_ms = :nowMs WHERE outcome = 'RUNNING'")
    suspend fun markRunningAsInterrupted(nowMs: Long): Int

    @Query("DELETE FROM sync_run WHERE id NOT IN (SELECT id FROM sync_run ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("SELECT * FROM sync_run ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<SyncRunEntity?>

    @Query("SELECT * FROM sync_run ORDER BY id")
    suspend fun getAll(): List<SyncRunEntity>
}
