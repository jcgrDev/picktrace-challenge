package com.jcgrdev.picktracechallenge.core.sync.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jcgrdev.picktracechallenge.core.sync.RunResult
import com.jcgrdev.picktracechallenge.core.sync.SyncRunner
import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

const val SYNC_WORK_NAME = "field-event-sync"
const val SYNC_CHANNEL_ID = "sync"
private const val SYNC_NOTIFICATION_ID = 0x5E4C

/** Runs one sync and maps its result onto WorkManager's retry machinery. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: SyncRunner,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val result = runner.run()) {
        RunResult.Completed -> Result.success()
        RunResult.Retry -> Result.retry()
        is RunResult.RetryAfter -> {
            // Result.retry() can't carry the server's Retry-After, so append a delayed run instead.
            scheduler.requestSync(delay = result.delay)
            Result.success()
        }
        // The refused events are FAILED; retrying the same batch cannot succeed.
        RunResult.Refused -> Result.success()
    }

    /** Expedited work runs as a foreground service below API 31, which needs a notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(SYNC_CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(applicationContext, SYNC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncing field events")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SYNC_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SYNC_NOTIFICATION_ID, notification)
        }
    }
}
