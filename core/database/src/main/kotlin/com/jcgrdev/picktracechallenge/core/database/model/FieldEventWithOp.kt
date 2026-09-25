package com.jcgrdev.picktracechallenge.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity

/** An event and its outbox row, if any (none once synced). Observing it watches both tables. */
data class FieldEventWithOp(
    @Embedded val event: FieldEventEntity,
    @Relation(parentColumn = "id", entityColumn = "entity_id")
    val op: PendingOpEntity?,
)
