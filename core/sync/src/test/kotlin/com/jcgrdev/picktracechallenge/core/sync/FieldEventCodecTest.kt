package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.model.BlockId
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.WorkerId
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.network.dto.FieldEventFields
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class FieldEventCodecTest {

    private val codec = FieldEventCodec(SyncJson)

    private val event = FieldEvent(
        id = UUID.fromString("a3f1c2d4-8b0e-4e2a-9c7f-1d6e5b3a2f01"),
        workerId = WorkerId("w_001"),
        blockId = BlockId("block_42"),
        quantity = 3,
        timestamp = Instant.parse("2025-06-10T08:32:00Z"),
        status = SyncStatus.PENDING,
    )

    @Test
    fun `encode yields exactly the four wire fields`() {
        val json = codec.encode(event)
        assertEquals(setOf("workerId", "blockId", "quantity", "timestamp"), json.keys)
        assertEquals("w_001", json.getValue("workerId").jsonPrimitive.content)
        assertEquals("block_42", json.getValue("blockId").jsonPrimitive.content)
        assertEquals(3, json.getValue("quantity").jsonPrimitive.int)
        assertEquals("2025-06-10T08:32:00Z", json.getValue("timestamp").jsonPrimitive.content)
    }

    @Test
    fun `millisecond timestamps keep ISO-8601 Z form`() {
        val json = codec.encode(event.copy(timestamp = Instant.parse("2025-06-10T08:32:00.123Z")))
        assertEquals("2025-06-10T08:32:00.123Z", json.getValue("timestamp").jsonPrimitive.content)
    }

    @Test
    fun `decode is the inverse of encode`() {
        assertEquals(
            FieldEventFields("w_001", "block_42", 3, "2025-06-10T08:32:00Z"),
            codec.decode(codec.encode(event)),
        )
    }

    @Test
    fun `the string form is what fields_json stores and parses back to the same object`() {
        val stored = codec.encodeToString(event)
        assertEquals(codec.encode(event), SyncJson.parseToJsonElement(stored))
    }
}
