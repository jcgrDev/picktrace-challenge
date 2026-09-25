package com.jcgrdev.picktracechallenge.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.model.FailureKind
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer.Fault
import com.jcgrdev.picktracechallenge.core.testing.Invariants
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** US2-5, FR-014, FR-017, FR-019, and the invalid-response edge case. Batches of 2 over 6 events. */
@RunWith(AndroidJUnit4::class)
class SyncEngineTransportTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val server = FakeSyncServer()
    private val engine = engineFor(db, server, SyncConfig(batchSize = 2))

    @After
    fun close() = db.close()

    private suspend fun seed(): List<FieldEvent> = TestOutbox.recordMany(db, 6)

    private suspend fun assertSecondBatchFailedInTransport(events: List<FieldEvent>, result: RunResult) {
        assertEquals(RunResult.Retry, result)
        val statuses = db.statuses()
        val ids = events.map { it.id.toString() }
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.SYNCED), ids.take(2).map(statuses::getValue))
        assertEquals(List(4) { SyncStatus.PENDING }, ids.drop(2).map(statuses::getValue))
        val attempts = db.ops().associate { it.entityId to it.attempts }
        assertEquals(listOf(1, 1, 0, 0), ids.drop(2).map(attempts::getValue))
        assertTrue(db.ops().all { it.state == OpState.QUEUED })
        assertEquals(RunOutcome.TRANSPORT_ERROR, db.runs().single().outcome)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun droppedConnectionOnTheSecondBatchKeepsTheRestPending() = runTest {
        val events = seed()
        server.enqueueFault(Fault.Pass)
        server.enqueueFault(Fault.DropConnection)
        assertSecondBatchFailedInTransport(events, engine.run())
    }

    @Test
    fun serviceUnavailableBehavesLikeADroppedConnection() = runTest {
        val events = seed()
        server.enqueueFault(Fault.Pass)
        server.enqueueFault(Fault.Status(503))
        assertSecondBatchFailedInTransport(events, engine.run())
        assertEquals("HTTP 503", db.ops().first().lastError)
    }

    @Test
    fun garbageBodyBehavesLikeADroppedConnection() = runTest {
        val events = seed()
        server.enqueueFault(Fault.Pass)
        server.enqueueFault(Fault.GarbageBody)
        assertSecondBatchFailedInTransport(events, engine.run())
    }

    @Test
    fun tooManyRequestsStopsTheRunAndAsksToRetryAfterTheServerDelay() = runTest {
        seed()
        server.enqueueFault(Fault.Status(429, retryAfter = "7"))

        assertEquals(RunResult.RetryAfter(7.seconds), engine.run())
        val attempts = db.ops().map { it.attempts }
        assertEquals(listOf(1, 1, 0, 0, 0, 0), attempts)
        assertEquals(RunOutcome.RATE_LIMITED, db.runs().single().outcome)
    }

    @Test
    fun opsMissingFromTheResponseStayPendingAndCostOneAttempt() = runTest {
        val events = seed()
        val omitted = db.ops().first { it.entityId == events[1].id.toString() }.opId
        server.enqueueFault(Fault.PartialBody(setOf(omitted)))

        val result = engine.run()

        assertEquals(RunResult.Retry, result)
        val statuses = db.statuses()
        assertEquals(SyncStatus.PENDING, statuses.getValue(events[1].id.toString()))
        assertEquals(events.size - 1, statuses.values.count { it == SyncStatus.SYNCED })
        val op = db.ops().single()
        assertEquals(1, op.attempts)
        assertEquals(OpState.QUEUED, op.state)
        // Not re-pushed within the same run.
        assertEquals(1, server.deliveryLog.count { it.opId == omitted })
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun nonRetryableClientErrorFailsTheWholeBatchAsRefused() = runTest {
        val events = seed()
        server.enqueueFault(Fault.Pass)
        server.enqueueFault(Fault.Status(400))

        assertEquals(RunResult.Refused, engine.run())
        val statuses = db.statuses()
        val ids = events.map { it.id.toString() }
        assertEquals(listOf(SyncStatus.SYNCED, SyncStatus.SYNCED, SyncStatus.FAILED, SyncStatus.FAILED,
            SyncStatus.PENDING, SyncStatus.PENDING), ids.map(statuses::getValue))
        val refused = db.ops().filter { it.state == OpState.FAILED }
        assertEquals(2, refused.size)
        assertTrue(refused.all { it.failureKind == FailureKind.REFUSED && it.lastError == "HTTP 400" })
        assertEquals(RunOutcome.REFUSED, db.runs().single().outcome)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun fiveTransportFailuresAcrossRunsExhaustTheEvent() = runTest {
        val event = TestOutbox.record(db)
        repeat(4) {
            server.enqueueFault(Fault.Status(503))
            assertEquals(RunResult.Retry, engine.run())
            assertEquals(SyncStatus.PENDING, db.statuses().getValue(event.id.toString()))
        }
        server.enqueueFault(Fault.Status(503))
        engine.run()

        assertEquals(SyncStatus.FAILED, db.statuses().getValue(event.id.toString()))
        val op = db.ops().single()
        assertEquals(5, op.attempts)
        assertEquals(FailureKind.EXHAUSTED, op.failureKind)
        assertTrue(op.lastError!!, op.lastError!!.startsWith("Could not reach server after 5 attempts"))
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }
}
