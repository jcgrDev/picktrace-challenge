package com.jcgrdev.picktracechallenge.core.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.sync.engineFor
import com.jcgrdev.picktracechallenge.core.sync.statuses
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** FR-010, FR-015, US2-1, US2-6: unique work, network constraint, append-not-parallel. */
@RunWith(AndroidJUnit4::class)
class SyncSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: PicktraceDatabase
    private val server = FakeSyncServer()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        db = TestDatabases.inMemory(context)
        val runner = engineFor(db, server)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                SyncWorker(appContext, params, runner, FakeSyncScheduler())
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(context)
    }

    @After
    fun tearDown() = db.close()

    private fun chain(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(SYNC_WORK_NAME).get()

    private fun driver() = WorkManagerTestInitHelper.getTestDriver(context)!!

    private suspend fun awaitFinished(id: UUID) = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) {
            while (!workManager.getWorkInfoById(id).get()!!.state.isFinished) delay(10)
        }
    }

    @Test
    fun requestEnqueuesUniqueWorkThatWaitsForANetwork() = runTest {
        scheduler.requestSync()

        val info = chain().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun theRunHappensOnceTheNetworkConstraintIsMet() = runTest {
        TestOutbox.recordMany(db, 3)
        scheduler.requestSync()
        val id = chain().single().id
        assertTrue(server.pushes.isEmpty())

        driver().setAllConstraintsMet(id)
        awaitFinished(id)

        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(id).get()!!.state)
        assertTrue(db.statuses().values.all { it == SyncStatus.SYNCED })
    }

    @Test
    fun aSecondRequestAppendsInsteadOfRunningInParallel() = runTest {
        TestOutbox.recordMany(db, 5)
        scheduler.requestSync()
        scheduler.requestSync(expedited = true)

        val (first, second) = chain().sortedBy { it.state != WorkInfo.State.ENQUEUED }
        assertEquals(WorkInfo.State.ENQUEUED, first.state)
        assertEquals(WorkInfo.State.BLOCKED, second.state) // waits for the first; never two RUNNING

        driver().setAllConstraintsMet(first.id)
        awaitFinished(first.id)
        driver().setAllConstraintsMet(second.id)
        awaitFinished(second.id)

        val delivered = server.deliveryLog.map { it.opId }
        assertEquals(5, delivered.size)
        assertEquals(delivered.toSet().size, delivered.size)
        assertTrue(chain().all { it.state == WorkInfo.State.SUCCEEDED })
    }

    @Test
    fun aDelayedRequestCarriesTheInitialDelay() = runTest {
        scheduler.requestSync(delay = 7.seconds)
        assertEquals(7_000L, chain().single().initialDelayMillis)
    }
}
