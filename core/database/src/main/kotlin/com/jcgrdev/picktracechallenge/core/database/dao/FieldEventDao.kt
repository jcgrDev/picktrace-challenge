package com.jcgrdev.picktracechallenge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.database.model.FieldEventWithOp
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldEventDao {

    @Insert
    suspend fun insert(event: FieldEventEntity)

    @Query("SELECT * FROM field_event WHERE id = :id")
    suspend fun getById(id: String): FieldEventEntity?

    /** Newest first (UUIDv7 = recording order). A null [status] means every status. */
    @Query("SELECT * FROM field_event WHERE :status IS NULL OR status = :status ORDER BY id DESC")
    fun observeEvents(status: SyncStatus?): Flow<List<FieldEventEntity>>

    @Query("UPDATE field_event SET status = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: Collection<String>, status: SyncStatus)

    @Transaction
    @Query("SELECT * FROM field_event WHERE id = :id")
    fun observeDetail(id: String): Flow<FieldEventWithOp?>

    @Transaction
    @Query("SELECT * FROM field_event WHERE id = :id")
    suspend fun getDetail(id: String): FieldEventWithOp?

    @Query("UPDATE field_event SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Int)

    @Query("DELETE FROM field_event WHERE id = :id")
    suspend fun deleteById(id: String)
}
