package com.jcgrdev.picktracechallenge.core.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Unique work with APPEND_OR_REPLACE: a request during a run appends one more run after it, so runs
 * never overlap (FR-015) and nothing is dropped. All scheduling state lives in WorkManager.
 */
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync(expedited: Boolean, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF.inWholeSeconds, TimeUnit.SECONDS)
            .apply {
                // WorkManager rejects delayed expedited work, so a delay wins over expedited.
                if (delay.isPositive()) {
                    setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                } else if (expedited) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        val BACKOFF = 30.seconds
    }
}
