package com.jcgrdev.picktracechallenge.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.model.FailureKind
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.Invariants
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val server = FakeSyncServer()
    private val engine = engineFor(db, server)

    @After
    fun close() = db.close()

    @Test
    fun ackedEventsBecomeSyncedAndTheirOpsAreDeletedTogether() = runTest {
        val events = TestOutbox.recordMany(db, 5)
        // Sample the invariants on committed states while the run commits. Room coalesces
        // invalidations, so this sees a subset of commits, never a state between two commits.
        val seen = mutableListOf<List<String>>()
        val subscribed = CompletableDeferred<Unit>()
        val watcher = launch(Dispatchers.Default) {
            db.invalidationTracker.createFlow("field_event", "pending_op")
                .map { Invariants.violations(db) }
                .collect {
                    synchronized(seen) { seen += it }
                    subscribed.complete(Unit)
                }
        }
        subscribed.await()

        val result = withContext(Dispatchers.Default) {
            val result = engine.run()
            // Real-time wait for at least one emission caused by the run's commits.
            withTimeout(5.seconds) { while (synchronized(seen) { seen.size } < 2) delay(10) }
            result
        }
        watcher.cancel()

        assertEquals(RunResult.Completed, result)
        assertEquals(events.associate { it.id.toString() to SyncStatus.SYNCED }, db.statuses())
        assertEquals(emptyList<Any>(), db.ops())
        assertEquals(events.size, server.ackedOpIds.size)
        synchronized(seen) {
            assertTrue("watcher observed ${seen.size} states", seen.size >= 2)
            assertTrue("violations seen: $seen", seen.all { it.isEmpty() })
        }
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun rejectedEventsBecomeFailedWithTheServerReason() = runTest {
        TestOutbox.record(db, quantity = 3)
        val rejected = TestOutbox.record(db, quantity = 9_999)
        server.rejectWhen("quantity exceeds limit") { it.fields!!.getValue("quantity").jsonPrimitive.int > 1_000 }

        val result = engine.run()

        assertEquals(RunResult.Completed, result)
        assertEquals(SyncStatus.FAILED, db.statuses().getValue(rejected.id.toString()))
        val op = db.ops().single()
        assertEquals(rejected.id.toString(), op.entityId)
        assertEquals(OpState.FAILED, op.state)
        assertEquals(FailureKind.REJECTED, op.failureKind)
        assertEquals("quantity exceeds limit", op.lastError)
        assertEquals(0, op.attempts)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun emptyOutboxCompletesWithoutPushing() = runTest {
        assertEquals(RunResult.Completed, engine.run())
        assertEquals(emptyList<List<String>>(), server.pushes)
        val run = db.runs().single()
        assertEquals(RunOutcome.COMPLETED, run.outcome)
        assertEquals(0, run.batches)
    }

    @Test
    fun aCompletedRunIsLoggedWithCounters() = runTest {
        TestOutbox.recordMany(db, 3)
        server.rejectWhen("no") { it.fields!!.getValue("quantity").jsonPrimitive.int == 2 }

        engine.run()

        val run = db.runs().single()
        assertEquals(RunOutcome.COMPLETED, run.outcome)
        assertEquals(1, run.batches)
        assertEquals(2, run.acked)
        assertEquals(1, run.rejected)
        assertTrue(run.finishedAtMs != null && run.finishedAtMs!! >= run.startedAtMs)
    }
}
