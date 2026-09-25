package com.jcgrdev.picktracechallenge.core.sync.trigger

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import kotlin.time.Duration

/** FR-010 foreground path and the SC-002 client-side bound: no debounce, expedited, immediate. */
@RunWith(AndroidJUnit4::class)
class ForegroundConnectivityTriggerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scheduler = FakeSyncScheduler()
    private val trigger = ForegroundConnectivityTrigger(context, scheduler)

    private fun callbacks() = shadowOf(connectivity).networkCallbacks

    private fun networkBecomesAvailable() = callbacks().forEach { it.onAvailable(ShadowNetwork.newInstance(1)) }

    @Test
    fun anAvailableNetworkRequestsOneExpeditedSyncImmediately() {
        trigger.start()
        networkBecomesAvailable()
        assertEquals(listOf(FakeSyncScheduler.Request(expedited = true, delay = Duration.ZERO)), scheduler.requests)
    }

    @Test
    fun nothingIsRequestedAfterStop() {
        trigger.start()
        trigger.stop()
        assertEquals(0, callbacks().size)
        networkBecomesAvailable()
        assertEquals(emptyList<FakeSyncScheduler.Request>(), scheduler.requests)
    }

    @Test
    fun startIsIdempotent() {
        trigger.start()
        trigger.start()
        assertEquals(1, callbacks().size)
        trigger.stop()
        trigger.stop()
        assertEquals(0, callbacks().size)
    }
}
