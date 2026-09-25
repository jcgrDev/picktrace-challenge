package com.jcgrdev.picktracechallenge.core.testing

import com.jcgrdev.picktracechallenge.core.network.SyncApi
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.network.dto.EntityType
import com.jcgrdev.picktracechallenge.core.network.dto.FieldEventFields
import com.jcgrdev.picktracechallenge.core.network.dto.OpType
import com.jcgrdev.picktracechallenge.core.network.dto.PendingOpDto
import com.jcgrdev.picktracechallenge.core.network.dto.PushRequest
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer.Fault
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeSyncServerTest {

    private val server = FakeSyncServer()
    private val api: SyncApi = server.syncApi()

    private fun op(n: Int, quantity: Int = 3, opId: String = "op-$n") = PendingOpDto(
        opId = opId,
        entityType = EntityType.FIELD_EVENT,
        entityId = "e-$n",
        opType = OpType.CREATE,
        schemaVersion = 1,
        hlc = "%019d-0000-0000000000000000".format(n),
        fields = SyncJson.encodeToJsonElement(FieldEventFields("w_001", "block_42", quantity, "2025-06-10T08:32:00Z")) as JsonObject,
    )

    @Test
    fun ackStoresTheEntity() = runTest {
        val response = api.push(PushRequest(listOf(op(1), op(2))))
        assertEquals(200, response.code())
        assertEquals(listOf("op-1", "op-2"), response.body()!!.acked)
        assertEquals(1L, server.storedEntity("e-1")!!.version)
        assertEquals(setOf("op-1", "op-2"), server.ackedOpIds)
    }

    @Test
    fun repeatedOpIdIsAckedAgainWithoutASecondStore() = runTest {
        api.push(PushRequest(listOf(op(1))))
        val again = api.push(PushRequest(listOf(op(1))))
        assertEquals(listOf("op-1"), again.body()!!.acked)
        assertEquals(1L, server.storedEntity("e-1")!!.version)
        assertEquals(2, server.deliveryLog.size)
    }

    @Test
    fun rejectRuleRejectsWithItsReason() = runTest {
        server.rejectWhen("too many") { it.opId == "op-2" }
        val body = api.push(PushRequest(listOf(op(1), op(2)))).body()!!
        assertEquals(listOf("op-1"), body.acked)
        assertEquals("op-2", body.rejected.single().opId)
        assertEquals("too many", body.rejected.single().reason)
        assertNull(server.storedEntity("e-2"))
    }

    @Test
    fun moreThan500OpsIsA400() = runTest {
        val response = api.push(PushRequest(List(501) { op(it) }))
        assertEquals(400, response.code())
        assertTrue(server.ackedOpIds.isEmpty())
    }

    @Test
    fun badJsonIsA400() {
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        val request = Request.Builder().url("https://fake.sync.local/sync/push").post("{ not json".toRequestBody()).build()
        client.newCall(request).execute().use { assertEquals(400, it.code) }
    }

    @Test
    fun unknownPathIsA404() {
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        client.newCall(Request.Builder().url("https://fake.sync.local/other").build()).execute().use {
            assertEquals(404, it.code)
        }
    }

    @Test
    fun faultsAreConsumedFifoOnePerPush() = runTest {
        server.enqueueFault(Fault.Pass)
        server.enqueueFault(Fault.Status(503))
        assertEquals(200, api.push(PushRequest(listOf(op(1)))).code())
        assertEquals(503, api.push(PushRequest(listOf(op(2)))).code())
        assertEquals(200, api.push(PushRequest(listOf(op(3)))).code())
        assertEquals(listOf(listOf("op-1"), listOf("op-2"), listOf("op-3")), server.pushes)
    }

    @Test
    fun dropConnectionThrowsBeforeProcessing() = runTest {
        server.enqueueFault(Fault.DropConnection)
        val failure = runCatching { api.push(PushRequest(listOf(op(1)))) }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
        assertTrue(server.ackedOpIds.isEmpty())
    }

    @Test
    fun dropAfterProcessingStoresThenThrows() = runTest {
        server.enqueueFault(Fault.DropAfterProcessing)
        val failure = runCatching { api.push(PushRequest(listOf(op(1)))) }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
        assertEquals(setOf("op-1"), server.ackedOpIds)
    }

    @Test
    fun statusFaultCarriesRetryAfter() = runTest {
        server.enqueueFault(Fault.Status(429, retryAfter = "7"))
        val response = api.push(PushRequest(listOf(op(1))))
        assertEquals(429, response.code())
        assertEquals("7", response.headers()["Retry-After"])
        assertTrue(server.ackedOpIds.isEmpty())
    }

    @Test
    fun garbageBodyIsA200ThatDoesNotParse() = runTest {
        server.enqueueFault(Fault.GarbageBody)
        val failure = runCatching { api.push(PushRequest(listOf(op(1)))) }.exceptionOrNull()
        assertTrue("$failure", failure != null && failure !is IOException)
    }

    @Test
    fun partialBodyOmitsOpsFromBothLists() = runTest {
        server.enqueueFault(Fault.PartialBody(setOf("op-2")))
        val body = api.push(PushRequest(listOf(op(1), op(2)))).body()!!
        assertEquals(listOf("op-1"), body.acked)
        assertTrue(body.rejected.isEmpty())
    }

    @Test
    fun pullReturnsChangesAfterTheCursor() = runTest {
        api.push(PushRequest(listOf(op(1), op(2), op(3))))
        val first = api.pull(cursor = 0, limit = 2).body()!!
        assertEquals(listOf("e-1", "e-2"), first.changes.map { it.entityId })
        assertTrue(first.hasMore)
        val rest = api.pull(cursor = first.nextCursor, limit = 2).body()!!
        assertEquals(listOf("e-3"), rest.changes.map { it.entityId })
        assertEquals(false, rest.hasMore)
    }

    @Test
    fun expiredCursorIsA410() = runTest {
        server.expiredBefore = 5
        assertEquals(410, api.pull(cursor = 1, limit = 10).code())
    }

    @Test
    fun snapshotPagesCarryTheCurrentCursor() = runTest {
        api.push(PushRequest(List(150) { op(it) }))
        val first = api.snapshot(page = 0).body()!!
        assertEquals(100, first.entities.size)
        assertTrue(first.hasMore)
        val last = api.snapshot(page = 1).body()!!
        assertEquals(50, last.entities.size)
        assertEquals(false, last.hasMore)
        assertEquals(150L, last.cursor)
    }
}
