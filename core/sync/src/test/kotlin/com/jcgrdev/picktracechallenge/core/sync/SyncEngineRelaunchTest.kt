package com.jcgrdev.picktracechallenge.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer.Fault
import com.jcgrdev.picktracechallenge.core.testing.Invariants
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import com.jcgrdev.picktracechallenge.core.testing.syncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** SC-005, FR-016 and the duplicate-delivery edge case, across a simulated process kill. */
@RunWith(AndroidJUnit4::class)
class SyncEngineRelaunchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Holds a push on the network thread until released, so the test can "kill" the run mid-push. */
    private class Gate : Interceptor {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            return chain.proceed(chain.request())
        }
    }

    @Test
    fun acceptedButUnacknowledgedEventsEndSyncedAndStoredOnceAfterAKill() = runTest {
        val name = "relaunch-sync-${System.nanoTime()}.db"
        val server = FakeSyncServer()
        val first = TestDatabases.fileBacked(context, name)
        val events = TestOutbox.recordMany(first, 3)

        // 1. The server stores and acks the batch, but the app never hears back.
        server.enqueueFault(Fault.DropAfterProcessing)
        assertEquals(RunResult.Retry, engineFor(first, server).run())
        assertTrue(first.statuses().values.all { it == SyncStatus.PENDING })
        assertEquals(3, server.ackedOpIds.size)

        // 2. The next run is killed mid-push: the batch is left IN_FLIGHT and the run RUNNING.
        val gate = Gate()
        val killed = launch(Dispatchers.Default) { engineFor(first, server.syncApi(gate)).run() }
        assertTrue(gate.entered.await(10, TimeUnit.SECONDS))
        killed.cancel()
        killed.join()
        gate.release.countDown()
        assertTrue(first.ops().all { it.state == OpState.IN_FLIGHT })
        assertEquals(RunOutcome.RUNNING, first.runs().last().outcome)
        first.close()

        // 3. Relaunch: a new database instance on the same file, a new engine.
        val relaunched = TestDatabases.fileBacked(context, name)
        assertEquals(RunResult.Completed, engineFor(relaunched, server).run())

        assertEquals(events.associate { it.id.toString() to SyncStatus.SYNCED }, relaunched.statuses())
        assertEquals(emptyList<Any>(), relaunched.ops())
        events.forEach { assertEquals(1L, server.storedEntity(it.id.toString())!!.version) }
        assertEquals(3, server.ackedOpIds.size)
        assertEquals(
            listOf(RunOutcome.TRANSPORT_ERROR, RunOutcome.INTERRUPTED, RunOutcome.COMPLETED),
            relaunched.runs().map { it.outcome },
        )
        assertEquals(emptyList<String>(), Invariants.violations(relaunched))
        relaunched.close()
    }
}
