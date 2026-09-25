package com.jcgrdev.picktracechallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jcgrdev.picktracechallenge.core.designsystem.theme.PicktraceTheme
import com.jcgrdev.picktracechallenge.navigation.PicktraceNavHost
import com.jcgrdev.picktracechallenge.core.sync.trigger.SyncTrigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Foreground connectivity trigger (SC-002); background sync relies on WorkManager's constraint. */
    @Inject
    lateinit var syncTrigger: SyncTrigger

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PicktraceTheme {
                PicktraceNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        syncTrigger.start()
    }

    override fun onStop() {
        syncTrigger.stop()
        super.onStop()
    }
}
