package com.jcgrdev.picktracechallenge.core.testing.fixtures

import com.jcgrdev.picktracechallenge.core.model.FieldEventDraft
import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import java.time.Instant
import java.util.UUID

/** The spec's sample event. */
fun sampleDraft(
    workerId: String = "w_001",
    blockId: String = "block_42",
    quantity: String = "3",
    timestamp: Instant = Instant.parse("2025-06-10T08:32:00Z"),
) = FieldEventDraft(workerId, blockId, quantity, timestamp)

/** Deterministic ids: 00000000-0000-0000-0000-000000000001, …02, … */
class SequentialIdGenerator(start: Long = 1) : IdGenerator {
    private var next = start

    @Synchronized
    override fun next(): UUID = UUID(0, next++)
}
