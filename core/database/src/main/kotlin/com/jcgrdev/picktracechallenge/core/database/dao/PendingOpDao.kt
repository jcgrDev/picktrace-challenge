package com.jcgrdev.picktracechallenge.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.model.FailureKind

@Dao
interface PendingOpDao {

    /** Returns the assigned `seq`. */
    @Insert
    suspend fun insert(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_op WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun getByEntityId(entityType: String, entityId: String): PendingOpEntity?

    @Query("SELECT COUNT(*) FROM pending_op")
    suspend fun count(): Int

    @Query("SELECT * FROM pending_op ORDER BY seq")
    suspend fun getAll(): List<PendingOpEntity>

    /** Undo the marking left by a run that died mid-batch. Safe because only one run exists at a time. */
    @Query("UPDATE pending_op SET state = 'QUEUED' WHERE state = 'IN_FLIGHT'")
    suspend fun resetInFlight(): Int

    /** The next batch in delivery order; [afterSeq] keeps a run from re-claiming ops it already pushed. */
    @Query("SELECT * FROM pending_op WHERE state = 'QUEUED' AND seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun queuedBatch(afterSeq: Long, limit: Int): List<PendingOpEntity>

    @Query("UPDATE pending_op SET state = :state WHERE seq IN (:seqs)")
    suspend fun setState(seqs: List<Long>, state: OpState)

    @Query("SELECT * FROM pending_op WHERE op_id IN (:opIds)")
    suspend fun getByOpIds(opIds: Collection<String>): List<PendingOpEntity>

    @Query("DELETE FROM pending_op WHERE op_id IN (:opIds)")
    suspend fun deleteByOpIds(opIds: Collection<String>)

    /** A transport failure costs each op one attempt and puts it back in the queue. */
    @Query("UPDATE pending_op SET attempts = attempts + 1, last_error = :error, state = 'QUEUED' WHERE seq IN (:seqs)")
    suspend fun recordTransportFailure(seqs: List<Long>, error: String)

    @Query("UPDATE pending_op SET state = 'FAILED', failure_kind = :kind, last_error = :error WHERE seq = :seq")
    suspend fun markFailed(seq: Long, kind: FailureKind, error: String)

    /** An edit before sync rewrites the CREATE op in place: same op_id, same seq (CLAUDE.md "Edits before sync"). */
    @Query("UPDATE pending_op SET fields_json = :fieldsJson, hlc = :hlc WHERE seq = :seq")
    suspend fun rewriteFields(seq: Long, fieldsJson: String, hlc: String)

    @Query("DELETE FROM pending_op WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun deleteByEntityId(entityType: String, entityId: String)
}
