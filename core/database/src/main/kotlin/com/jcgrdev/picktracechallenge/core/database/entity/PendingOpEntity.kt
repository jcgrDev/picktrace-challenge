package com.jcgrdev.picktracechallenge.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jcgrdev.picktracechallenge.core.model.FailureKind

enum class OpState { QUEUED, IN_FLIGHT, FAILED }

/**
 * The FIFO outbox. Drained by `seq ASC`; `seq` is AUTOINCREMENT, so it is never reused and survives
 * edits and retries (research §R2). No foreign key: the outbox stays entity-agnostic, and pairing with
 * the entity row is enforced by the repository's transactions.
 */
@Entity(
    tableName = "pending_op",
    indices = [
        Index(value = ["op_id"], unique = true),
        Index(value = ["entity_type", "entity_id"], unique = true),
        Index(value = ["state", "seq"]),
    ],
)
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "op_id") val opId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "op_type") val opType: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    val hlc: String,
    @ColumnInfo(name = "base_version") val baseVersion: Long? = null,
    /** Encoded fields, exactly what is sent. */
    @ColumnInfo(name = "fields_json") val fieldsJson: String,
    val state: OpState,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    @ColumnInfo(name = "failure_kind") val failureKind: FailureKind? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
