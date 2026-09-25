package com.jcgrdev.picktracechallenge.core.sync.work

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.jcgrdev.picktracechallenge.core.sync.RunResult
import com.jcgrdev.picktracechallenge.core.sync.SyncRunner
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = FakeSyncScheduler()

    private fun workerReturning(result: RunResult): SyncWorker {
        val runner = SyncRunner { result }
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                    SyncWorker(appContext, params, runner, scheduler)
            })
            .build()
    }

    @Test
    fun completedIsSuccess() = runTest {
        assertEquals(ListenableWorker.Result.success(), workerReturning(RunResult.Completed).doWork())
        assertEquals(emptyList<FakeSyncScheduler.Request>(), scheduler.requests)
    }

    @Test
    fun retryIsRetryWithBackoff() = runTest {
        assertEquals(ListenableWorker.Result.retry(), workerReturning(RunResult.Retry).doWork())
    }

    @Test
    fun retryAfterSchedulesAFollowUpWithTheServerDelayAndSucceeds() = runTest {
        assertEquals(ListenableWorker.Result.success(), workerReturning(RunResult.RetryAfter(7.seconds)).doWork())
        assertEquals(listOf(FakeSyncScheduler.Request(expedited = false, delay = 7.seconds)), scheduler.requests)
    }

    @Test
    fun refusedIsSuccessBecauseRetryingCannotHelp() = runTest {
        assertEquals(ListenableWorker.Result.success(), workerReturning(RunResult.Refused).doWork())
    }

    @Test
    fun foregroundInfoUsesTheSyncChannelAndDataSyncType() = runTest {
        val info = workerReturning(RunResult.Completed).getForegroundInfo()
        assertEquals(SYNC_CHANNEL_ID, info.notification.channelId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(SYNC_CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }
}
