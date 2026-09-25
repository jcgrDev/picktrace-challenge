package com.jcgrdev.picktracechallenge.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.database.entity.SyncRunEntity
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import com.jcgrdev.picktracechallenge.core.testing.syncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** FR-020: sync activity is observable from Room, including runs killed mid-flight. */
@RunWith(AndroidJUnit4::class)
class SyncRunLogTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val server = FakeSyncServer()

    @After
    fun close() = db.close()

    @Test
    fun aRunIsRunningWhilePushingThenCompletedWithCounters() = runTest {
        TestOutbox.recordMany(db, 3)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gate = Interceptor { chain ->
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            chain.proceed(chain.request())
        }

        val run = async(Dispatchers.Default) { engineFor(db, server.syncApi(gate)).run() }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        assertEquals(RunOutcome.RUNNING, db.runs().single().outcome)
        release.countDown()
        assertEquals(RunResult.Completed, run.await())

        val finished = db.runs().single()
        assertEquals(RunOutcome.COMPLETED, finished.outcome)
        assertEquals(1, finished.batches)
        assertEquals(3, finished.acked)
        assertEquals(0, finished.rejected)
    }

    @Test
    fun aRunLeftRunningIsMarkedInterruptedWhenTheNextRunStarts() = runTest {
        db.syncRunDao().insert(SyncRunEntity(startedAtMs = 1, outcome = RunOutcome.RUNNING))

        engineFor(db, server).run()

        assertEquals(listOf(RunOutcome.INTERRUPTED, RunOutcome.COMPLETED), db.runs().map { it.outcome })
        assertTrue(db.runs().first().finishedAtMs != null)
    }

    @Test
    fun onlyTheLatestTwentyRunsAreKept() = runTest {
        repeat(25) { engineFor(db, server).run() }
        val runs = db.runs()
        assertEquals(20, runs.size)
        assertEquals((6L..25L).toList(), runs.map { it.id })
    }
}
