package com.jcgrdev.picktracechallenge.core.network

import com.jcgrdev.picktracechallenge.core.network.dto.ChangeDto
import com.jcgrdev.picktracechallenge.core.network.dto.EntityType
import com.jcgrdev.picktracechallenge.core.network.dto.FieldEventFields
import com.jcgrdev.picktracechallenge.core.network.dto.OpType
import com.jcgrdev.picktracechallenge.core.network.dto.PendingOpDto
import com.jcgrdev.picktracechallenge.core.network.dto.PullResponse
import com.jcgrdev.picktracechallenge.core.network.dto.PushRequest
import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import com.jcgrdev.picktracechallenge.core.network.dto.RejectedOp
import com.jcgrdev.picktracechallenge.core.network.dto.SnapshotResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Examples are the ones in specs/001-field-event-sync/contracts/sync-api.md. */
class SyncDtoSerializationTest {

    private val json = SyncJson

    private val pushRequestJson = """
        { "ops": [ {
          "opId": "0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f60",
          "entityType": "FIELD_EVENT",
          "entityId": "0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f5f",
          "opType": "CREATE",
          "schemaVersion": 1,
          "hlc": "0000001757800000000-0000-3f9a1c0e7b2d4a61",
          "fields": { "workerId": "w_001", "blockId": "block_42", "quantity": 3, "timestamp": "2025-06-10T08:32:00Z" }
        } ] }
    """.trimIndent()

    @Test
    fun `push request example decodes and re-encodes equal`() {
        val request = json.decodeFromString<PushRequest>(pushRequestJson)
        val op = request.ops.single()
        assertEquals(OpType.CREATE, op.opType)
        assertEquals(EntityType.FIELD_EVENT, op.entityType)
        assertEquals(
            FieldEventFields("w_001", "block_42", 3, "2025-06-10T08:32:00Z"),
            json.decodeFromJsonElement(FieldEventFields.serializer(), op.fields!!),
        )
        assertEquals(json.parseToJsonElement(pushRequestJson), json.encodeToJsonElement(request))
    }

    @Test
    fun `push response example decodes and re-encodes equal`() {
        val body = """{ "acked": ["0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f60"], "rejected": [ { "opId": "x", "reason": "bad", "serverVersion": 4 } ] }"""
        val response = json.decodeFromString<PushResponse>(body)
        assertEquals(listOf("0199a1b2-7c3d-7e4f-8a90-1b2c3d4e5f60"), response.acked)
        assertEquals(RejectedOp("x", "bad", 4), response.rejected.single())
        assertEquals(json.parseToJsonElement(body), json.encodeToJsonElement(response))
    }

    @Test
    fun `null baseVersion and fields are omitted on the wire`() {
        val op = PendingOpDto(
            opId = "a", entityType = EntityType.FIELD_EVENT, entityId = "b", opType = OpType.DELETE,
            schemaVersion = 1, hlc = "h",
        )
        val encoded = json.encodeToJsonElement(op).jsonObject
        assertFalse("baseVersion" in encoded)
        assertFalse("fields" in encoded)
    }

    @Test
    fun `unknown keys are ignored`() {
        val body = """{ "acked": [], "rejected": [], "serverTime": 123, "extra": { "a": 1 } }"""
        assertEquals(PushResponse(emptyList(), emptyList()), json.decodeFromString<PushResponse>(body))
    }

    @Test
    fun `pull and snapshot responses round-trip`() {
        val change = ChangeDto(
            entityType = EntityType.FIELD_EVENT, entityId = "e1", version = 3, hlc = "h",
            fields = json.encodeToJsonElement(FieldEventFields("w_001", "block_42", 3, "2025-06-10T08:32:00Z")) as JsonObject,
        )
        val tombstone = ChangeDto(EntityType.FIELD_EVENT, "e2", 9, "h2", deleted = true)
        val pull = PullResponse(listOf(change, tombstone), nextCursor = 42, hasMore = true)
        assertEquals(pull, json.decodeFromString<PullResponse>(json.encodeToString(pull)))

        val snapshot = SnapshotResponse(listOf(change), page = 0, hasMore = false, cursor = 42)
        assertEquals(snapshot, json.decodeFromString<SnapshotResponse>(json.encodeToString(snapshot)))
    }
}
