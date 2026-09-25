package com.jcgrdev.picktracechallenge.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer.Fault
import com.jcgrdev.picktracechallenge.core.testing.Invariants
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** US3: large backlogs go out in consecutive batches, each event with its own outcome. */
@RunWith(AndroidJUnit4::class)
class SyncEngineBatchTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val server = FakeSyncServer()

    @After
    fun close() = db.close()

    private suspend fun opIdsInSeqOrder() = db.ops().map { it.opId }

    @Test
    fun twoHundredFiftyEventsGoOutAsFivePushesOfFiftyInOrder() = runTest {
        TestOutbox.recordMany(db, 250)
        val expectedOrder = opIdsInSeqOrder()

        assertEquals(RunResult.Completed, engineFor(db, server).run())

        assertEquals(List(5) { 50 }, server.pushes.map { it.size })
        assertEquals(expectedOrder.chunked(50), server.pushes)
        assertTrue(db.statuses().values.all { it == SyncStatus.SYNCED })
        assertEquals(5, db.runs().single().batches)
        assertEquals(250, db.runs().single().acked)
    }

    @Test
    fun partialRejectionInsideABatchResolvesEachEventIndependently() = runTest {
        // quantity = index + 1; reject 3 of the first 50.
        val events = TestOutbox.recordMany(db, 50)
        val rejectedQuantities = setOf(7, 21, 42)
        server.rejectWhen("rejected by rule") { it.fields!!.getValue("quantity").jsonPrimitive.int in rejectedQuantities }

        engineFor(db, server).run()

        val statuses = db.statuses()
        assertEquals(47, statuses.values.count { it == SyncStatus.SYNCED })
        val failed = events.filter { statuses.getValue(it.id.toString()) == SyncStatus.FAILED }
        assertEquals(rejectedQuantities, failed.map { it.quantity }.toSet())
        assertEquals(1, server.pushes.size)
        assertEquals(3, db.runs().single().rejected)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun aBatchThatCannotBeDeliveredLeavesItAndLaterBatchesPending() = runTest {
        val events = TestOutbox.recordMany(db, 250)
        repeat(2) { server.enqueueFault(Fault.Pass) }
        server.enqueueFault(Fault.DropConnection)

        assertEquals(RunResult.Retry, engineFor(db, server).run())

        val statuses = db.statuses()
        val ids = events.map { it.id.toString() }
        assertTrue(ids.take(100).all { statuses.getValue(it) == SyncStatus.SYNCED })
        assertTrue(ids.drop(100).all { statuses.getValue(it) == SyncStatus.PENDING })
        assertEquals(3, server.pushes.size)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun batchSizeIsConfigurable() = runTest {
        TestOutbox.recordMany(db, 250)
        engineFor(db, server, SyncConfig(batchSize = 10)).run()
        assertEquals(List(25) { 10 }, server.pushes.map { it.size })
    }

    @Test
    fun batchSizeIsClampedToTheContractLimitOf500() = runTest {
        TestOutbox.recordMany(db, 600)
        engineFor(db, server, SyncConfig(batchSize = 10_000)).run()
        assertEquals(listOf(500, 100), server.pushes.map { it.size })
        assertTrue(db.statuses().values.all { it == SyncStatus.SYNCED })
    }
}
