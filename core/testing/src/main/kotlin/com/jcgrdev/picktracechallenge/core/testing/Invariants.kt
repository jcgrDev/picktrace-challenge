package com.jcgrdev.picktracechallenge.core.testing

import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** data-model.md "Invariants" 1–5, checked with raw SQL. Returns the violated ones (empty = all hold). */
object Invariants {

    private val checks = mapOf(
        "1-3: event status matches its op row" to """
            SELECT COUNT(*) FROM field_event e LEFT JOIN pending_op o ON o.entity_id = e.id
            WHERE (e.status = 'PENDING' AND (o.seq IS NULL OR o.state NOT IN ('QUEUED', 'IN_FLIGHT')))
               OR (e.status = 'FAILED' AND (o.seq IS NULL OR o.state != 'FAILED' OR o.failure_kind IS NULL OR o.last_error IS NULL))
               OR (e.status = 'SYNCED' AND o.seq IS NOT NULL)
        """,
        "4: every op references an event" to """
            SELECT COUNT(*) FROM pending_op o LEFT JOIN field_event e ON e.id = o.entity_id WHERE e.id IS NULL
        """,
        "5: IN_FLIGHT only while a run is RUNNING" to """
            SELECT COUNT(*) FROM pending_op WHERE state = 'IN_FLIGHT'
              AND NOT EXISTS (SELECT 1 FROM sync_run WHERE outcome = 'RUNNING')
        """,
    )

    suspend fun violations(db: PicktraceDatabase): List<String> = withContext(Dispatchers.IO) {
        checks.mapNotNull { (name, sql) ->
            val count = db.query(sql.trimIndent(), null).use { it.moveToFirst(); it.getInt(0) }
            if (count == 0) null else "$name ($count rows)"
        }
    }
}
