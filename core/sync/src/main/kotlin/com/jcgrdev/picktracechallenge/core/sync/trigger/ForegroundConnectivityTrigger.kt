package com.jcgrdev.picktracechallenge.core.sync.trigger

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * While the app is in the foreground, a network becoming available requests an expedited sync right
 * away (SC-002). The background path is WorkManager's CONNECTED constraint on every request.
 */
@Singleton
class ForegroundConnectivityTrigger @Inject constructor(
    @ApplicationContext context: Context,
    private val scheduler: SyncScheduler,
) : SyncTrigger {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduler.requestSync(expedited = true)
        }
    }

    @Synchronized
    override fun start() {
        if (registered) return
        connectivity.registerDefaultNetworkCallback(callback)
        registered = true
    }

    @Synchronized
    override fun stop() {
        if (!registered) return
        connectivity.unregisterNetworkCallback(callback)
        registered = false
    }
}
